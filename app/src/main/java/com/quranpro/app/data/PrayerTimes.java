package com.quranpro.app.data;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * One day of prayer timings parsed from the Aladhan API.
 * Displays the "HH:mm" strings as published for the location's timezone, and
 * also exposes epoch millis (same timezone) for countdowns and alarm scheduling.
 */
public class PrayerTimes {

    public static final int FAJR = 0, SUNRISE = 1, DHUHR = 2, ASR = 3, MAGHRIB = 4, ISHA = 5;
    /** The five salat (no sunrise). */
    public static final int[] PRAYERS = {FAJR, DHUHR, ASR, MAGHRIB, ISHA};

    private static final String[] KEYS =
            {"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};

    public String date = "";      // dd-MM-yyyy
    public String readable = "";  // e.g. "14 Sep 2026"
    public String hijri = "";     // e.g. "٣ ربيع الآخر ١٤٤٨"
    public String tzId = TimeZone.getDefault().getID();
    public final String[] raw = new String[6];
    public final long[] times = new long[6];

    public static PrayerTimes parse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        if (root.optInt("code", root.optInt("status_code", 200)) >= 400) {
            throw new Exception("api error");
        }
        JSONObject data = root.getJSONObject("data");
        JSONObject t = data.getJSONObject("timings");
        JSONObject meta = data.optJSONObject("meta");
        PrayerTimes pt = new PrayerTimes();
        if (meta != null) {
            pt.tzId = meta.optString("timezone", pt.tzId);
        }
        JSONObject d = data.optJSONObject("date");
        if (d != null) {
            pt.readable = d.optString("readable", "");
            JSONObject g = d.optJSONObject("gregorian");
            if (g != null) {
                // aladhan "date.gregorian.date" is dd-MM-yyyy
                pt.date = g.optString("date", "");
            }
            try {
                JSONObject h = d.getJSONObject("hijri");
                String day = h.optString("day", "");
                String year = h.optString("year", "");
                JSONObject hm = h.optJSONObject("month");
                String mon = hm == null ? ""
                        : hm.optString(Locale.getDefault().getLanguage(),
                        hm.optString("ar", hm.optString("en", "")));
                if (!day.isEmpty() && !year.isEmpty()) {
                    pt.hijri = (mon.isEmpty() ? "" : mon + " ") + day + " " + year;
                    // reorder: "day month year"
                    pt.hijri = day + (mon.isEmpty() ? "" : " " + mon) + " " + year;
                }
            } catch (Exception ignored) {}
        }
        if (pt.date.isEmpty()) {
            pt.date = String.format(Locale.US, "%td-%tm-%tY",
                    Calendar.getInstance(), Calendar.getInstance(), Calendar.getInstance());
        }
        TimeZone tz = TimeZone.getTimeZone(pt.tzId);
        Calendar dayCal = dayFrom(pt.date, tz);
        for (int i = 0; i < 6; i++) {
            String hhmm = stripTail(t.optString(KEYS[i], ""));
            pt.raw[i] = hhmm;
            pt.times[i] = atTime(dayCal, hhmm);
        }
        return pt;
    }

    private static String stripTail(String s) {
        if (s == null) return "";
        s = s.trim();
        int sp = s.indexOf(' ');
        if (sp > 0) s = s.substring(0, sp);
        return s;
    }

    /** dayCal positioned at 00:00 of the given day in tz. */
    private static Calendar dayFrom(String ddMmYyyy, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz);
        try {
            String[] p = ddMmYyyy.split("-");
            int y = Integer.parseInt(p[2].trim());
            int mo = Integer.parseInt(p[1].trim());
            int da = Integer.parseInt(p[0].trim());
            c.set(y, mo - 1, da, 0, 0, 0);
            c.set(Calendar.MILLISECOND, 0);
            return c;
        } catch (Exception e) {
            Calendar now = Calendar.getInstance(tz);
            now.set(Calendar.HOUR_OF_DAY, 0);
            now.set(Calendar.MINUTE, 0);
            now.set(Calendar.SECOND, 0);
            now.set(Calendar.MILLISECOND, 0);
            return now;
        }
    }

    /** "HH:mm" on the base day → epoch millis, or -1. */
    private static long atTime(Calendar dayBase, String hhmm) {
        String[] hm = hhmm.split(":");
        if (hm.length < 2) return -1;
        try {
            Calendar c = (Calendar) dayBase.clone();
            c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hm[0].trim()));
            c.set(Calendar.MINUTE, Integer.parseInt(hm[1].trim()));
            return c.getTimeInMillis();
        } catch (Exception e) {
            return -1;
        }
    }

    /** Next prayer time strictly after `now`, or -1 when the day is over. */
    public long nextTime(long now) {
        for (int i : PRAYERS) if (times[i] > 0 && times[i] > now) return times[i];
        return -1;
    }

    public int nextIndex(long now) {
        for (int i : PRAYERS) if (times[i] > 0 && times[i] > now) return i;
        return FAJR; // wraps to tomorrow's fajr
    }

    /** Whether there is still a prayer today after `now`. */
    public boolean hasLater(long now) {
        for (int i : PRAYERS) if (times[i] > 0 && times[i] > now) return true;
        return false;
    }

    /** Prayer currently in its time window, or -1 (before fajr / between sunrise & dhuhr). */
    public int currentIndex(long now) {
        if (times[ISHA] > 0 && now >= times[ISHA]) return ISHA;
        if (times[MAGHRIB] > 0 && now >= times[MAGHRIB]) return MAGHRIB;
        if (times[ASR] > 0 && now >= times[ASR]) return ASR;
        if (times[DHUHR] > 0 && now >= times[DHUHR]) return DHUHR;
        if (times[FAJR] > 0 && now >= times[FAJR]
                && (times[SUNRISE] <= 0 || now < times[SUNRISE])) return FAJR;
        return -1;
    }

    public static String arName(int idx) {
        switch (idx) {
            case FAJR: return "الفجر";
            case SUNRISE: return "الشروق";
            case DHUHR: return "الظهر";
            case ASR: return "العصر";
            case MAGHRIB: return "المغرب";
            case ISHA: return "العشاء";
            default: return "";
        }
    }
}
