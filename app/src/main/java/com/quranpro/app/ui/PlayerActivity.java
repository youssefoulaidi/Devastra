package com.quranpro.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.session.MediaController;

import com.google.android.material.appbar.MaterialToolbar;
import com.quranpro.app.App;
import com.quranpro.app.R;
import com.quranpro.app.audio.PlayerManager;
import com.quranpro.app.audio.PlayerService;
import com.quranpro.app.audio.Track;
import com.quranpro.app.data.Models;
import com.quranpro.app.data.QuranMeta;
import com.quranpro.app.data.Store;
import com.quranpro.app.util.DownloadHelper;
import com.quranpro.app.util.Ui;

/** Full-screen audio player. */
public class PlayerActivity extends AppCompatActivity {

    private MediaController controller;
    private TextView title, sub, tCur, tTotal;
    private SeekBar seek;
    private ImageButton btnPlay, btnPrev, btnNext, btnRepeat, btnSpeed,
            btnSleep, btnDownload, btnFav, btnShare;
    private boolean seeking;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refresh();
            App.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        title = findViewById(R.id.title);
        sub = findViewById(R.id.sub);
        tCur = findViewById(R.id.t_cur);
        tTotal = findViewById(R.id.t_total);
        seek = findViewById(R.id.seek);
        btnPlay = findViewById(R.id.btn_play);
        btnPrev = findViewById(R.id.btn_prev);
        btnNext = findViewById(R.id.btn_next);
        btnRepeat = findViewById(R.id.btn_repeat);
        btnSpeed = findViewById(R.id.btn_speed);
        btnSleep = findViewById(R.id.btn_sleep);
        btnDownload = findViewById(R.id.btn_download);
        btnFav = findViewById(R.id.btn_fav);
        btnShare = findViewById(R.id.btn_share);

        btnPlay.setOnClickListener(v -> {
            if (controller == null) return;
            if (controller.isPlaying()) controller.pause();
            else controller.play();
            App.postDelayed(this::refresh, 150);
        });
        btnPrev.setOnClickListener(v -> {
            if (controller != null) controller.seekToPreviousMediaItem();
        });
        btnNext.setOnClickListener(v -> {
            if (controller != null) controller.seekToNextMediaItem();
        });
        btnRepeat.setOnClickListener(v -> {
            if (controller == null) return;
            boolean one = !PlayerManager.isRepeatOne(controller);
            PlayerManager.setRepeatOne(controller, one);
            Ui.toast(this, one ? R.string.repeat_one : R.string.repeat_off);
            refresh();
        });
        btnSpeed.setOnClickListener(v -> showSpeed());
        btnSleep.setOnClickListener(v -> showSleep());
        btnDownload.setOnClickListener(v -> downloadCurrent());
        btnFav.setOnClickListener(v -> favCurrent());
        btnShare.setOnClickListener(v -> shareCurrent());

        findViewById(R.id.btn_read).setOnClickListener(v -> {
            Track t = currentTrack();
            if (t != null && t.kind == Track.KIND_SURAH) ReadActivity.open(this, t.surahId);
        });
        findViewById(R.id.btn_reciter).setOnClickListener(v -> {
            Intent i = new Intent(this, MainActivity.class);
            i.putExtra("tab", MainActivity.TAB_RECITERS);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            finish();
        });

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser && controller != null) {
                    long dur = controller.getDuration();
                    if (dur > 0) tCur.setText(Ui.fmtTime(dur * p / 1000));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {
                seeking = true;
            }
            @Override public void onStopTrackingTouch(SeekBar s) {
                seeking = false;
                if (controller != null) {
                    long dur = controller.getDuration();
                    if (dur > 0) controller.seekTo(dur * s.getProgress() / 1000);
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        PlayerManager.controller(this, c -> {
            controller = c;
            refresh();
        });
        App.post(ticker);
    }

    @Override
    protected void onStop() {
        App.removeCallbacks(ticker);
        PlayerManager.release(controller);
        controller = null;
        super.onStop();
    }

    private Track currentTrack() {
        PlayerService s = PlayerService.getInstance();
        return s == null ? null : s.currentTrack();
    }

    private void refresh() {
        if (controller == null || controller.getMediaItemCount() == 0) return;
        int i = controller.getCurrentMediaItemIndex();
        if (i >= 0 && i < controller.getMediaItemCount()) {
            androidx.media3.common.MediaMetadata md =
                    controller.getMediaItemAt(i).mediaMetadata;
            title.setText(md.title == null ? "" : md.title.toString());
            sub.setText(md.artist == null ? "" : md.artist.toString());
        }
        btnPlay.setImageResource(controller.isPlaying()
                ? R.drawable.ic_pause : R.drawable.ic_play);
        long dur = controller.getDuration();
        long pos = controller.getCurrentPosition();
        if (dur > 0) {
            tTotal.setText(Ui.fmtTime(dur));
            if (!seeking) {
                tCur.setText(Ui.fmtTime(pos));
                seek.setProgress((int) (1000 * pos / dur));
            }
            seek.setEnabled(true);
        } else {
            tTotal.setText("--:--");
            seek.setEnabled(false);
        }
        btnPrev.setEnabled(controller.hasPreviousMediaItem());
        btnNext.setEnabled(controller.hasNextMediaItem());
        boolean one = PlayerManager.isRepeatOne(controller);
        btnRepeat.setImageResource(one ? R.drawable.ic_repeat : R.drawable.ic_repeat);
        btnRepeat.setColorFilter(one ? getColor(R.color.gold) : getColor(R.color.muted));

        PlayerService s = PlayerService.getInstance();
        boolean sleep = s != null && s.getSleepMinutes() > 0;
        btnSleep.setColorFilter(sleep ? getColor(R.color.gold) : getColor(R.color.muted));

        Track t = currentTrack();
        boolean isSurah = t != null && t.kind == Track.KIND_SURAH;
        btnDownload.setEnabled(isSurah);
        btnFav.setEnabled(isSurah);
        if (isSurah && Store.isFav(this, t.key)) {
            btnFav.setImageResource(R.drawable.ic_heart_fill);
            btnFav.setColorFilter(getColor(R.color.live_red));
        } else {
            btnFav.setImageResource(R.drawable.ic_heart);
            btnFav.setColorFilter(getColor(R.color.muted));
        }
    }

    private void showSpeed() {
        if (controller == null) return;
        final float[] speeds = {0.75f, 1f, 1.25f, 1.5f, 2f};
        String[] labels = {"0.75x", "1x", "1.25x", "1.5x", "2x"};
        new AlertDialog.Builder(this)
                .setTitle(R.string.speed)
                .setItems(labels, (d, which) -> controller.setPlaybackSpeed(speeds[which]))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showSleep() {
        final PlayerService s = PlayerService.getInstance();
        if (s == null) return;
        final int[] mins = {0, 5, 10, 15, 30, 60};
        String[] labels = new String[mins.length];
        labels[0] = getString(R.string.sleep_off);
        for (int i = 1; i < mins.length; i++) {
            labels[i] = getString(R.string.sleep_minutes, mins[i])
                    .replace(String.valueOf(mins[i]), Ui.digits(mins[i]));
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.sleep)
                .setItems(labels, (d, which) -> {
                    if (mins[which] == 0) {
                        s.cancelSleep();
                        Ui.toast(this, R.string.sleep_cancelled);
                    } else {
                        s.setSleepMinutes(mins[which]);
                        Ui.toast(this, getString(R.string.sleep_set, mins[which])
                                .replace(String.valueOf(mins[which]), Ui.digits(mins[which])));
                    }
                    refresh();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void downloadCurrent() {
        Track t = currentTrack();
        if (t == null || t.kind != Track.KIND_SURAH) return;
        Models.Surah s = QuranMeta.byId(t.surahId);
        String name = s == null ? t.title : getString(R.string.read_title, s.ar);
        int reciterId = reciterIdFor(t.server);
        String reciter = t.sub == null ? "" : t.sub.split(" • ")[0];
        boolean started = DownloadHelper.enqueue(this, reciterId, reciter,
                t.surahId, name, t.server, t.url);
        Ui.toast(this, started ? R.string.downloading : R.string.downloaded);
    }

    private void favCurrent() {
        Track t = currentTrack();
        if (t == null || t.kind != Track.KIND_SURAH) return;
        boolean was = Store.isFav(this, t.key);
        if (!was) {
            Models.Surah s = QuranMeta.byId(t.surahId);
            Store.Fav f = new Store.Fav();
            f.key = t.key;
            f.surahId = t.surahId;
            f.surahName = s == null ? t.title : s.ar;
            String[] parts = t.sub == null ? new String[0] : t.sub.split(" • ");
            f.reciterName = parts.length > 0 ? parts[0] : "";
            f.moshafName = parts.length > 1 ? parts[1] : "";
            f.server = t.server;
            Store.toggleFav(this, f);
        } else {
            Store.removeFav(this, t.key);
        }
        Ui.toast(this, was ? R.string.fav_removed : R.string.fav_added);
        refresh();
    }

    private void shareCurrent() {
        Track t = currentTrack();
        if (t == null) return;
        Ui.shareText(this, t.title + " - " + t.sub + "\n" + t.url);
    }

    private int reciterIdFor(String server) {
        Store.Current cur = Store.getCurrent(this);
        if (cur != null && server != null && server.equals(cur.server)) return cur.reciterId;
        return server == null ? 0 : Math.abs(server.hashCode());
    }
}
