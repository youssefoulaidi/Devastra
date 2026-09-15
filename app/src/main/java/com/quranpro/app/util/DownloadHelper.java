package com.quranpro.app.util;

import android.content.Context;
import android.net.Uri;
import android.system.ErrnoException;
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

/** Internal surah downloader into app-private storage. */
public final class DownloadHelper {
    private DownloadHelper() {}

    private static final ExecutorService EX = Executors.newFixedThreadPool(2);
    private static final Set<String> RUNNING = ConcurrentHashMap.newKeySet();
    private static final Map<String, Job> JOBS = new ConcurrentHashMap<>();
    private static final CopyOnWriteArrayList<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private static final int CONNECT_TIMEOUT_MS = 20_000;
    private static final int READ_TIMEOUT_MS = 40_000;
    private static final int MAX_REDIRECTS = 4;
    private static final long MAX_BYTES = 60L * 1024L * 1024L;
    private static final long MIN_OK_BYTES = 1000L;

    private static final class Job {
        volatile boolean cancelled;
        volatile HttpURLConnection conn;
        volatile InputStream in;
    }

    private static final class OpenResult {
        final HttpURLConnection conn;
        final long contentLength;

        OpenResult(HttpURLConnection conn, long contentLength) {
            this.conn = conn;
            this.contentLength = contentLength;
        }
    }

    public static void addListener(Runnable r) {
        if (r == null) return;
        if (!LISTENERS.contains(r)) LISTENERS.add(r);
    }

    public static void removeListener(Runnable r) {
        if (r == null) return;
        LISTENERS.remove(r);
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

    public static File dir(Context c) {
        Context app = c.getApplicationContext();
        File base = app.getExternalFilesDir("audio");
        if (base == null) base = new File(app.getFilesDir(), "audio");
        File d = new File(base, "quran");
        d.mkdirs();
        return d;
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

    /** Enqueue download; returns false only when the file is already complete on disk. */
    public static boolean enqueue(Context ctx, int reciterId, String reciterName,
                                  int surahId, String surahName, String server, String url) {
        Context app = ctx.getApplicationContext();
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

        if (target.exists() && target.length() <= MIN_OK_BYTES) {
            try { target.delete(); } catch (Exception ignored) {}
        }

        Store.upsertDl(app, buildEntry(k, surahId, surahName, reciterName, server,
                target.getAbsolutePath(), false));
        notifyListeners();

        Job job = new Job();
        RUNNING.add(k);
        JOBS.put(k, job);
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

    private static void runDownload(Context app, String key, int surahId, String surahName,
                                    String reciterName, String server, String url,
                                    File target, File part, Job job) {
        boolean ok = false;
        try {
            target.getParentFile().mkdirs();
            if (part.exists()) part.delete();
            OpenResult open = open(url, job);
            if (open.contentLength > MAX_BYTES) {
                throw new IOException("too large");
            }
            long written = copyTo(open.conn, part, job);
            if (written <= MIN_OK_BYTES) {
                throw new IOException("too short");
            }
            if (open.contentLength > 0 && written < Math.round(open.contentLength * 0.95d)) {
                throw new IOException("truncated");
            }
            renameAtomic(part, target);
            Store.upsertDl(app, buildEntry(key, surahId, surahName, reciterName, server,
                    target.getAbsolutePath(), true));
            ok = true;
            toast(app, R.string.dl_done);
        } catch (Exception ignored) {
            try { if (part.exists()) part.delete(); } catch (Exception ignored2) {}
            try { if (!job.cancelled && target.exists() && target.length() <= MIN_OK_BYTES) target.delete(); }
            catch (Exception ignored2) {}
            Store.removeDl(app, key);
            if (!job.cancelled) toast(app, R.string.dl_fail);
        } finally {
            closeQuietly(job.in);
            disconnectQuietly(job.conn);
            JOBS.remove(key);
            RUNNING.remove(key);
            notifyListeners();
        }
    }

    private static OpenResult open(String url, Job job) throws IOException {
        String current = url;
        for (int i = 0; i <= MAX_REDIRECTS; i++) {
            if (job.cancelled) throw new IOException("cancelled");
            HttpURLConnection cn = (HttpURLConnection) new URL(current).openConnection();
            cn.setInstanceFollowRedirects(false);
            cn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            cn.setReadTimeout(READ_TIMEOUT_MS);
            cn.setRequestProperty("User-Agent", "QuranPro/1.3 (Android)");
            cn.setRequestProperty("Accept", "*/*");
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
            if (code < 200 || code >= 300) {
                cn.disconnect();
                throw new IOException("HTTP " + code);
            }
            job.conn = cn;
            return new OpenResult(cn, cn.getContentLengthLong());
        }
        throw new IOException("redirect loop");
    }

    private static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    private static long copyTo(HttpURLConnection cn, File outFile, Job job) throws IOException {
        long total = 0;
        byte[] buf = new byte[16 * 1024];
        try (InputStream in = cn.getInputStream(); FileOutputStream out = new FileOutputStream(outFile)) {
            job.in = in;
            int n;
            while ((n = in.read(buf)) != -1) {
                if (job.cancelled) throw new IOException("cancelled");
                total += n;
                if (total > MAX_BYTES) throw new IOException("too large");
                out.write(buf, 0, n);
            }
            out.flush();
        }
        return total;
    }

    private static void renameAtomic(File from, File to) throws IOException {
        try {
            Os.rename(from.getAbsolutePath(), to.getAbsolutePath());
        } catch (ErrnoException e) {
            throw new IOException(e);
        }
    }

    private static boolean isCompleteFile(File f) {
        return f != null && f.exists() && f.length() > MIN_OK_BYTES;
    }

    public static void refreshStatuses(Context ctx) {
        Context app = ctx.getApplicationContext();
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
                    try { part.delete(); } catch (Exception ignored) {}
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
        Store.Dl d = Store.findDl(c.getApplicationContext(), key(server, surahId));
        return d != null && d.done && d.path != null && isCompleteFile(new File(d.path));
    }

    public static String offlinePath(Context c, String server, int surahId) {
        Store.Dl d = Store.findDl(c.getApplicationContext(), key(server, surahId));
        if (d != null && d.done && d.path != null && isCompleteFile(new File(d.path))) return d.path;
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
