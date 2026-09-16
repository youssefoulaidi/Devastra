package com.quranpro.app.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import com.quranpro.app.App;
import com.quranpro.app.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tiny async image loader (memory + disk cache) for video thumbnails. */
public final class ImageLoader {
    private ImageLoader() {}

    private static final ExecutorService EX = Executors.newFixedThreadPool(3);
    private static LruCache<String, Bitmap> mem;

    private static synchronized LruCache<String, Bitmap> mem() {
        if (mem == null) {
            int max = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8);
            mem = new LruCache<String, Bitmap>(Math.max(4096, max)) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount() / 1024;
                }
            };
        }
        return mem;
    }

    public static void load(final ImageView v, final String url) {
        final Context app = v.getContext().getApplicationContext();
        v.setTag(url);
        v.setImageResource(R.drawable.bg_play_badge);
        if (url == null || url.isEmpty()) return;
        Bitmap b = mem().get(url);
        if (b != null) {
            v.setImageBitmap(b);
            return;
        }
        EX.execute(() -> {
            try {
                File dir = new File(app.getCacheDir(), "img");
                dir.mkdirs();
                String name = String.format(Locale.US, "%x", url.hashCode()) + ".img";
                File f = new File(dir, name);
                Bitmap bmp = null;
                if (f.exists() && f.length() > 100) {
                    bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
                }
                if (bmp == null) {
                    HttpURLConnection cn = (HttpURLConnection) new URL(url).openConnection();
                    cn.setConnectTimeout(15000);
                    cn.setReadTimeout(20000);
                    cn.setRequestProperty("User-Agent", App.userAgent());
                    cn.connect();
                    if (cn.getResponseCode() >= 200 && cn.getResponseCode() < 300) {
                        InputStream in = cn.getInputStream();
                        try {
                            byte[] data = readAll(in);
                            if (data.length > 100) {
                                FileOutputStream out = new FileOutputStream(f);
                                try {
                                    out.write(data);
                                } finally {
                                    try { out.close(); } catch (Exception ignored) {}
                                }
                                bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                            }
                        } finally {
                            try { in.close(); } catch (Exception ignored) {}
                        }
                    }
                    cn.disconnect();
                }
                if (bmp != null) {
                    mem().put(url, bmp);
                    final Bitmap fb = bmp;
                    App.post(() -> {
                        if (url.equals(v.getTag())) v.setImageBitmap(fb);
                    });
                }
            } catch (Exception ignored) {}
        });
    }

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }
}
