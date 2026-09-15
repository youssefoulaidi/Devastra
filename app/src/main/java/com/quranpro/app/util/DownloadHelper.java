package com.quranpro.app.util;

import android.content.Context;
import android.net.Uri;
import android.system.ErrnoException;
import android.os.StatFs;
import android.system.Os;

import com.quranpro.app.App;
import com.quranpro.app.R;
import com.quranpro.app.data.Store;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Surah downloader into app-private storage.
 *
 * <p>Everything is stored under {@code filesDir/audio/quran} (internal storage) so the
 * files are never unmounted and never wiped by the system cache cleaner — an earlier
 * version wrote them to the *external* files dir, which is unavailable on some devices
 * while the storage is busy, which made "offline" surahs disappear.
 *
 * <p>The downloader also: resumes interrupted downloads with HTTP Range requests,
 * retries transient failures, verifies the payload really is audio (and not a captive
 * portal / error page), and reports progress to the UI.
 */
public final class DownloadHelper {
    private DownloadHelper() {}

    private static final ExecutorService EX = Executors.newFixedThreadPool(2);
    private static final Set<String> RUNNING = ConcurrentHashMap.newKeySet();
    private static final Map<String, Job> JOBS = new ConcurrentHashMap<>();
    private static final Map<String, long[]> PROGRESS = new ConcurrentHashMap<>();
    private static final CopyOnWriteArrayList<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private static final int CONNECT_TIMEOUT_MS = 20_000;
    private static final int READ_TIMEOUT_MS = 45_000;
    private static final int MAX_REDIRECTS = 4;
    private static final int ATTEMPTS = 3;
    /** Some full-surah recitations (Al-Baqarah…) are ~150 MB — do not cap at 60 MB. */
    private static final long MAX_BYTES = 512L * 1024L * 1024L;
    private static final long MIN_OK_BYTES = 20L * 1024L;
    private static final long FREE_SPACE_FLOOR = 8L * 1024L * 1024L;
    private static final String UA = "Mozilla/5.0 (Linux; Android 13) QuranPro/1.5";

    private static volatile boolean migrated;

    private static final class Job {
        volatile boolean cancelled;
        volatile HttpURLConnection conn;
        volatile InputStream in;
    }

    private static final class OpenResult {
        final HttpURLConnection conn;
        final long contentLength;
        final boolean resumed;

        OpenResult(HttpURLConnection conn, long contentLength, boolean resumed) {
            this.conn = conn;
            this.contentLength = contentLength;
            this.resumed = resumed;
        }
    }

    // ---------- listeners / progress ----------

    public static void addListener(Runnable r) {
        if (r == null) return;
        if (!LISTENERS.contains(r)) LISTENERS.add(r);
    }

    public static void removeListener(Runnable r) {
        if (r == null) return;
        LISTENERS.remove(r);
    }

    /** 0…100 while downloading, -1 when unknown / not running. */
    public static int progress(String key) {
        long[] p = PROGRESS.get(key);
        if (p == null) return -1;
        long done = p[0], total = p[1];
        if (total <= 0) return -1;
        int v = (int) Math.min(100, Math.max(0, done * 100 / total));
        return v >= 100 && !RUNNING.contains(key) ? -1 : v;
    }

    public static long bytesDone(String key) {
        long[] p = PROGRESS.get(key);
        return p == null ? 0 : p[0];
    }

    public static boolean isRunning(String key) {
        return key != null && RUNNING.contains(key);
    }

    private static void notifyListeners() {
        App.post(() -> {
            for (Runnable r : LISTENERS) {
                try {
                    r.run();
                } catch (Exception ignored) {}
            }
        });
    }

    private static void toast(Context c, int resId) {
        App.post(() -> Ui.toast(c, resId));
    }

    // ---------- storage ----------

    /** Internal app storage: always mounted, never cleared by the system. */
    public static File dir(Context c) {
        Context app = c.getApplicationContext();
        File d = new File(new File(app.getFilesDir(), "audio"), "quran");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Old location used up to v1.4 (external app dir) — migrated lazily. */
    private static File legacyDir(Context c) {
        File base = c.getApplicationContext().getExternalFilesDir("audio");
        return base == null ? null : new File(base, "quran");
    }

    public static String fileName(int reciterId, int surahId) {
        return String.format(Locale.US, "r%d_s%03d.mp3", reciterId, surahId);
    }

    public static File fileFor(Context c, int reciterId, int surahId) {
        return new File(dir(c), fileName(reciterId, surahId));
    }

    private static File partFor(File target) {
        return new File(target.getAbsolutePath() + ".part");
    }

    public static String key(String server, int surahId) {
        return Store.favKey(server, surahId);
    }

    /** Moves files downloaded by older versions from external → internal storage. */
    public static synchronized void migrate(Context ctx) {
        if (migrated) return;
        migrated = true;
        Context app = ctx.getApplicationContext();
        File from = legacyDir(app);
        if (from == null || !from.exists()) return;
        File to = dir(app);
        File[] files = from.listFiles();
        if (files == null) return;
        for (File f : files) {
            try {
                if (!f.isFile() || f.length() <= MIN_OK_BYTES) continue;
                String name = f.getName();
                if (name.endsWith(".part")) continue;
                File dest = new File(to, name);
                if (dest.exists() && dest.length() > MIN_OK_BYTES) continue;
                try {
                    Os.rename(f.getAbsolutePath(), dest.getAbsolutePath());
                } catch (Exception e) {
                    copyFile(f, dest);
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            } catch (Exception ignored) {}
        }
        // Point existing bookkeeping at the new paths.
        for (Store.Dl d : new ArrayList<>(Store.getDls(app))) {
            if (d.path == null) continue;
            File old = new File(d.path);
            if (!old.exists()) continue;
            File dest = new File(to, old.getName());
            if (dest.exists() && dest.length() > MIN_OK_BYTES) {
                d.path = dest.getAbsolutePath();
                d.done = true;
                Store.upsertDl(app, d);
            }
        }
    }

    private static void copyFile(File from, File to) throws IOException {
        try (InputStream in = new java.io.FileInputStream(from);
             FileOutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            out.flush();
        }
    }

    // ---------- enqueue ----------

    /** Enqueue download; returns false only when the file is already complete on disk. */
    public static boolean enqueue(Context ctx, int reciterId, String reciterName,
                                  int surahId, String surahName, String server, String url) {
        Context app = ctx.getApplicationContext();
        migrate(app);
        String k = key(server, surahId);
        File target = fileFor(app, reciterId, surahId);
        File part = partFor(target);
        if (isCompleteFile(target)) {
            try { if (part.exists()) part.delete(); } catch (Exception ignored) {}
            Store.upsertDl(app, buildEntry(k, surahId, surahName, reciterName, server,
                    target.getAbsolutePath(), true));
            notifyListeners();
            return false;
        }
        if (RUNNING.contains(k)) return true;

        if (target.exists() && !looksLikeAudio(target)) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
        }

        Store.upsertDl(app, buildEntry(k, surahId, surahName, reciterName, server,
                target.getAbsolutePath(), false));
        notifyListeners();

        Job job = new Job();
        RUNNING.add(k);
        JOBS.put(k, job);
        PROGRESS.put(k, new long[]{0, 0});
        EX.execute(() -> runDownload(app, k, surahId, surahName, reciterName,
                server, url, target, part, job));
        return true;
    }

    private static Store.Dl buildEntry(String key, int surahId, String surahName,
                                       String reciterName, String server,
                                       String path, boolean done) {
        Store.Dl d = new Store.Dl();
        d.key = key;
        d.surahId = surahId;
        d.surahName = surahName;
        d.reciterName = reciterName;
        d.server = server;
        d.path = path;
        d.done = done;
        d.dlId = 0;
        return d;
    }

    // ---------- worker ----------

    private static void runDownload(Context app, String key, int surahId, String surahName,
                                    String reciterName, String server, String url,
                                    File target, File part, Job job) {
        boolean ok = false;
        Exception last = null;
        for (int attempt = 1; attempt <= ATTEMPTS && !job.cancelled && !ok; attempt++) {
            try {
                target.getParentFile().mkdirs();
                Space space = freeSpace(target);
                if (space.usable > 0 && space.usable < FREE_SPACE_FLOOR) {
                    throw new IOException("no space");
                }
                downloadOnce(key, url, part, target, job);
                if (!looksLikeAudio(part)) throw new IOException("not audio");
                renameAtomic(part, target);
                Store.upsertDl(app, buildEntry(key, surahId, surahName, reciterName, server,
                        target.getAbsolutePath(), true));
                ok = true;
                toast(app, R.string.dl_done);
            } catch (Exception e) {
                last = e;
                if (job.cancelled) break;
                if (attempt < ATTEMPTS) {
                    try {
                        Thread.sleep(attempt == 1 ? 1200L : 3500L);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        if (!ok) {
            try { if (part.exists()) part.delete(); } catch (Exception ignored) {}
            try { if (target.exists() && !looksLikeAudio(target)) target.delete(); }
            catch (Exception ignored) {}
            if (!job.cancelled) {
                Store.removeDl(app, key);
                toast(app, last != null && "no space".equals(last.getMessage())
                        ? R.string.dl_no_space : R.string.dl_fail);
            }
        }
        closeQuietly(job.in);
        disconnectQuietly(job.conn);
        JOBS.remove(key);
        RUNNING.remove(key);
        PROGRESS.remove(key);
        notifyListeners();
    }

    private static final class Space {
        final long usable;
        final long total;

        Space(long usable, long total) {
            this.usable = usable;
            this.total = total;
        }
    }

    private static Space freeSpace(File f) {
        try {
            StatFs sf = new StatFs(f.getAbsolutePath());
            return new Space(sf.getAvailableBytes(), sf.getTotalBytes());
        } catch (Exception e) {
            return new Space(-1, -1);
        }
    }

    /** One download pass — resumes a partial {@code .part} file when possible. */
    private static void downloadOnce(String key, String url, File part, File target,
                                     Job job) throws IOException {
        long already = (part.exists() && part.length() > 0) ? part.length() : 0;
        if (already > MAX_BYTES) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
            already = 0;
        }
        OpenResult open = open(url, already, job);
        if (open.contentLength > MAX_BYTES) throw new IOException("too large");

        long total = open.contentLength;
        long base = open.resumed ? already : 0;
        if (!open.resumed) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
            base = 0;
        }
        PROGRESS.put(key, new long[]{base, total});

        long written = copyTo(key, open.conn, part, base, total, job);
        if (written <= MIN_OK_BYTES) throw new IOException("too short");
        if (total > 0 && written < Math.round(total * 0.98d)) throw new IOException("truncated");
    }

    private static OpenResult open(String url, long resumeFrom, Job job) throws IOException {
        String current = url;
        for (int i = 0; i <= MAX_REDIRECTS; i++) {
            if (job.cancelled) throw new IOException("cancelled");
            HttpURLConnection cn = (HttpURLConnection) new URL(current).openConnection();
            cn.setInstanceFollowRedirects(false);
            cn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            cn.setReadTimeout(READ_TIMEOUT_MS);
            cn.setRequestProperty("User-Agent", UA);
            cn.setRequestProperty("Accept", "*/*");
            cn.setRequestProperty("Accept-Encoding", "identity");
            if (resumeFrom > 0) cn.setRequestProperty("Range", "bytes=" + resumeFrom + "-");
            cn.connect();
            int code = cn.getResponseCode();
            if (isRedirect(code)) {
                String next = cn.getHeaderField("Location");
                if (next == null || next.trim().isEmpty()) {
                    cn.disconnect();
                    throw new IOException("redirect without location");
                }
                if (i >= MAX_REDIRECTS) {
                    cn.disconnect();
                    throw new IOException("redirect loop");
                }
                current = new URL(new URL(current), next).toString();
                cn.disconnect();
                continue;
            }
            if (code == 416) { // requested range not satisfiable → start over
                cn.disconnect();
                throw new IOException("range not satisfiable");
            }
            if (code < 200 || code >= 300) {
                cn.disconnect();
                throw new IOException("HTTP " + code);
            }
            String type = cn.getContentType();
            if (type != null && type.toLowerCase(Locale.US).contains("text/html")) {
                cn.disconnect();
                throw new IOException("html payload"); // captive portal / error page
            }
            boolean resumed = code == 206 && resumeFrom > 0;
            long len = cn.getContentLengthLong();
            long total = resumed ? resumeFrom + len : len;
            job.conn = cn;
            return new OpenResult(cn, total, resumed);
        }
        throw new IOException("redirect loop");
    }

    private static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    private static long copyTo(String key, HttpURLConnection cn, File outFile, long base,
                               long total, Job job) throws IOException {
        long total2 = base;
        byte[] buf = new byte[32 * 1024];
        try (InputStream in = cn.getInputStream();
             FileOutputStream out = new FileOutputStream(outFile, base > 0)) {
            job.in = in;
            int n;
            while ((n = in.read(buf)) != -1) {
                if (job.cancelled) throw new IOException("cancelled");
                total2 += n;
                if (total2 > MAX_BYTES) throw new IOException("too large");
                out.write(buf, 0, n);
                long[] p = PROGRESS.get(key);
                if (p == null) PROGRESS.put(key, new long[]{total2, total});
                else p[0] = total2;
            }
            out.flush();
        }
        return total2;
    }

    private static void renameAtomic(File from, File to) throws IOException {
        try {
            Os.rename(from.getAbsolutePath(), to.getAbsolutePath());
        } catch (ErrnoException e) {
            throw new IOException(e);
        }
    }

    // ---------- validation ----------

    /** Cheap sniff test: ID3 tag or an MPEG audio frame sync (see {@link AudioCheck}). */
    public static boolean looksLikeAudio(File f) {
        return AudioCheck.looksLikeAudio(f);
    }

    private static boolean isCompleteFile(File f) {
        return f != null && f.exists() && f.length() > MIN_OK_BYTES && looksLikeAudio(f);
    }

    // ---------- queries ----------

    public static void refreshStatuses(Context ctx) {
        Context app = ctx.getApplicationContext();
        migrate(app);
        List<Store.Dl> items = new ArrayList<>(Store.getDls(app));
        boolean changed = false;
        for (Store.Dl d : items) {
            File target = d.path == null ? null : new File(d.path);
            File part = target == null ? null : partFor(target);
            if (isCompleteFile(target)) {
                if (!d.done) {
                    d.done = true;
                    Store.upsertDl(app, d);
                    changed = true;
                }
                if (part != null && part.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    part.delete();
                }
                continue;
            }
            if (!RUNNING.contains(d.key)) {
                try { if (part != null && part.exists()) part.delete(); } catch (Exception ignored) {}
                try { if (target != null && target.exists()) target.delete(); } catch (Exception ignored) {}
                Store.removeDl(app, d.key);
                changed = true;
            }
        }
        if (changed) notifyListeners();
    }

    public static boolean isDownloaded(Context c, String server, int surahId) {
        return offlinePath(c, server, surahId) != null;
    }

    /** Absolute path of the offline copy, or null when it is not downloadable-free. */
    public static String offlinePath(Context c, String server, int surahId) {
        Store.Dl d = Store.findDl(c.getApplicationContext(), key(server, surahId));
        if (d != null && d.path != null && isCompleteFile(new File(d.path))) return d.path;
        return null;
    }

    /**
     * Playback-time lookup: store entry first, then a directory scan with the canonical
     * file name (self-heals bookkeeping that was lost, e.g. after a storage move).
     */
    public static String localPath(Context c, int reciterId, String server, int surahId) {
        Context app = c.getApplicationContext();
        migrate(app);
        String k = key(server, surahId);
        Store.Dl d = Store.findDl(app, k);
        if (d != null && d.path != null && isCompleteFile(new File(d.path))) return d.path;

        File guess = fileFor(app, reciterId, surahId);
        if (isCompleteFile(guess)) {
            Store.upsertDl(app, buildEntry(k, surahId,
                    d == null ? null : d.surahName,
                    d == null ? null : d.reciterName, server,
                    guess.getAbsolutePath(), true));
            return guess.getAbsolutePath();
        }
        // last resort: any file for this surah in the download folder
        File[] files = dir(app).listFiles();
        if (files != null) {
            String suffix = String.format(Locale.US, "_s%03d.mp3", surahId);
            for (File f : files) {
                if (f.getName().endsWith(suffix) && isCompleteFile(f)) {
                    Store.upsertDl(app, buildEntry(k, surahId,
                            d == null ? null : d.surahName,
                            d == null ? null : d.reciterName, server,
                            f.getAbsolutePath(), true));
                    return f.getAbsolutePath();
                }
            }
        }
        return null;
    }

    public static void delete(Context c, String key) {
        Context app = c.getApplicationContext();
        Store.Dl d = Store.findDl(app, key);
        Job job = JOBS.get(key);
        if (job != null) {
            job.cancelled = true;
            closeQuietly(job.in);
            disconnectQuietly(job.conn);
        }
        if (d != null && d.path != null) {
            File target = new File(d.path);
            File part = partFor(target);
            try { if (target.exists()) target.delete(); } catch (Exception ignored) {}
            try { if (part.exists()) part.delete(); } catch (Exception ignored) {}
        }
        Store.removeDl(app, key);
        notifyListeners();
    }

    /** Sum of the audio files currently on disk (bytes). */
    public static long totalBytes(Context c) {
        long total = 0;
        File[] files = dir(c).listFiles();
        if (files == null) return 0;
        for (File f : files) if (f.isFile()) total += f.length();
        return total;
    }

    public static String fileUri(String path) {
        return Uri.fromFile(new File(path)).toString();
    }

    private static void closeQuietly(InputStream in) {
        if (in == null) return;
        try {
            in.close();
        } catch (Exception ignored) {}
    }

    private static void disconnectQuietly(HttpURLConnection cn) {
        if (cn == null) return;
        try {
            cn.disconnect();
        } catch (Exception ignored) {}
    }
}
