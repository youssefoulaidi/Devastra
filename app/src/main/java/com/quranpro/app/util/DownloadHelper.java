package com.quranpro.app.util;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.quranpro.app.data.Store;

import java.io.File;
import java.util.Locale;

/** Surah downloads via system DownloadManager into app-private storage. */
public final class DownloadHelper {
    private DownloadHelper() {}

    private static DownloadManager dm(Context c) {
        return (DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE);
    }

    public static File dir(Context c) {
        File d = new File(c.getApplicationContext().getExternalFilesDir("audio"), "quran");
        d.mkdirs();
        return d;
    }

    public static String fileName(int reciterId, int surahId) {
        return String.format(Locale.US, "r%d_s%03d.mp3", reciterId, surahId);
    }

    public static File fileFor(Context c, int reciterId, int surahId) {
        return new File(dir(c), fileName(reciterId, surahId));
    }

    public static String key(String server, int surahId) {
        return Store.favKey(server, surahId);
    }

    /** Enqueue download; returns false if already downloaded. */
    public static boolean enqueue(Context ctx, int reciterId, String reciterName,
                                  int surahId, String surahName, String server, String url) {
        Context app = ctx.getApplicationContext();
        String k = key(server, surahId);
        File f = fileFor(app, reciterId, surahId);
        if (f.exists() && f.length() > 1000) {
            Store.Dl d = new Store.Dl();
            d.key = k;
            d.surahId = surahId;
            d.surahName = surahName;
            d.reciterName = reciterName;
            d.server = server;
            d.path = f.getAbsolutePath();
            d.done = true;
            Store.upsertDl(app, d);
            return false;
        }
        Store.Dl old = Store.findDl(app, k);
        if (old != null && !old.done) return true; // already in progress
        try {
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setTitle(surahName + " - " + reciterName);
            req.setDescription(surahName);
            req.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setAllowedOverMetered(true);
            req.setAllowedOverRoaming(true);
            req.setDestinationUri(Uri.fromFile(f));
            long id = dm(app).enqueue(req);
            Store.Dl d = new Store.Dl();
            d.key = k;
            d.surahId = surahId;
            d.surahName = surahName;
            d.reciterName = reciterName;
            d.server = server;
            d.path = f.getAbsolutePath();
            d.dlId = id;
            d.done = false;
            Store.upsertDl(app, d);
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    /** Mark completed downloads as done (call on library open + on download complete). */
    public static void refreshStatuses(Context ctx) {
        Context app = ctx.getApplicationContext();
        for (Store.Dl d : Store.getDls(app)) {
            if (d.done) {
                File f = d.path == null ? null : new File(d.path);
                if (f == null || !f.exists()) {
                    Store.removeDl(app, d.key);
                }
                continue;
            }
            boolean done = false;
            try {
                DownloadManager.Query q = new DownloadManager.Query().setFilterById(d.dlId);
                Cursor cur = dm(app).query(q);
                if (cur != null) {
                    try {
                        if (cur.moveToFirst()) {
                            int st = cur.getInt(
                                    cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                            done = (st == DownloadManager.STATUS_SUCCESSFUL);
                            if (st == DownloadManager.STATUS_FAILED) {
                                Store.removeDl(app, d.key);
                                continue;
                            }
                        }
                    } finally {
                        cur.close();
                    }
                }
            } catch (Exception ignored) {}
            File f = d.path == null ? null : new File(d.path);
            if (!done && f != null && f.exists() && f.length() > 1000) done = true;
            if (done) {
                d.done = true;
                Store.upsertDl(app, d);
            }
        }
    }

    public static boolean isDownloaded(Context c, String server, int surahId) {
        Store.Dl d = Store.findDl(c.getApplicationContext(), key(server, surahId));
        if (d == null || !d.done) return false;
        return d.path != null && new File(d.path).exists();
    }

    public static String offlinePath(Context c, String server, int surahId) {
        Store.Dl d = Store.findDl(c.getApplicationContext(), key(server, surahId));
        if (d != null && d.done && d.path != null && new File(d.path).exists()) return d.path;
        return null;
    }

    public static void delete(Context c, String key) {
        Context app = c.getApplicationContext();
        Store.Dl d = Store.findDl(app, key);
        if (d != null) {
            try {
                if (d.dlId > 0) dm(app).remove(d.dlId);
            } catch (Exception ignored) {}
            try {
                if (d.path != null) new File(d.path).delete();
            } catch (Exception ignored) {}
            Store.removeDl(app, key);
        }
    }
}
