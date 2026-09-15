package com.quranpro.app.pray;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.quranpro.app.data.PrayerTimes;
import com.quranpro.app.data.Store;

import java.util.Calendar;
import java.util.Locale;

/**
 * Schedules exact alarms for every enabled adhan of the day + a midnight
 * refresh alarm that re-fetches tomorrow's timings.
 */
public final class AdhanScheduler {
    private AdhanScheduler() {}

    public static final String ACTION_FIRE = "com.quranpro.app.ADHAN_FIRE";
    public static final String ACTION_REFRESH = "com.quranpro.app.ADHAN_REFRESH";

    public static final String EXTRA_PRAYER = "prayer";
    public static final String EXTRA_PRE = "pre";

    private static PendingIntent pi(Context c, int code, Intent i) {
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(c.getApplicationContext(), code, i, fl);
    }

    private static Intent fireIntent(Context c, int prayerIdx, boolean pre) {
        Intent i = new Intent(c.getApplicationContext(), AdhanReceiver.class);
        i.setAction(ACTION_FIRE);
        i.putExtra(EXTRA_PRAYER, prayerIdx);
        i.putExtra(EXTRA_PRE, pre);
        return i;
    }

    /** Cancel everything, then schedule remaining adhan for today + daily refresh. */
    public static synchronized void rescheduleAll(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        // cancel previous alarms
        for (int i : PrayerTimes.PRAYERS) {
            for (boolean pre : new boolean[]{false, true}) {
                am.cancel(pi(ctx, reqCode(i, pre), fireIntent(ctx, i, pre)));
            }
        }
        am.cancel(pi(ctx, 900, new Intent(ctx, AdhanReceiver.class).setAction(ACTION_REFRESH)));

        if (!Store.adhanMaster(ctx)) return;

        long now = System.currentTimeMillis();
        PrayerTimes pt = loadToday(ctx);
        if (pt != null) {
            for (int i : PrayerTimes.PRAYERS) {
                String key = keyFor(i);
                if (!Store.adhanOn(ctx, key)) continue;
                if (pt.times[i] > now) {
                    setAlarm(ctx, am, reqCode(i, false), fireIntent(ctx, i, false), pt.times[i]);
                    int pre = Store.adhanPreMin(ctx);
                    if (pre > 0 && pt.times[i] - pre * 60_000L > now) {
                        setAlarm(ctx, am, reqCode(i, true), fireIntent(ctx, i, true),
                                pt.times[i] - pre * 60_000L);
                    }
                }
            }
        } else if (Store.location(ctx) != null) {
            AdhanReceiver.refreshTimes(ctx);
        }
        // daily refresh just after midnight (local)
        Calendar next = Calendar.getInstance();
        next.add(Calendar.DAY_OF_YEAR, 1);
        next.set(Calendar.HOUR_OF_DAY, 0);
        next.set(Calendar.MINUTE, 3);
        next.set(Calendar.SECOND, 0);
        setAlarm(ctx, am, 900,
                new Intent(ctx, AdhanReceiver.class).setAction(ACTION_REFRESH),
                next.getTimeInMillis());
    }

    private static void setAlarm(Context ctx, AlarmManager am, int code,
                                 Intent i, long triggerAt) {
        PendingIntent pi = pi(ctx, code, i);
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
        } catch (Exception e) {
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi); // never crash
        }
    }

    /** True when the OS currently allows exact alarms (Android 12+ can revoke them). */
    public static boolean exactAllowed(Context ctx) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        return am == null || am.canScheduleExactAlarms();
    }

    private static int reqCode(int prayerIdx, boolean pre) {
        return 400 + prayerIdx * 10 + (pre ? 1 : 0);
    }

    public static String keyFor(int prayerIdx) {
        switch (prayerIdx) {
            case PrayerTimes.FAJR: return "fajr";
            case PrayerTimes.DHUHR: return "dhuhr";
            case PrayerTimes.ASR: return "asr";
            case PrayerTimes.MAGHRIB: return "maghrib";
            case PrayerTimes.ISHA: return "isha";
            default: return "sunrise";
        }
    }

    public static int nameRes(int prayerIdx) {
        switch (prayerIdx) {
            case PrayerTimes.FAJR: return com.quranpro.app.R.string.pt_fajr;
            case PrayerTimes.SUNRISE: return com.quranpro.app.R.string.pt_sunrise;
            case PrayerTimes.DHUHR: return com.quranpro.app.R.string.pt_dhuhr;
            case PrayerTimes.ASR: return com.quranpro.app.R.string.pt_asr;
            case PrayerTimes.MAGHRIB: return com.quranpro.app.R.string.pt_maghrib;
            case PrayerTimes.ISHA: return com.quranpro.app.R.string.pt_isha;
            default: return com.quranpro.app.R.string.pt_fajr;
        }
    }

    /** Loads today's cached timings if they belong to today (device date). */
    public static PrayerTimes loadToday(Context ctx) {
        String json = Store.prTimesJson(ctx);
        if (json == null) return null;
        try {
            PrayerTimes pt = PrayerTimes.parse(json);
            if (todayKey().equals(Store.prTimesDate(ctx)) || todayKey().equals(pt.date)) {
                return pt;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static String todayKey() {
        return String.format(Locale.US, "%td-%tm-%tY",
                Calendar.getInstance(), Calendar.getInstance(), Calendar.getInstance());
    }
}
