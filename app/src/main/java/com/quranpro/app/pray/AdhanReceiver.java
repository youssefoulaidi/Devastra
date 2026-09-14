package com.quranpro.app.pray;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.quranpro.app.data.Api;
import com.quranpro.app.data.PrayerTimes;
import com.quranpro.app.data.Store;

/** Entry point for adhan alarms, the midnight refresh and boot re-scheduling. */
public class AdhanReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String a = intent == null || intent.getAction() == null
                ? "" : intent.getAction();

        if (Intent.ACTION_BOOT_COMPLETED.equals(a)
                || "android.intent.action.MY_PACKAGE_REPLACED".equals(a)
                || "android.intent.action.TIME_SET".equals(a)
                || "android.intent.action.TIMEZONE_CHANGED".equals(a)) {
            AdhanScheduler.rescheduleAll(ctx);
            if (Store.adhanMaster(ctx)) refreshTimes(ctx); // keep the day's data fresh
            return;
        }

        if (AdhanScheduler.ACTION_REFRESH.equals(a)) {
            AdhanScheduler.rescheduleAll(ctx);
            refreshTimes(ctx);
            return;
        }

        if (AdhanScheduler.ACTION_FIRE.equals(a)) {
            final int prayer = intent.getIntExtra(AdhanScheduler.EXTRA_PRAYER, PrayerTimes.FAJR);
            final boolean pre = intent.getBooleanExtra(AdhanScheduler.EXTRA_PRE, false);
            Intent in = new Intent(ctx, AdhanService.class);
            in.putExtra(AdhanService.EXTRA_PRAYER, prayer);
            in.putExtra(AdhanService.EXTRA_PRE, pre);
            try {
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(in);
                else ctx.startService(in);
            } catch (Exception e) {
                // FGS not allowed in background — still notify.
                AdhanService.notifySilently(ctx.getApplicationContext(), prayer, pre);
            }
        }
    }

    /** Re-fetch today's timings in background, cache them, then re-schedule alarms. */
    public static void refreshTimes(final Context ctx) {
        final Context app = ctx.getApplicationContext();
        double[] loc = Store.location(app);
        if (loc == null) return;
        Api.fetchPrayerTimes(app, loc[0], loc[1], Store.prayerMethod(app), null,
                new Api.Cb<String>() {
                    @Override public void ok(String v) {
                        try {
                            PrayerTimes p = PrayerTimes.parse(v);
                            Store.setPrTimes(app, v, AdhanScheduler.todayKey());
                        } catch (Exception ignored) {}
                        AdhanScheduler.rescheduleAll(app);
                    }

                    @Override public void err(String m) {
                        // network hiccup — retry once after 30 min, keep old alarms
                        java.util.Calendar c = java.util.Calendar.getInstance();
                        c.add(java.util.Calendar.MINUTE, 30);
                        AlarmManager am = (AlarmManager)
                                app.getSystemService(Context.ALARM_SERVICE);
                        if (am != null) {
                            Intent i = new Intent(app, AdhanReceiver.class)
                                    .setAction(AdhanScheduler.ACTION_REFRESH);
                            int fl = PendingIntent.FLAG_UPDATE_CURRENT
                                    | PendingIntent.FLAG_IMMUTABLE;
                            am.set(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(),
                                    PendingIntent.getBroadcast(app, 901, i, fl));
                        }
                    }
                });
    }
}
