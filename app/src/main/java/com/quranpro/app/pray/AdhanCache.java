package com.quranpro.app.pray;

import android.content.Context;
import android.net.Uri;
import android.system.ErrnoException;
import android.system.Os;

import com.quranpro.app.App;
import com.quranpro.app.util.AudioCheck;
import com.quranpro.app.util.Net;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Offline storage for the muezzin voices.
 *
 * <p>Voices that ship inside the APK are used directly from {@code assets/adhan}. Any
 * other voice can be downloaded once and is then kept in <b>internal</b> storage
 * ({@code filesDir/adhan}) — the system never clears that folder, unlike the cache dir
 * used before, which is why the offline adhan could silently disappear.
 */
public final class AdhanCache {
    private AdhanCache() {}

    private static final ExecutorService EX = Executors.newSingleThreadExecutor();
    private static final Set<Integer> RUNNING = ConcurrentHashMap.newKeySet();
    private static final Map<Integer, long[]> PROGRESS = new ConcurrentHashMap<>();
    private static final CopyOnWriteArrayList<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private static final long MIN_CACHE_BYTES = 40L * 1024L;
    private static final long MAX_BYTES = 40L * 1024L * 1024L;
    private static final int ATTEMPTS = 2;
    /** Browser-like UA; the app version comes from the installed package, not a literal. */
    private static String ua() {
        return "Mozilla/5.0 (Linux; Android 13) " + App.userAgent();
    }

    private static volatile boolean migrated;

    // ---------- storage ----------

    /** Permanent per-app storage (never cleared by the OS cache cleaner). */
    public static File dir(Context c) {
        File d = new File(c.getApplicationContext().getFilesDir(), "adhan");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Old location (cacheDir) used up to v1.4 — moved on first use. */
    private static File legacyDir(Context c) {
        return new File(c.getApplicationContext().getCacheDir(), "adhan");
    }

    public static File fileFor(Context c, int idx) {
        return new File(dir(c), String.format(Locale.US, "voice_%02d.mp3", idx));
    }

    private static File partFor(Context c, int idx) {
        return new File(dir(c), String.format(Locale.US, "voice_%02d.mp3.part", idx));
    }

    /** Moves voices cached by older versions out of the cache directory. */
    public static synchronized void migrate(Context ctx) {
        if (migrated) return;
        migrated = true;
        Context app = ctx.getApplicationContext();
        File from = legacyDir(app);
        if (from == null || !from.exists()) return;
        File[] files = from.listFiles();
        if (files == null) return;
        File to = dir(app);
        for (File f : files) {
            try {
                if (!f.isFile() || f.length() <= MIN_CACHE_BYTES) continue;
                File dest = new File(to, f.getName());
                if (dest.exists() && dest.length() > MIN_CACHE_BYTES) continue;
                try {
                    Os.rename(f.getAbsolutePath(), dest.getAbsolutePath());
                } catch (Exception e) {
                    copy(f, dest);
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            } catch (Exception ignored) {}
        }
    }

    private static void copy(File from, File to) throws IOException {
        try (InputStream in = new java.io.FileInputStream(from);
             FileOutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            out.flush();
        }
    }

    // ---------- state ----------

    /** Cheap check for lists/badges (no header read). */
    public static boolean isCached(Context c, int idx) {
        File f = fileFor(c, idx);
        return f.exists() && f.length() > MIN_CACHE_BYTES;
    }

    /** Full check used right before playback. */
    private static boolean isPlayable(File f) {
        return f.exists() && f.length() > MIN_CACHE_BYTES && AudioCheck.looksLikeAudio(f);
    }

    /** True when the voice plays with no internet at all (bundled asset or download). */
    public static boolean isOfflineReady(Context c, int idx) {
        if (idx < 0) return true; // "notification only" needs no audio
        migrate(c);
        return Muezzins.isBundled(c, idx) || isCached(c, idx);
    }

    public static boolean isDownloading(int idx) {
        return RUNNING.contains(idx);
    }

    /** 0…100, or -1 when idle. */
    public static int progress(int idx) {
        long[] p = PROGRESS.get(idx);
        if (p == null || p[1] <= 0) return -1;
        return (int) Math.min(100, Math.max(0, p[0] * 100 / p[1]));
    }

    /** Offline-only source: bundled asset or downloaded file (null when unavailable). */
    public static String offlineSource(Context c, int idx) {
        if (idx < 0) return null;
        migrate(c);
        File cached = fileFor(c, idx);
        if (isPlayable(cached)) return Uri.fromFile(cached).toString();
        return Muezzins.assetFor(c, idx);
    }

    /** Network source for the voice (used only when nothing is stored locally). */
    public static String streamSource(int idx) {
        return idx < 0 ? null : Muezzins.urlFor(idx);
    }

    /** Backwards-compatible alias: local source when possible, network otherwise. */
    public static String playSource(Context c, int idx) {
        String offline = offlineSource(c, idx);
        return offline != null ? offline : streamSource(idx);
    }

    public static int offlineCount(Context c) {
        int n = 0;
        for (int i = 0; i < Muezzins.count(); i++) {
            if (isOfflineReady(c, i)) n++;
        }
        return n;
    }

    // ---------- listeners ----------

    public static void addListener(Runnable r) {
        if (r != null && !LISTENERS.contains(r)) LISTENERS.add(r);
    }

    public static void removeListener(Runnable r) {
        if (r != null) LISTENERS.remove(r);
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

    // ---------- download ----------

    /** Downloads a voice for offline use unless it is already available. */
    public static void ensure(Context c, int idx) {
        Context app = c.getApplicationContext();
        if (idx < 0) return;
        migrate(app);
        if (Muezzins.isBundled(app, idx) || isCached(app, idx) || RUNNING.contains(idx)) return;
        RUNNING.add(idx);
        PROGRESS.put(idx, new long[]{0, 0});
        notifyListeners();
        EX.execute(() -> {
            try {
                if (!download(app, idx, Muezzins.urlFor(idx))) {
                    String fb = Muezzins.fallbackFor(idx);
                    if (fb != null) download(app, idx, fb);
                }
            } finally {
                RUNNING.remove(idx);
                PROGRESS.remove(idx);
                notifyListeners();
            }
        });
    }

    /** Downloads every voice that is not available offline yet (sequential). */
    public static void ensureAll(Context c) {
        for (int i = 0; i < Muezzins.count(); i++) ensure(c, i);
    }

    public static void delete(Context c, int idx) {
        File f = fileFor(c, idx);
        try { if (f.exists()) f.delete(); } catch (Exception ignored) {}
        File p = partFor(c, idx);
        try { if (p.exists()) p.delete(); } catch (Exception ignored) {}
        notifyListeners();
    }

    private static boolean download(Context c, int idx, String url) {
        if (url == null || url.trim().isEmpty()) return false;
        if (!Net.online(c)) return false;
        File out = fileFor(c, idx);
        File part = partFor(c, idx);
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            HttpURLConnection cn = null;
            try {
                if (part.exists()) part.delete();
                cn = open(url);
                long declared = cn.getContentLengthLong();
                if (declared > MAX_BYTES) return false;
                long total = 0;
                byte[] buf = new byte[32 * 1024];
                try (InputStream in = cn.getInputStream();
                     FileOutputStream fos = new FileOutputStream(part)) {
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        total += n;
                        if (total > MAX_BYTES) throw new IOException("too large");
                        fos.write(buf, 0, n);
                        long[] p = PROGRESS.get(idx);
                        if (p == null) PROGRESS.put(idx, new long[]{total, declared});
                        else p[0] = total;
                    }
                    fos.flush();
                }
                if (total <= MIN_CACHE_BYTES) throw new IOException("too short");
                if (declared > 0 && total < declared * 0.98d) throw new IOException("truncated");
                if (!AudioCheck.looksLikeAudio(part)) throw new IOException("not audio");
                renameAtomic(part, out);
                return isPlayable(out);
            } catch (Exception e) {
                try { if (part.exists()) part.delete(); } catch (Exception ignored) {}
            } finally {
                if (cn != null) cn.disconnect();
            }
        }
        return false;
    }

    private static HttpURLConnection open(String url) throws IOException {
        String current = url;
        for (int i = 0; i <= 4; i++) {
            HttpURLConnection cn = (HttpURLConnection) new URL(current).openConnection();
            cn.setInstanceFollowRedirects(false);
            cn.setConnectTimeout(20_000);
            cn.setReadTimeout(45_000);
            cn.setRequestProperty("User-Agent", ua());
            cn.setRequestProperty("Accept", "*/*");
            int code = cn.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String next = cn.getHeaderField("Location");
                cn.disconnect();
                if (next == null || next.trim().isEmpty() || i >= 4) {
                    throw new IOException("redirect");
                }
                current = new URL(new URL(current), next).toString();
                continue;
            }
            if (code < 200 || code >= 300) {
                cn.disconnect();
                throw new IOException("HTTP " + code);
            }
            String type = cn.getContentType();
            if (type != null && type.toLowerCase(Locale.US).contains("text/html")) {
                cn.disconnect();
                throw new IOException("html");
            }
            return cn;
        }
        throw new IOException("redirect");
    }

    private static void renameAtomic(File from, File to) throws IOException {
        try {
            Os.rename(from.getAbsolutePath(), to.getAbsolutePath());
        } catch (ErrnoException e) {
            throw new IOException(e);
        }
    }
}
