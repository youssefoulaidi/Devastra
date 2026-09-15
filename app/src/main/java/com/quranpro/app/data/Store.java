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
        Context app = c == null ? null : c.getApplicationContext();
        return (app == null ? c : app).getSharedPreferences(P, Context.MODE_PRIVATE);
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

    public static String lang(Context c) {
        String v = p(c).getString("lang", "auto");
        return "ar".equals(v) || "en".equals(v) ? v : "auto";
    }

    public static void setLang(Context c, String v) {
        if (!"ar".equals(v) && !"en".equals(v) && !"auto".equals(v)) v = "auto";
        p(c).edit().putString("lang", v).apply();
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

    // ---------- prayer times & adhan ----------

    public static void setLocation(Context c, double lat, double lon, String label) {
        p(c).edit()
                .putFloat("prLat", (float) lat)
                .putFloat("prLon", (float) lon)
                .putString("prLabel", label == null ? "" : label)
                .apply();
    }

    public static double[] location(Context c) {
        SharedPreferences sp = p(c);
        if (!sp.contains("prLat") || !sp.contains("prLon")) return null;
        return new double[]{sp.getFloat("prLat", 0f), sp.getFloat("prLon", 0f)};
    }

    public static String locationLabel(Context c) {
        return p(c).getString("prLabel", "");
    }

    public static int prayerMethod(Context c) {
        return p(c).getInt("prMethod", 3); // 3 = Muslim World League
    }

    /** 0 = standard (Shafi'i/Maliki/Hanbali), 1 = Hanafi — affects the Asr time. */
    public static int asrSchool(Context c) {
        return p(c).getInt("prSchool", 0);
    }

    public static void setAsrSchool(Context c, int s) {
        p(c).edit().putInt("prSchool", s <= 0 ? 0 : 1).apply();
    }

    public static void setPrayerMethod(Context c, int m) {
        p(c).edit().putInt("prMethod", m).apply();
    }

    /** Cache of the last fetched timings response + the day it belongs to. */
    public static String prTimesJson(Context c) {
        return p(c).getString("prTimesJson", null);
    }

    public static String prTimesDate(Context c) {
        return p(c).getString("prTimesDate", "");
    }

    public static void setPrTimes(Context c, String json, String ddMmYyyy) {
        p(c).edit().putString("prTimesJson", json).putString("prTimesDate", ddMmYyyy).apply();
    }

    /** Master switch for all adhan alerts. */
    public static boolean adhanMaster(Context c) {
        return p(c).getBoolean("adhanMaster", false);
    }

    public static void setAdhanMaster(Context c, boolean v) {
        p(c).edit().putBoolean("adhanMaster", v).apply();
    }

    public static boolean adhanOn(Context c, String key) {
        return p(c).getBoolean("adhanOn." + key, true);
    }

    public static void setAdhanOn(Context c, String key, boolean v) {
        p(c).edit().putBoolean("adhanOn." + key, v).apply();
    }

    /** -1 = silent notification, otherwise index in Muezzins list. */
    public static int adhanVoice(Context c) {
        return p(c).getInt("adhanVoice", 0);
    }

    public static void setAdhanVoice(Context c, int v) {
        p(c).edit().putInt("adhanVoice", v).apply();
    }

    /** -2 = inherit main voice, -1 = silent notification, otherwise a muezzin index. */
    public static int adhanFajrVoice(Context c) {
        return p(c).getInt("adhanFajrVoice", -2);
    }

    public static void setAdhanFajrVoice(Context c, int v) {
        p(c).edit().putInt("adhanFajrVoice", v).apply();
    }

    public static int adhanPreMin(Context c) {
        return p(c).getInt("adhanPre", 0);
    }

    public static void setAdhanPreMin(Context c, int m) {
        p(c).edit().putInt("adhanPre", m).apply();
    }

    public static int adhanStopMin(Context c) {
        return p(c).getInt("adhanStop", 5);
    }

    public static void setAdhanStopMin(Context c, int m) {
        p(c).edit().putInt("adhanStop", m).apply();
    }

    public static boolean adhanVibrate(Context c) {
        return p(c).getBoolean("adhanVib", true);
    }

    public static void setAdhanVibrate(Context c, boolean v) {
        p(c).edit().putBoolean("adhanVib", v).apply();
    }

    /** Last day we scheduled alarms for (DD-MM-YYYY) — avoids double-scheduling. */
    public static String adhanScheduledDay(Context c) {
        return p(c).getString("adhanSchedDay", "");
    }

    public static void setAdhanScheduledDay(Context c, String d) {
        p(c).edit().putString("adhanSchedDay", d).apply();
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
