package com.quranpro.app.data;

import android.content.Context;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Prayer times computed on the device (no network) for the saved location.
 *
 * <p>Used when the Aladhan request fails or its cached response does not belong to the
 * requested day — this is what makes the adhan and the prayer-times screen keep working
 * after days without internet. The astronomical algorithm is the classic sun-declination /
 * equation-of-time computation (same family used by PrayTimes.org), with the same
 * Fajr/Isha angles as the Aladhan method ids the app already exposes.
 */
public final class PrayerCalc {
    private PrayerCalc() {}

    /** {fajrAngle, ishaAngle, ishaMinutesAfterMaghrib} — ishaMinutes wins when > 0. */
    private static double[] methodAngles(int method) {
        switch (method) {
            case 0:  return new double[]{16, 14, 0};      // Shia Ithna-Ashari
            case 1:  return new double[]{18, 18, 0};      // Karachi
            case 2:  return new double[]{15, 15, 0};      // ISNA
            case 4:  return new double[]{18.5, 0, 90};    // Umm al-Qura
            case 5:  return new double[]{19.5, 17.5, 0};  // Egyptian
            case 7:  return new double[]{17.7, 14, 0};    // Tehran
            case 8:  return new double[]{19.5, 0, 90};    // Gulf
            case 9:  return new double[]{18, 17.5, 0};    // Kuwait
            case 10: return new double[]{18, 0, 90};      // Qatar
            case 11: return new double[]{20, 18, 0};      // Singapore
            case 12: return new double[]{12, 12, 0};      // UOIF France
            case 13: return new double[]{18, 17, 0};      // Diyanet Turkey
            case 14: return new double[]{16, 15, 0};      // Russia
            case 15: return new double[]{18, 18, 0};      // Moonsighting Committee
            case 16: return new double[]{18.2, 18.2, 0};  // Dubai
            case 17: return new double[]{20, 18, 0};      // JAKIM Malaysia
            case 18: return new double[]{18, 18, 0};      // Tunisia
            case 19: return new double[]{18, 17, 0};      // Algeria
            case 20: return new double[]{20, 18, 0};      // Kemenag Indonesia
            case 21: return new double[]{19, 17, 0};      // Morocco
            case 22: return new double[]{18, 17, 0};      // Lisbon
            case 23: return new double[]{18, 18, 0};      // Jordan
            default: return new double[]{18, 17, 0};      // MWL (id 3) & fallback
        }
    }

    /**
     * Prayer times for a day using the cached Aladhan response when it matches the day,
     * otherwise the on-device calculation. {@code dayOffset} 0 = today, 1 = tomorrow…
     */
    public static PrayerTimes day(Context c, int dayOffset) {
        double[] loc = Store.location(c);
        if (loc == null) return null;
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, dayOffset);
        Calendar today = Calendar.getInstance();
        boolean isToday = dayOffset == 0 || sameDay(cal, today);

        if (isToday) {
            String json = Store.prTimesJson(c);
            if (json != null) {
                try {
                    PrayerTimes pt = PrayerTimes.parse(json);
                    String key = PrayerTimes.key(cal);
                    if (key.equals(Store.prTimesDate(c)) || key.equals(pt.date)) return pt;
                } catch (Exception ignored) {}
            }
        }
        return compute(c, loc[0], loc[1], cal);
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    /** On-device calculation for the given calendar day. */
    public static PrayerTimes compute(Context c, double lat, double lon, Calendar day) {
        TimeZone tz = TimeZone.getDefault();
        int method = Store.prayerMethod(c);
        int school = Store.asrSchool(c);
        double[] angles = methodAngles(method);

        int year = day.get(Calendar.YEAR);
        int month = day.get(Calendar.MONTH) + 1;
        int dom = day.get(Calendar.DAY_OF_MONTH);

        double[] hoursUtc = computeUtc(lat, lon, year, month, dom,
                angles[0], angles[1], angles[2], school == 1 ? 2 : 1);

        PrayerTimes pt = new PrayerTimes();
        pt.tzId = tz.getID();
        pt.date = String.format(Locale.US, "%02d-%02d-%04d", dom, month, year);
        pt.readable = String.format(Locale.US, "%02d/%02d/%04d", dom, month, year);
        pt.gDay = dom;
        pt.gMonth = month;
        pt.gYear = year;
        pt.hijri = ""; // stays empty offline — the screen hides it

        Calendar base = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        base.clear();
        base.set(year, month - 1, dom, 0, 0, 0);
        long utcMidnight = base.getTimeInMillis();

        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("HH:mm", Locale.US);
        fmt.setTimeZone(tz);
        for (int i = 0; i < 6; i++) {
            double h = hoursUtc[i];
            if (Double.isNaN(h)) {
                pt.raw[i] = "";
                pt.times[i] = -1;
                continue;
            }
            long ms = utcMidnight + Math.round(h * 3600_000d);
            pt.times[i] = ms;
            pt.raw[i] = fmt.format(new java.util.Date(ms));
        }
        return pt;
    }

    /**
     * Returns {fajr, sunrise, dhuhr, asr, maghrib, isha} as fractional hours in UTC,
     * or NaN when the sun never reaches the required altitude (extreme latitudes).
     */
    private static double[] computeUtc(double lat, double lon, int year, int month, int dom,
                                       double fajrAngle, double ishaAngle,
                                       double ishaMinutes, double asrShadow) {
        double[] out = new double[]{Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN};

        double jd = julianDay(year, month, dom) - lon / (15d * 24d);
        double d = jd - 2451545.0;
        double g = fixAngle(357.529 + 0.98560028 * d);
        double q = fixAngle(280.459 + 0.98564736 * d);
        double l = fixAngle(q + 1.915 * sinDeg(g) + 0.020 * sinDeg(2 * g));
        double e = 23.439 - 0.00000036 * d;

        double decl = asinDeg(sinDeg(e) * sinDeg(l));
        double ra = fixHour(atan2Deg(cosDeg(e) * sinDeg(l), cosDeg(l)) / 15d);
        double eqt = q / 15d - ra;

        double dhuhr = fixHour(12d - eqt);
        double sunrise = dhuhr - hourAngle(0.833, lat, decl);
        double maghrib = dhuhr + hourAngle(0.833, lat, decl);
        double fajr = dhuhr - hourAngle(fajrAngle, lat, decl);
        double isha;
        if (ishaMinutes > 0) isha = maghrib + ishaMinutes / 60d;
        else isha = dhuhr + hourAngle(ishaAngle, lat, decl);
        double asr = dhuhr + asrTime(asrShadow, lat, decl);

        double nightLength = 24d - (maghrib - sunrise);
        if (Double.isNaN(fajr) || Double.isInfinite(fajr)) fajr = sunrise - nightLength / 7d;
        if (Double.isNaN(isha) || Double.isInfinite(isha)) isha = maghrib + nightLength / 7d;

        // The computation above is in local *solar* time; convert to UTC.
        //
        // Do NOT wrap the result into 0..24 here: the caller turns these numbers into
        // epoch millis as `utcMidnight + hours`, and a location east of UTC needs a
        // *negative* offset for the early prayers. Wrapping Fajr/Sunrise forward by a
        // whole day (what `fixHour` did) dated them tomorrow — so in Tokyo, Jakarta,
        // Kuala Lumpur… the offline calculation showed 16 Sep for Dhuhr but 17 Sep for
        // Fajr, the countdown picked the wrong "next prayer", and today's Fajr adhan
        // was never scheduled.
        double shift = lon / 15d;
        out[0] = fajr - shift;
        out[1] = sunrise - shift;
        out[2] = dhuhr - shift;
        out[3] = asr - shift;
        out[4] = maghrib - shift;
        out[5] = isha - shift;
        return out;
    }

    /** Sun altitude → hour angle (hours). */
    private static double hourAngle(double altitude, double lat, double decl) {
        double cosH = (-sinDeg(altitude) - sinDeg(lat) * sinDeg(decl))
                / (cosDeg(lat) * cosDeg(decl));
        if (cosH > 1 || cosH < -1) return Double.NaN;
        return acosDeg(cosH) / 15d;
    }

    /** Asr: shadow length equals {@code shadow}× the object height. */
    private static double asrTime(double shadow, double lat, double decl) {
        double angle = -atanDeg(1d / (shadow + tanDeg(Math.abs(lat - decl))));
        return hourAngle(angle, lat, decl);
    }

    private static double julianDay(int year, int month, int day) {
        if (month <= 2) {
            year -= 1;
            month += 12;
        }
        double a = Math.floor(year / 100d);
        double b = 2 - a + Math.floor(a / 4d);
        return Math.floor(365.25 * (year + 4716)) + Math.floor(30.6001 * (month + 1))
                + day + b - 1524.5;
    }

    private static double fixAngle(double a) {
        a = a - 360d * Math.floor(a / 360d);
        return a < 0 ? a + 360d : a;
    }

    private static double fixHour(double a) {
        a = a - 24d * Math.floor(a / 24d);
        return a < 0 ? a + 24d : a;
    }

    private static double sinDeg(double x) { return Math.sin(Math.toRadians(x)); }

    private static double cosDeg(double x) { return Math.cos(Math.toRadians(x)); }

    private static double tanDeg(double x) { return Math.tan(Math.toRadians(x)); }

    private static double asinDeg(double x) { return Math.toDegrees(Math.asin(x)); }

    private static double acosDeg(double x) { return Math.toDegrees(Math.acos(x)); }

    private static double atanDeg(double x) { return Math.toDegrees(Math.atan(x)); }

    private static double atan2Deg(double y, double x) { return Math.toDegrees(Math.atan2(y, x)); }
}
