package com.quranpro.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/** App preferences: current reciter, favorites, downloads, last listen, settings. */
public final class Store {
    private Store() {}

    private static final String P = "qprefs";
    private static final Gson G = new Gson();

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(P, Context.MODE_PRIVATE);
    }

    // ---------- theme ----------

    public static int themeMode(Context c) {
        int t = p(c).getInt("theme", 0);
        if (t == 1) return AppCompatDelegate.MODE_NIGHT_NO;
        if (t == 2) return AppCompatDelegate.MODE_NIGHT_YES;
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }

    public static int themePref(Context c) {
        return p(c).getInt("theme", 0);
    }

    public static void setThemePref(Context c, int t) {
        p(c).edit().putInt("theme", t).apply();
        AppCompatDelegate.setDefaultNightMode(themeMode(c));
    }

    // ---------- settings ----------

    public static boolean autoplay(Context c) {
        return p(c).getBoolean("autoplay", true);
    }

    public static void setAutoplay(Context c, boolean v) {
        p(c).edit().putBoolean("autoplay", v).apply();
    }

    public static float textSize(Context c) {
        return p(c).getFloat("textSize", 20f);
    }

    public static void setTextSize(Context c, float v) {
        p(c).edit().putFloat("textSize", v).apply();
    }

    // ---------- current reciter ----------

    public static class Current {
        public int reciterId;
        public String reciterName;
        public int moshafId;
        public String moshafName;
        public String server;
        public String surahList;
    }

    public static Current getCurrent(Context c) {
        String j = p(c).getString("current", null);
        if (j == null) return null;
        try {
            return G.fromJson(j, Current.class);
        } catch (Exception e) {
            return null;
        }
    }

    public static void setCurrent(Context c, int reciterId, String reciterName,
                                  int moshafId, String moshafName, String server, String surahList) {
        Current cur = new Current();
        cur.reciterId = reciterId;
        cur.reciterName = reciterName;
        cur.moshafId = moshafId;
        cur.moshafName = moshafName;
        cur.server = server;
        cur.surahList = surahList;
        p(c).edit().putString("current", G.toJson(cur)).apply();
    }

    public static Models.Moshaf currentMoshaf(Context c) {
        Current cur = getCurrent(c);
        if (cur == null || cur.server == null) return null;
        Models.Moshaf m = new Models.Moshaf();
        m.id = cur.moshafId;
        m.name = cur.moshafName == null ? "" : cur.moshafName;
        m.server = cur.server;
        m.list = cur.surahList == null ? "" : cur.surahList;
        return m;
    }

    // ---------- favorites ----------

    public static class Fav {
        public String key;
        public int surahId;
        public String surahName;
        public String reciterName;
        public String moshafName;
        public String server;
    }

    public static String favKey(String server, int surahId) {
        return (server == null ? "" : server) + "|" + surahId;
    }

    private static List<Fav> favs(Context c) {
        String j = p(c).getString("favs", null);
        if (j == null) return new ArrayList<>();
        try {
            Type t = new TypeToken<List<Fav>>() {}.getType();
            List<Fav> l = G.fromJson(j, t);
            return l == null ? new ArrayList<>() : l;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static List<Fav> getFavs(Context c) {
        return favs(c);
    }

    public static boolean isFav(Context c, String key) {
        for (Fav f : favs(c)) if (key.equals(f.key)) return true;
        return false;
    }

    public static void toggleFav(Context c, Fav f) {
        List<Fav> l = favs(c);
        boolean found = false;
        for (int i = 0; i < l.size(); i++) {
            if (f.key.equals(l.get(i).key)) {
                l.remove(i);
                found = true;
                break;
            }
        }
        if (!found) l.add(0, f);
        p(c).edit().putString("favs", G.toJson(l)).apply();
    }

    public static void removeFav(Context c, String key) {
        List<Fav> l = favs(c);
        for (int i = 0; i < l.size(); i++) {
            if (key.equals(l.get(i).key)) {
                l.remove(i);
                break;
            }
        }
        p(c).edit().putString("favs", G.toJson(l)).apply();
    }

    // ---------- downloads ----------

    public static class Dl {
        public String key;
        public int surahId;
        public String surahName;
        public String reciterName;
        public String server;
        public String path;
        public long dlId;
        public boolean done;
    }

    private static List<Dl> dls(Context c) {
        String j = p(c).getString("dls", null);
        if (j == null) return new ArrayList<>();
        try {
            Type t = new TypeToken<List<Dl>>() {}.getType();
            List<Dl> l = G.fromJson(j, t);
            return l == null ? new ArrayList<>() : l;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static void saveDls(Context c, List<Dl> l) {
        p(c).edit().putString("dls", G.toJson(l)).apply();
    }

    public static List<Dl> getDls(Context c) {
        return dls(c);
    }

    public static Dl findDl(Context c, String key) {
        for (Dl d : dls(c)) if (key.equals(d.key)) return d;
        return null;
    }

    public static void upsertDl(Context c, Dl d) {
        List<Dl> l = dls(c);
        for (int i = 0; i < l.size(); i++) {
            if (d.key.equals(l.get(i).key)) {
                l.set(i, d);
                saveDls(c, l);
                return;
            }
        }
        l.add(0, d);
        saveDls(c, l);
    }

    public static void removeDl(Context c, String key) {
        List<Dl> l = dls(c);
        for (int i = 0; i < l.size(); i++) {
            if (key.equals(l.get(i).key)) {
                l.remove(i);
                break;
            }
        }
        saveDls(c, l);
    }

    // ---------- last listen ----------

    public static void setLast(Context c, int surahId, String surahName,
                               String reciterName, String server) {
        p(c).edit()
                .putInt("lastSurah", surahId)
                .putString("lastSurahName", surahName)
                .putString("lastReciter", reciterName)
                .putString("lastServer", server)
                .apply();
    }

    public static int lastSurah(Context c) {
        return p(c).getInt("lastSurah", 0);
    }

    public static String lastSurahName(Context c) {
        return p(c).getString("lastSurahName", "");
    }

    public static String lastReciter(Context c) {
        return p(c).getString("lastReciter", "");
    }

    public static String lastServer(Context c) {
        return p(c).getString("lastServer", "");
    }
}
