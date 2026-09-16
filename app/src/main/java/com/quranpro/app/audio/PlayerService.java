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
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.DefaultMediaNotificationProvider;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import com.quranpro.app.App;
import com.quranpro.app.R;
import com.quranpro.app.ui.PlayerActivity;
import com.quranpro.app.util.AudioCheck;
import com.quranpro.app.util.DownloadHelper;
import com.quranpro.app.util.Net;
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
    /** Media ids that already failed once — used to avoid switching back and forth. */
    private final List<String> failedLocally = new ArrayList<>();
    private final List<String> failedRemote = new ArrayList<>();

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
                .setUserAgent("QuranPro/1.5 (Android)")
                .setConnectTimeoutMs(20000)
                .setReadTimeoutMs(30000)
                .setAllowCrossProtocolRedirects(true);

        // IMPORTANT: a raw HTTP data source cannot open file:// URIs — that is why
        // downloaded surahs failed to play offline. DefaultDataSource resolves file://,
        // content://, asset:// and raw resources locally and only falls back to the
        // network for http(s) URLs.
        DefaultDataSource.Factory dataSource =
                new DefaultDataSource.Factory(this, http);

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSource))
                .setAudioAttributes(AudioAttributes.DEFAULT, true)
                .setHandleAudioBecomingNoisy(true)
                .build();
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                if (recoverCurrentItem(error)) return;

                // If an offline file exists, never show \"no internet\" — the user explicitly downloaded it.
                int idx = player == null ? -1 : player.getCurrentMediaItemIndex();
                String offlinePath = null;
                if (idx >= 0 && idx < queue.size()) {
                    Track tr = queue.get(idx);
                    if (tr != null) {
                        if (tr.filePath != null) {
                            File fp = new File(tr.filePath);
                            if (fp.exists() && fp.length() > 1024 && AudioCheck.looksLikeAudio(fp)) {
                                offlinePath = fp.getAbsolutePath();
                            }
                        }
                        if (offlinePath == null && tr.kind == Track.KIND_SURAH) {
                            offlinePath = DownloadHelper.offlinePath(getApplicationContext(), tr.server, tr.surahId);
                            if (offlinePath == null) {
                                offlinePath = DownloadHelper.localPath(getApplicationContext(),
                                        reciterId(tr.server), tr.server, tr.surahId);
                            }
                        }
                        // One last forced attempt to play offline if we haven't tried it yet
                        if (offlinePath != null && !failedLocally.contains(tr.key)) {
                            try {
                                File f = new File(offlinePath);
                                if (f.exists() && AudioCheck.looksLikeAudio(f)) {
                                    failedLocally.add(tr.key);
                                    Uri uri = Uri.fromFile(f);
                                    player.replaceMediaItem(idx, buildItem(tr, uri));
                                    player.seekTo(idx, Math.max(0, player.getCurrentPosition()));
                                    player.prepare();
                                    player.play();
                                    return;
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }

                boolean online = Net.online(getApplicationContext());
                if (offlinePath != null && !online) {
                    // Offline file exists but still failed — show generic error, not network
                    App.post(() -> Ui.toast(getApplicationContext(), R.string.error_generic));
                } else {
                    App.post(() -> Ui.toast(getApplicationContext(),
                            online ? R.string.error_network : R.string.error_generic));
                }

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

    /**
     * When a source fails, transparently try the other one: a broken local copy should
     * fall back to streaming and a failed stream should use the downloaded file when the
     * surah is available offline (playback then keeps working without internet).
     */
    private boolean recoverCurrentItem(PlaybackException error) {
        int index = player == null ? -1 : player.getCurrentMediaItemIndex();
        if (index < 0 || index >= queue.size()) return false;
        Track t = queue.get(index);
        if (t == null || t.kind != Track.KIND_SURAH) return false;

        String local = DownloadHelper.localPath(getApplicationContext(), reciterId(t.server),
                t.server, t.surahId);
        MediaItem currentItem = player.getCurrentMediaItem();
        Uri current = (currentItem == null || currentItem.localConfiguration == null)
                ? null : currentItem.localConfiguration.uri;

        boolean isLocal = current != null && "file".equals(current.getScheme());
        long position = Math.max(0, player.getCurrentPosition());

        Uri next = null;
        if (isLocal) {
            if (!failedRemote.contains(t.key)) {
                next = Uri.parse(t.url);
                failedRemote.add(t.key);
            }
        } else if (local != null && !failedLocally.contains(t.key)) {
            next = Uri.fromFile(new File(local));
            failedLocally.add(t.key);
        }
        if (next == null) return false;

        player.replaceMediaItem(index, buildItem(t, next));
        player.seekTo(index, position);
        player.prepare();
        player.play();
        return true;
    }

    private static int reciterId(String server) {
        return server == null ? 0 : Math.abs(server.hashCode());
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
        failedLocally.clear();
        failedRemote.clear();
        List<MediaItem> items = new ArrayList<>(tracks.size());
        for (Track t : tracks) {
            items.add(buildItem(t, null));
        }
        if (index < 0) index = 0;
        if (index >= items.size()) index = 0;
        player.setMediaItems(items, index, C.TIME_UNSET);
        player.prepare();
        player.play();
    }

    /** Builds a media item; when {@code override} is null the offline copy is preferred. */
    private MediaItem buildItem(Track t, @Nullable Uri override) {
        Uri uri = override;
        if (uri == null) {
            if (t.filePath != null) {
                File f = new File(t.filePath);
                if (f.exists() && f.length() > 1024 && AudioCheck.looksLikeAudio(f)) {
                    uri = Uri.fromFile(f);
                }
            }
            if (uri == null) {
                String local = t.kind == Track.KIND_SURAH
                        ? DownloadHelper.offlinePath(getApplicationContext(), t.server, t.surahId)
                        : null;
                if (local == null && t.kind == Track.KIND_SURAH) {
                    local = DownloadHelper.localPath(getApplicationContext(),
                            reciterId(t.server), t.server, t.surahId);
                }
                if (local != null) {
                    File lf = new File(local);
                    if (lf.exists() && lf.length() > 1024 && AudioCheck.looksLikeAudio(lf)) {
                        uri = Uri.fromFile(lf);
                    }
                }
                if (uri == null) {
                    uri = Uri.parse(t.url);
                }
            }
        }
        MediaMetadata md = new MediaMetadata.Builder()
                .setTitle(t.title == null ? "" : t.title)
                .setArtist(t.sub == null ? "" : t.sub)
                .setAlbumTitle(getString(R.string.app_short))
                .build();
        return new MediaItem.Builder()
                .setUri(uri)
                .setMediaId(t.key == null ? uri.toString() : t.key)
                .setMediaMetadata(md)
                .build();
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
