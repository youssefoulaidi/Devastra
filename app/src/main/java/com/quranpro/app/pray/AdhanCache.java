package com.quranpro.app.pray;

import android.content.Context;
import android.net.Uri;
import android.system.ErrnoException;
import android.system.Os;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Background cache for adhan voices. */
public final class AdhanCache {
    private AdhanCache() {}

    private static final ExecutorService EX = Executors.newSingleThreadExecutor();
    private static final Set<Integer> RUNNING = ConcurrentHashMap.newKeySet();
    private static final long MIN_CACHE_BYTES = 40L * 1024L;
    private static final long MAX_BYTES = 60L * 1024L * 1024L;

    public static File dir(Context c) {
        File d = new File(c.getApplicationContext().getCacheDir(), "adhan");
        d.mkdirs();
        return d;
    }

    public static File fileFor(Context c, int idx) {
        return new File(dir(c), String.format(Locale.US, "voice_%02d.mp3", idx));
    }

    private static File partFor(Context c, int idx) {
        return new File(dir(c), String.format(Locale.US, "voice_%02d.mp3.part", idx));
    }

    public static boolean isCached(Context c, int idx) {
        File f = fileFor(c, idx);
        return f.exists() && f.length() > MIN_CACHE_BYTES;
    }

    public static String playSource(Context c, int idx) {
        if (idx < 0) return null;
        File cached = fileFor(c, idx);
        if (cached.exists() && cached.length() > MIN_CACHE_BYTES) {
            return Uri.fromFile(cached).toString();
        }
        String asset = Muezzins.assetFor(c, idx);
        if (asset != null) return asset;
        return Muezzins.urlFor(idx);
    }

    public static void start(Context c, int idx) {
        Context app = c.getApplicationContext();
        if (idx < 0 || Muezzins.isBundled(app, idx) || isCached(app, idx) || RUNNING.contains(idx)) {
            return;
        }
        RUNNING.add(idx);
        EX.execute(() -> {
            try {
                if (!download(app, idx, Muezzins.urlFor(idx))) {
                    String fb = Muezzins.fallbackFor(idx);
                    if (fb != null) download(app, idx, fb);
                }
            } finally {
                RUNNING.remove(idx);
            }
        });
    }

    private static boolean download(Context c, int idx, String url) {
        if (url == null || url.trim().isEmpty()) return false;
        File out = fileFor(c, idx);
        File part = partFor(c, idx);
        HttpURLConnection cn = null;
        try {
            if (part.exists()) part.delete();
            cn = open(url);
            long declared = cn.getContentLengthLong();
            if (declared > MAX_BYTES) return false;
            long total = 0;
            byte[] buf = new byte[16 * 1024];
            try (InputStream in = cn.getInputStream(); FileOutputStream fos = new FileOutputStream(part)) {
                int n;
                while ((n = in.read(buf)) != -1) {
                    total += n;
                    if (total > MAX_BYTES) throw new IOException("too large");
                    fos.write(buf, 0, n);
                }
                fos.flush();
            }
            if (total <= MIN_CACHE_BYTES) return false;
            renameAtomic(part, out);
            return out.exists() && out.length() > MIN_CACHE_BYTES;
        } catch (Exception e) {
            try { if (part.exists()) part.delete(); } catch (Exception ignored) {}
            return false;
        } finally {
            if (cn != null) cn.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        String current = url;
        for (int i = 0; i <= 4; i++) {
            HttpURLConnection cn = (HttpURLConnection) new URL(current).openConnection();
            cn.setInstanceFollowRedirects(false);
            cn.setConnectTimeout(20_000);
            cn.setReadTimeout(40_000);
            cn.setRequestProperty("User-Agent", "QuranPro/1.3 (Android)");
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
