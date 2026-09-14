package com.quranpro.app.pray;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.core.app.NotificationCompat;

import com.quranpro.app.R;
import com.quranpro.app.data.PrayerTimes;
import com.quranpro.app.data.Store;
import com.quranpro.app.ui.MainActivity;

/** Plays the adhan with a foreground notification; silent alerts for pre-adhan. */
public class AdhanService extends Service {

    public static final String EXTRA_PRAYER = "prayer";
    public static final String EXTRA_PRE = "pre";
    private static final String ACTION_STOP = "qp.adhan.STOP";
    private static final int NOTIF_ID = 4001;
    public static final String CHANNEL = "adhan";

    private MediaPlayer mp;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable autoStop = this::stopAndClean;

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createChannel(this);
        if ((intent != null && ACTION_STOP.equals(intent.getAction()))
                || !Store.adhanMaster(this)) {
            // safe "start then stop" dance so the O+ startForeground contract is met
            try {
                startForeground(NOTIF_ID, baseNotif(getString(R.string.app_short)));
                stopForeground(STOP_FOREGROUND_REMOVE);
            } catch (Exception ignored) {}
            stopAndClean();
            return START_NOT_STICKY;
        }
        boolean pre = intent != null && intent.getBooleanExtra(EXTRA_PRE, false);
        int prayer = intent == null ? PrayerTimes.FAJR : intent.getIntExtra(EXTRA_PRAYER, PrayerTimes.FAJR);

        String title = getString(R.string.adhan_notif_title, getString(AdhanScheduler.nameRes(prayer)));
        Notification n = baseNotif(pre ? getString(R.string.adhan_pre) : title);
        try {
            startForeground(NOTIF_ID, n);
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        vibrate(this);

        if (pre) {
            // Pre-adhan: alert only, stop after a minute.
            h.postDelayed(this::stopAndClean, 60_000L);
            return START_STICKY;
        }

        int voice = Store.adhanVoice(this);
        if (voice >= 0) {
            play(Muezzins.urlFor(voice), Muezzins.fallbackFor(voice), prayer);
            long stopMs = Math.max(1, Store.adhanStopMin(this)) * 60_000L;
            h.postDelayed(autoStop, stopMs);
        } else {
            h.postDelayed(this::stopAndClean, 15_000L);
        }
        return START_STICKY;
    }

    private Notification baseNotif(String text) {
        Intent open = new Intent(this, MainActivity.class);
        open.putExtra("tab", 0);
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 910, open, fl);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_quran)
                .setContentTitle(text == null || text.isEmpty()
                        ? getString(R.string.app_short) : text)
                .setContentText(getString(R.string.adhan_playing))
                .setContentIntent(pi)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC);

        Intent stop = new Intent(this, AdhanService.class).setAction(ACTION_STOP);
        b.addAction(0, getString(R.string.adhan_stop_now),
                PendingIntent.getService(this, 911, stop, fl));
        return b.build();
    }

    private void play(String url, String fallback, int prayer) {
        release();
        try {
            mp = new MediaPlayer();
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                try {
                    am.requestAudioFocus(afListener, AudioManager.STREAM_MUSIC,
                            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
                } catch (Exception ignored) {}
            }
            mp.setDataSource(url);
            mp.setOnErrorListener((m, what, extra) -> {
                if (fallback != null) play(fallback, null, prayer);
                else h.postDelayed(this::stopAndClean, 400L);
                return true;
            });
            mp.setOnCompletionListener(m -> h.postDelayed(this::stopAndClean, 800L));
            mp.prepareAsync();
            mp.setOnPreparedListener(MediaPlayer::start);
        } catch (Exception e) {
            if (fallback != null) play(fallback, null, prayer);
            else h.postDelayed(this::stopAndClean, 800L);
        }
    }

    private final AudioManager.OnAudioFocusChangeListener afListener =
            focus -> {
                if (mp != null) {
                    try {
                        if (focus == AudioManager.AUDIOFOCUS_LOSS) stopAndClean();
                        else if (focus == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                                && mp.isPlaying()) mp.pause();
                        else if (focus == AudioManager.AUDIOFOCUS_GAIN
                                && !mp.isPlaying()) mp.start();
                    } catch (Exception ignored) {}
                }
            };

    public static void vibrate(Context c) {
        if (!Store.adhanVibrate(c)) return;
        try {
            Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(500);
            }
        } catch (Exception ignored) {}
    }

    static void notifySilently(Context ctx, int prayer, boolean pre) {
        createChannel(ctx);
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        String t = pre ? getString(ctx, R.string.adhan_pre)
                : getString(ctx, R.string.adhan_notif_title, getString(ctx, AdhanScheduler.nameRes(prayer)));
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_quran)
                .setContentTitle(t)
                .setContentText(pre ? "" : getString(ctx, R.string.adhan_notif_text,
                        getString(ctx, AdhanScheduler.nameRes(prayer))))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_ALARM);
        Notification n = b.build();
        nm.notify(NOTIF_ID, n);
        vibrate(ctx);
        if (!pre) {
            int voice = Store.adhanVoice(ctx);
            if (voice >= 0) {
                // Audio-only fallback path without FGS (best effort).
                try {
                    MediaPlayer p = new MediaPlayer();
                    p.setAudioStreamType(AudioManager.STREAM_MUSIC);
                    p.setDataSource(Muezzins.urlFor(voice));
                    p.setOnPreparedListener(MediaPlayer::start);
                    p.setOnCompletionListener(mp2 -> mp2.release());
                    p.setOnErrorListener((mp2, w, e2) -> {
                        mp2.release();
                        return true;
                    });
                    p.prepareAsync();
                } catch (Exception ignored) {}
            }
        }
    }

    private static String getString(Context c, int res) {
        return c.getString(res);
    }

    private static String getString(Context c, int res, Object arg) {
        return c.getString(res, arg);
    }

    static void createChannel(Context ctx) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL,
                ctx.getString(R.string.adhan_channel_name), NotificationManager.IMPORTANCE_HIGH);
        ch.setShowBadge(true);
        ch.setBypassDnd(false);
        nm.createNotificationChannel(ch);
    }

    private void release() {
        h.removeCallbacks(autoStop);
        if (mp != null) {
            try {
                mp.reset();
                mp.release();
            } catch (Exception ignored) {}
            mp = null;
        }
    }

    private void stopAndClean() {
        release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        release();
        super.onDestroy();
    }
}
