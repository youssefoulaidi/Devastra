package com.quranpro.app.audio;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.DefaultMediaNotificationProvider;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import com.quranpro.app.App;
import com.quranpro.app.R;
import com.quranpro.app.ui.PlayerActivity;
import com.quranpro.app.util.Ui;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Background audio: surahs, radios, tafsir — with notification + headset support. */
public class PlayerService extends MediaSessionService {

    public static final String ACT_PLAY = "com.quranpro.app.PLAY";

    private static PlayerService inst;

    private ExoPlayer player;
    private MediaSession session;
    private final List<Track> queue = new ArrayList<>();

    private final Handler sleepHandler = new Handler(Looper.getMainLooper());
    private Runnable sleepTask;
    private int sleepMinutes;

    public static boolean isRunning() {
        return inst != null;
    }

    public static PlayerService getInstance() {
        return inst;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        inst = this;

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent("QuranPro/1.0 (Android)")
                .setConnectTimeoutMs(20000)
                .setReadTimeoutMs(30000)
                .setAllowCrossProtocolRedirects(true);

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(http))
                .setAudioAttributes(AudioAttributes.DEFAULT, true)
                .setHandleAudioBecomingNoisy(true)
                .build();
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                App.post(() -> Ui.toast(getApplicationContext(), R.string.error_network));
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem();
                    player.prepare();
                    player.play();
                }
            }
        });

        Intent open = new Intent(this, PlayerActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        session = new MediaSession.Builder(this, player)
                .setSessionActivity(pi)
                .build();

        DefaultMediaNotificationProvider notificationProvider =
                new DefaultMediaNotificationProvider.Builder(this)
                        .setChannelName(R.string.notif_channel)
                        .build();
        notificationProvider.setSmallIcon(R.drawable.ic_stat_quran);
        setMediaNotificationProvider(notificationProvider);
    }

    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return session;
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        if (intent != null && ACT_PLAY.equals(intent.getAction())) {
            Serializable s = intent.getSerializableExtra("tracks");
            int index = intent.getIntExtra("index", 0);
            if (s instanceof ArrayList) {
                // noinspection unchecked
                playQueue((ArrayList<Track>) s, index);
            }
        }
        return START_STICKY;
    }

    private void playQueue(List<Track> tracks, int index) {
        if (tracks == null || tracks.isEmpty()) return;
        queue.clear();
        queue.addAll(tracks);
        List<MediaItem> items = new ArrayList<>(tracks.size());
        for (Track t : tracks) {
            Uri uri;
            if (t.filePath != null && new File(t.filePath).exists()) {
                uri = Uri.fromFile(new File(t.filePath));
            } else {
                uri = Uri.parse(t.url);
            }
            MediaMetadata md = new MediaMetadata.Builder()
                    .setTitle(t.title == null ? "" : t.title)
                    .setArtist(t.sub == null ? "" : t.sub)
                    .setAlbumTitle(getString(R.string.app_short))
                    .build();
            items.add(new MediaItem.Builder()
                    .setUri(uri)
                    .setMediaId(t.key == null ? uri.toString() : t.key)
                    .setMediaMetadata(md)
                    .build());
        }
        if (index < 0) index = 0;
        if (index >= items.size()) index = 0;
        player.setMediaItems(items, index, C.TIME_UNSET);
        player.prepare();
        player.play();
    }

    @Nullable
    public Track currentTrack() {
        int i = player == null ? -1 : player.getCurrentMediaItemIndex();
        if (i >= 0 && i < queue.size()) return queue.get(i);
        return null;
    }

    // ---------- sleep timer ----------

    public void setSleepMinutes(int minutes) {
        cancelSleep();
        sleepMinutes = minutes;
        if (minutes > 0) {
            sleepTask = () -> {
                if (player != null) player.pause();
                sleepMinutes = 0;
                sleepTask = null;
            };
            sleepHandler.postDelayed(sleepTask, minutes * 60_000L);
        }
    }

    public void cancelSleep() {
        sleepMinutes = 0;
        if (sleepTask != null) {
            sleepHandler.removeCallbacks(sleepTask);
            sleepTask = null;
        }
    }

    public int getSleepMinutes() {
        return sleepMinutes;
    }

    @Override
    public void onTaskRemoved(@Nullable Intent rootIntent) {
        if (player != null && !player.isPlaying()) stopSelf();
    }

    @Override
    public void onDestroy() {
        cancelSleep();
        if (session != null) {
            session.release();
            session = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        queue.clear();
        inst = null;
        super.onDestroy();
    }
}
