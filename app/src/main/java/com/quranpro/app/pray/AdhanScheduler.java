package com.quranpro.app.pray;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.quranpro.app.data.PrayerCalc;
import com.quranpro.app.data.PrayerTimes;
import com.quranpro.app.data.Store;

import java.util.Calendar;

/**
 * Schedules exact alarms for every enabled adhan of the day + a midnight refresh alarm
 * that re-fetches tomorrow's timings.
 *
 * <p>Tomorrow's prayers are scheduled as well (from the on-device calculation when the
 * network is unavailable), so the adhan keeps firing even if the phone is off overnight
 * or the refresh alarm is missed — and it needs no internet at all, since the times can
 * be computed locally.
 */
public final class AdhanScheduler {
    private AdhanScheduler() {}

    public static final String ACTION_FIRE = "com.quranpro.app.ADHAN_FIRE";
    public static final String ACTION_REFRESH = "com.quranpro.app.ADHAN_REFRESH";

    public static final String EXTRA_PRAYER = "prayer";
    public static final String EXTRA_PRE = "pre";
    /** Request code offset for tomorrow's alarms (so they do not clash with today's). */
    private static final int TOMORROW_BASE = 600;

    private static PendingIntent pi(Context c, int code, Intent i) {
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(c.getApplicationContext(), code, i, fl);
    }

    private static Intent fireIntent(Context c, int prayerIdx, boolean pre, boolean tomorrow) {
        Intent i = new Intent(c.getApplicationContext(), AdhanReceiver.class);
        i.setAction(ACTION_FIRE);
        i.putExtra(EXTRA_PRAYER, prayerIdx);
        i.putExtra(EXTRA_PRE, pre);
        i.putExtra("tomorrow", tomorrow);
        return i;
    }

    /** Cancel everything, then schedule the remaining adhan for today + tomorrow. */
    public static synchronized void rescheduleAll(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        // cancel previous alarms
        for (int i : PrayerTimes.PRAYERS) {
            for (boolean pre : new boolean[]{false, true}) {
                am.cancel(pi(ctx, reqCode(i, pre, false), fireIntent(ctx, i, pre, false)));
                am.cancel(pi(ctx, reqCode(i, pre, true), fireIntent(ctx, i, pre, true)));
            }
        }
        am.cancel(pi(ctx, 900, new Intent(ctx, AdhanReceiver.class).setAction(ACTION_REFRESH)));

        if (!Store.adhanMaster(ctx)) return;

        long now = System.currentTimeMillis();
        int pre = Math.max(0, Store.adhanPreMin(ctx));

        for (int dayOffset = 0; dayOffset <= 1; dayOffset++) {
            // today: cached API timings when available, otherwise the local calculation
            PrayerTimes pt = PrayerCalc.day(ctx, dayOffset);
            if (pt == null || !pt.hasTimes()) continue;
            boolean tomorrow = dayOffset == 1;
            for (int i : PrayerTimes.PRAYERS) {
                if (!Store.adhanOn(ctx, keyFor(i))) continue;
                long t = pt.times[i];
                if (t <= 0 || t <= now) continue;
                setAlarm(ctx, am, reqCode(i, false, tomorrow),
                        fireIntent(ctx, i, false, tomorrow), t);
                if (pre > 0) {
                    long preAt = t - pre * 60_000L;
                    if (preAt > now) {
                        setAlarm(ctx, am, reqCode(i, true, tomorrow),
                                fireIntent(ctx, i, true, tomorrow), preAt);
                    }
                }
            }
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

    private static int reqCode(int prayerIdx, boolean pre, boolean tomorrow) {
        return (tomorrow ? TOMORROW_BASE : 400) + prayerIdx * 10 + (pre ? 1 : 0);
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
        PrayerTimes pt = PrayerCalc.day(ctx, 0);
        if (pt != null && pt.hasTimes()) return pt;
        return null;
    }

    public static String todayKey() {
        return PrayerTimes.key(Calendar.getInstance());
    }
}
