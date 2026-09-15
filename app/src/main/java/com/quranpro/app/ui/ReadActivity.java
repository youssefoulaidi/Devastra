package com.quranpro.app.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.media3.session.MediaController;

import com.google.android.material.appbar.MaterialToolbar;
import com.quranpro.app.App;
import com.quranpro.app.R;
import com.quranpro.app.audio.PlayerManager;
import com.quranpro.app.audio.PlayerService;
import com.quranpro.app.audio.Track;
import com.quranpro.app.data.Api;
import com.quranpro.app.data.Models;
import com.quranpro.app.data.QuranMeta;
import com.quranpro.app.data.Store;
import com.quranpro.app.util.Ui;

import java.util.List;

/** Mushaf text (uthmani) with optional ayah-by-ayah follow while listening. */
public class ReadActivity extends BaseActivity {

    public static void open(Context c, int surahId) {
        Intent i = new Intent(c, ReadActivity.class);
        i.putExtra("surah", surahId);
        c.startActivity(i);
    }

    private int surahId;
    private TextView tText, tBismillah, tMeta, tReciter;
    private ScrollView scroll;
    private ProgressBar progress;
    private View error;
    private Button btnFollow;
    private ImageButton btnPlay;
    private MediaController controller;

    private List<Models.Ayah> ayahs;
    private List<Models.Timing> timings;
    private boolean followOn;
    private boolean timingLoading;
    private int currentAyah = -1;
    private int[] starts, ends;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updatePlayIcon();
            if (followOn) updateFollow();
            App.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_read);
        surahId = getIntent().getIntExtra("surah", 1);

        Models.Surah s = QuranMeta.byId(surahId);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(s == null ? "" : getString(R.string.read_title, s.ar));
        toolbar.setNavigationIcon(R.drawable.ic_close);
        toolbar.setNavigationOnClickListener(v -> finish());

        tText = findViewById(R.id.t_text);
        tBismillah = findViewById(R.id.t_bismillah);
        tMeta = findViewById(R.id.t_meta);
        tReciter = findViewById(R.id.t_reciter);
        scroll = findViewById(R.id.scroll);
        progress = findViewById(R.id.progress);
        error = findViewById(R.id.error);
        btnFollow = findViewById(R.id.btn_follow);
        btnPlay = findViewById(R.id.btn_play);

        try {
            Typeface tf = Typeface.createFromAsset(getAssets(), "fonts/AmiriQuran-Regular.ttf");
            tText.setTypeface(tf);
            tBismillah.setTypeface(tf);
        } catch (Exception ignored) {}
        float size = Store.textSize(this);
        tText.setTextSize(size);
        tBismillah.setTextSize(size + 2);

        tBismillah.setVisibility(surahId == 1 || surahId == 9 ? View.GONE : View.VISIBLE);

        Store.Current cur = Store.getCurrent(this);
        tReciter.setText(cur == null ? "" : cur.reciterName);

        btnPlay.setOnClickListener(v -> togglePlay());
        btnFollow.setOnClickListener(v -> toggleFollow());
        findViewById(R.id.btn_retry).setOnClickListener(v -> load());

        load();
    }

    @Override
    protected void onStart() {
        super.onStart();
        PlayerManager.controller(this, c -> controller = c);
        App.post(ticker);
    }

    @Override
    protected void onStop() {
        App.removeCallbacks(ticker);
        PlayerManager.release(controller);
        controller = null;
        super.onStop();
    }

    private void load() {
        progress.setVisibility(View.VISIBLE);
        error.setVisibility(View.GONE);
        Api.fetchSurahText(this, surahId, new Api.Cb<List<Models.Ayah>>() {
            @Override public void ok(List<Models.Ayah> v) {
                progress.setVisibility(View.GONE);
                ayahs = v;
                render();
            }
            @Override public void err(String m) {
                progress.setVisibility(View.GONE);
                error.setVisibility(View.VISIBLE);
            }
        });
    }

    private void render() {
        if (ayahs == null || ayahs.isEmpty()) return;
        Models.Surah s = QuranMeta.byId(surahId);
        String type = (s != null && !s.makki) ? getString(R.string.madania) : getString(R.string.makkia);
        tMeta.setText(getString(R.string.surah_meta, Ui.digits(ayahs.size()), type)
                + " • " + getString(R.string.juz, Ui.digits(ayahs.get(0).juz)));

        SpannableStringBuilder sb = new SpannableStringBuilder();
        starts = new int[ayahs.size()];
        ends = new int[ayahs.size()];
        for (int i = 0; i < ayahs.size(); i++) {
            Models.Ayah a = ayahs.get(i);
            starts[i] = sb.length();
            sb.append(a.text).append(' ').append(Ui.ayahEnd(a.num));
            ends[i] = sb.length();
            sb.append("\n\n");
        }
        tText.setText(sb);
    }

    private void togglePlay() {
        Track t = PlayerService.getInstance() == null ? null
                : PlayerService.getInstance().currentTrack();
        if (t != null && t.kind == Track.KIND_SURAH && t.surahId == surahId
                && controller != null) {
            if (controller.isPlaying()) controller.pause();
            else controller.play();
            return;
        }
        Models.Moshaf m = Store.currentMoshaf(this);
        Store.Current cur = Store.getCurrent(this);
        if (m == null || cur == null) return;
        if (!m.hasSurah(surahId)) {
            Ui.toast(this, R.string.surah_not_in_moshaf);
            return;
        }
        PlayerManager.playSurah(this, m, cur.reciterName, surahId);
        if (controller == null) {
            PlayerManager.controller(this, c -> controller = c);
        }
    }

    private void updatePlayIcon() {
        Track t = PlayerService.getInstance() == null ? null
                : PlayerService.getInstance().currentTrack();
        boolean playing = controller != null && controller.isPlaying()
                && t != null && t.kind == Track.KIND_SURAH && t.surahId == surahId;
        btnPlay.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    private void toggleFollow() {
        if (followOn) {
            followOn = false;
            currentAyah = -1;
            highlight(-1);
            btnFollow.setText(R.string.follow_ayat);
            Ui.toast(this, R.string.follow_off);
            return;
        }
        Track t = PlayerService.getInstance() == null ? null
                : PlayerService.getInstance().currentTrack();
        if (t == null || t.kind != Track.KIND_SURAH || t.surahId != surahId) {
            Ui.toast(this, R.string.timing_unavailable);
            return;
        }
        if (timings != null) {
            followOn = true;
            btnFollow.setText(R.string.follow_on);
            return;
        }
        if (timingLoading) return;
        timingLoading = true;
        Ui.toast(this, R.string.loading);
        final String server = t.server;
        Api.fetchTimingReads(this, new Api.Cb<List<Models.TimingRead>>() {
            @Override public void ok(List<Models.TimingRead> reads) {
                int readId = 0;
                String norm = Api.normServer(server);
                for (Models.TimingRead r : reads) {
                    if (norm.equals(Api.normServer(r.folder))) {
                        readId = r.id;
                        break;
                    }
                }
                if (readId == 0) {
                    timingLoading = false;
                    Ui.toast(ReadActivity.this, R.string.timing_unavailable);
                    return;
                }
                Api.fetchTiming(ReadActivity.this, surahId, readId,
                        new Api.Cb<List<Models.Timing>>() {
                    @Override public void ok(List<Models.Timing> v) {
                        timingLoading = false;
                        timings = v;
                        followOn = true;
                        btnFollow.setText(R.string.follow_on);
                        Ui.toast(ReadActivity.this, R.string.follow_on);
                    }
                    @Override public void err(String m) {
                        timingLoading = false;
                        Ui.toast(ReadActivity.this, R.string.timing_unavailable);
                    }
                });
            }
            @Override public void err(String m) {
                timingLoading = false;
                Ui.toast(ReadActivity.this, R.string.timing_unavailable);
            }
        });
    }

    private void updateFollow() {
        if (controller == null || timings == null || ayahs == null) return;
        Track t = PlayerService.getInstance() == null ? null
                : PlayerService.getInstance().currentTrack();
        if (t == null || t.kind != Track.KIND_SURAH || t.surahId != surahId) return;
        long pos = controller.getCurrentPosition();
        int ayah = -1;
        for (Models.Timing tm : timings) {
            if (tm.ayah >= 1 && pos >= tm.start && pos < tm.end) {
                ayah = tm.ayah;
                break;
            }
        }
        if (ayah != currentAyah) {
            currentAyah = ayah;
            highlight(ayah);
            scrollToAyah(ayah);
        }
    }

    private void highlight(int ayah) {
        if (ayahs == null || starts == null) return;
        CharSequence cur = tText.getText();
        SpannableStringBuilder sb;
        if (cur instanceof SpannableStringBuilder) sb = (SpannableStringBuilder) cur;
        else sb = new SpannableStringBuilder(cur);
        BackgroundColorSpan[] spans = sb.getSpans(0, sb.length(), BackgroundColorSpan.class);
        for (BackgroundColorSpan sp : spans) sb.removeSpan(sp);
        if (ayah >= 1 && ayah <= ayahs.size()) {
            sb.setSpan(new BackgroundColorSpan(ContextCompat.getColor(this, R.color.gold_light)),
                    starts[ayah - 1], ends[ayah - 1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        tText.setText(sb);
    }

    private void scrollToAyah(int ayah) {
        if (ayah < 1 || ayahs == null || starts == null || ayah > ayahs.size()) return;
        if (tText.getLayout() == null) return;
        int line = tText.getLayout().getLineForOffset(starts[ayah - 1]);
        int y = tText.getLayout().getLineTop(line);
        scroll.smoothScrollTo(0, Math.max(0, y - 300));
    }
}
