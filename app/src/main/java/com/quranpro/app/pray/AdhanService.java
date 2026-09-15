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
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.core.app.NotificationCompat;

import com.quranpro.app.R;
import com.quranpro.app.data.PrayerTimes;
import com.quranpro.app.data.Store;
import com.quranpro.app.ui.MainActivity;
import com.quranpro.app.util.Net;

/**
 * Plays the adhan with a foreground notification; silent alerts for pre-adhan.
 *
 * <p>Order of sources: offline copy (bundled asset or downloaded voice) → network
 * stream → system notification tone. The audio therefore never depends on the network
 * once a voice is available offline, and when a voice is streamed it is cached in the
 * background so the next adhan plays without internet.
 */
public class AdhanService extends Service {

    public static final String EXTRA_PRAYER = "prayer";
    public static final String EXTRA_PRE = "pre";
    private static final String ACTION_STOP = "qp.adhan.STOP";
    private static final int NOTIF_ID = 4001;
    public static final String CHANNEL = "adhan";

    private MediaPlayer mp;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable autoStop = this::stopAndClean;
    private int currentPrayer = PrayerTimes.FAJR;

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createChannel(this);
        if ((intent != null && ACTION_STOP.equals(intent.getAction()))
                || !Store.adhanMaster(this)) {
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
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIF_ID, n);
            }
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        vibrate(this);

        if (pre) {
            h.postDelayed(this::stopAndClean, 60_000L);
            return START_STICKY;
        }

        currentPrayer = prayer;
        int voice = resolveVoice(this, prayer);
        if (voice < 0) {
            h.postDelayed(this::stopAndClean, 15_000L);
            return START_STICKY;
        }
        playVoice(voice);
        long stopMs = Math.max(1, Store.adhanStopMin(this)) * 60_000L;
        h.postDelayed(autoStop, stopMs);
        return START_STICKY;
    }

    /** Offline-first playback with graceful degradation. */
    private void playVoice(int voice) {
        migrateCache();
        String offline = AdhanCache.offlineSource(this, voice);
        if (offline != null) {
            play(offline, null, false);
            return;
        }
        // Nothing stored: download it for next time, and stream it now if we can.
        AdhanCache.ensure(this, voice);
        if (Net.online(this)) {
            play(AdhanCache.streamSource(voice), Muezzins.fallbackFor(voice), true);
        } else {
            notifyOfflineMissing(voice);
            playTone();
        }
    }

    private void migrateCache() {
        try {
            AdhanCache.migrate(this);
        } catch (Exception ignored) {}
    }

    /** Nothing stored and no internet: ring the default alert tone so the user hears it. */
    private void playTone() {
        try {
            Uri tone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            if (tone == null) {
                h.postDelayed(this::stopAndClean, 800L);
                return;
            }
            play(tone.toString(), null, false);
        } catch (Exception e) {
            h.postDelayed(this::stopAndClean, 800L);
        }
    }

    private void notifyOfflineMissing(int voice) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        Intent open = new Intent(this, com.quranpro.app.ui.AdhanSettingsActivity.class);
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 913, open, fl);
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_quran)
                .setContentTitle(getString(R.string.app_short))
                .setContentText(getString(R.string.adhan_offline_need_download,
                        Muezzins.voiceLabel(voice)))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM);
        nm.notify(NOTIF_ID + 1, b.build());
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

        Intent settings = new Intent(this, com.quranpro.app.ui.AdhanSettingsActivity.class);
        b.addAction(0, getString(R.string.adhan_title),
                PendingIntent.getActivity(this, 914, settings, fl));
        return b.build();
    }

    private void play(String src, String fallback, boolean streamed) {
        release();
        if (src == null || src.trim().isEmpty()) {
            h.postDelayed(this::stopAndClean, 800L);
            return;
        }
        try {
            mp = new MediaPlayer();
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            try {
                mp.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            } catch (Exception ignored) {}
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                try {
                    am.requestAudioFocus(afListener, AudioManager.STREAM_MUSIC,
                            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
                } catch (Exception ignored) {}
            }
            setSource(this, mp, src);
            final String fb = fallback;
            mp.setOnErrorListener((m, what, extra) -> {
                if (fb != null) {
                    play(fb, null, streamed);
                } else if (streamed) {
                    // streaming failed (network dropped): fall back to whatever we have
                    final int voice = resolveVoice(this, currentPrayer);
                    String offline = AdhanCache.offlineSource(this, voice);
                    if (offline != null) play(offline, null, false);
                    else playTone();
                } else {
                    h.postDelayed(this::stopAndClean, 400L);
                }
                return true;
            });
            mp.setOnCompletionListener(m -> h.postDelayed(this::stopAndClean, 800L));
            mp.setOnPreparedListener(m -> {
                try {
                    m.start();
                } catch (Exception ignored) {}
            });
            mp.prepareAsync();
        } catch (Exception e) {
            if (fallback != null) play(fallback, null, streamed);
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
        Intent open = new Intent(ctx, MainActivity.class);
        open.putExtra("tab", 0);
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(ctx, 912, open, fl);

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_quran)
                .setContentTitle(t)
                .setContentText(pre ? "" : getString(ctx, R.string.adhan_notif_text,
                        getString(ctx, AdhanScheduler.nameRes(prayer))))
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_ALARM);
        Notification n = b.build();
        nm.notify(NOTIF_ID, n);
        vibrate(ctx);
        if (!pre) {
            int voice = resolveVoice(ctx, prayer);
            if (voice >= 0) {
                AdhanCache.migrate(ctx);
                String offline = AdhanCache.offlineSource(ctx, voice);
                if (offline != null) {
                    playOnce(ctx.getApplicationContext(), offline, null);
                } else {
                    AdhanCache.ensure(ctx, voice);
                    if (Net.online(ctx)) {
                        playOnce(ctx.getApplicationContext(),
                                AdhanCache.streamSource(voice), Muezzins.fallbackFor(voice));
                    }
                }
            }
        }
    }

    private static void playOnce(Context ctx, String src, String fallback) {
        if (src == null || src.trim().isEmpty()) return;
        try {
            MediaPlayer p = new MediaPlayer();
            p.setAudioStreamType(AudioManager.STREAM_MUSIC);
            try {
                p.setWakeMode(ctx, PowerManager.PARTIAL_WAKE_LOCK);
            } catch (Exception ignored) {}
            setSource(ctx, p, src);
            p.setOnPreparedListener(MediaPlayer::start);
            p.setOnCompletionListener(MediaPlayer::release);
            p.setOnErrorListener((mp2, w, e2) -> {
                mp2.release();
                if (fallback != null) playOnce(ctx, fallback, null);
                return true;
            });
            p.prepareAsync();
        } catch (Exception ignored) {
            if (fallback != null) playOnce(ctx, fallback, null);
        }
    }

    private static void setSource(Context ctx, MediaPlayer player, String src) throws Exception {
        if (src == null || src.trim().isEmpty()) throw new IllegalArgumentException("empty source");
        if (src.startsWith("file:///android_asset/") || src.startsWith("file://")
                || src.startsWith("content://")) {
            player.setDataSource(ctx, Uri.parse(src));
        } else {
            player.setDataSource(src);
        }
    }

    private static int resolveVoice(Context ctx, int prayer) {
        if (prayer == PrayerTimes.FAJR) {
            int fajr = Store.adhanFajrVoice(ctx);
            if (fajr >= -1) return fajr;
        }
        return Store.adhanVoice(ctx);
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
        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } catch (Exception ignored) {}
        stopSelf();
    }

    @Override
    public void onDestroy() {
        release();
        super.onDestroy();
    }
}
