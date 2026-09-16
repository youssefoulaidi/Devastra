package com.quranpro.app.ui;

import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.quranpro.app.R;
import com.quranpro.app.data.PrayerTimes;
import com.quranpro.app.data.Store;
import com.quranpro.app.pray.AdhanCache;
import com.quranpro.app.pray.AdhanScheduler;
import com.quranpro.app.pray.AdhanService;
import com.quranpro.app.pray.Muezzins;
import com.quranpro.app.util.Net;
import com.quranpro.app.util.Ui;

import java.util.ArrayList;
import java.util.List;

/** Muezzin settings: voice, offline downloads, per-prayer switches, pre-adhan alert. */
public class AdhanSettingsActivity extends BaseActivity {

    private TextView tVoice, tFajrVoice, tOffline, tPre, tStop, tTestBtn;
    private MediaPlayer test;
    private boolean triedFallback;

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable refreshTicker = new Runnable() {
        @Override public void run() {
            refreshVoiceViews();
            if (anyDownloading()) h.postDelayed(this, 900);
        }
    };

    private boolean anyDownloading() {
        for (int i = 0; i < Muezzins.count(); i++) {
            if (AdhanCache.isDownloading(i)) return true;
        }
        return false;
    }

    private void restartTicker() {
        h.removeCallbacks(refreshTicker);
        h.post(refreshTicker);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_adhan_settings);

        MaterialToolbar tb = findViewById(R.id.toolbar);
        tb.setNavigationIcon(R.drawable.ic_close);
        tb.setNavigationOnClickListener(v -> finish());

        tVoice = findViewById(R.id.t_voice);
        tFajrVoice = findViewById(R.id.t_fajr_voice);
        tOffline = findViewById(R.id.t_offline);
        tPre = findViewById(R.id.t_pre);
        tStop = findViewById(R.id.t_stop);
        tTestBtn = findViewById(R.id.btn_test);

        final SwitchMaterial master = findViewById(R.id.sw_master);
        master.setChecked(Store.adhanMaster(this));
        master.setOnCheckedChangeListener((b, v) -> {
            Store.setAdhanMaster(this, v);
            AdhanScheduler.rescheduleAll(this);
            findViewById(R.id.details).setVisibility(v ? View.VISIBLE : View.GONE);
        });
        findViewById(R.id.details).setVisibility(master.isChecked() ? View.VISIBLE : View.GONE);

        findViewById(R.id.row_voice).setOnClickListener(v -> pickVoice());
        findViewById(R.id.row_fajr_voice).setOnClickListener(v -> pickFajrVoice());
        findViewById(R.id.row_pre).setOnClickListener(v -> pickPre());
        findViewById(R.id.row_stop).setOnClickListener(v -> pickStop());
        findViewById(R.id.btn_test).setOnClickListener(v -> toggleTest());
        findViewById(R.id.btn_offline_all).setOnClickListener(v -> downloadAll());

        refreshVoiceViews();
        tPre.setText(preLabel(Store.adhanPreMin(this)));
        tStop.setText(stopLabel(Store.adhanStopMin(this)));

        SwitchMaterial vib = findViewById(R.id.sw_vibrate);
        vib.setChecked(Store.adhanVibrate(this));
        vib.setOnCheckedChangeListener((b, v) -> Store.setAdhanVibrate(this, v));

        int[][] rows = {
                {R.id.sw_fajr, PrayerTimes.FAJR},
                {R.id.sw_dhuhr, PrayerTimes.DHUHR},
                {R.id.sw_asr, PrayerTimes.ASR},
                {R.id.sw_maghrib, PrayerTimes.MAGHRIB},
                {R.id.sw_isha, PrayerTimes.ISHA},
        };
        for (final int[] r : rows) {
            SwitchMaterial s = findViewById(r[0]);
            final String key = AdhanScheduler.keyFor(r[1]);
            s.setText(AdhanScheduler.nameRes(r[1]));
            s.setChecked(Store.adhanOn(this, key));
            s.setOnCheckedChangeListener((b, v) -> {
                Store.setAdhanOn(this, key, v);
                AdhanScheduler.rescheduleAll(this);
            });
        }

        final View warn = findViewById(R.id.warn_exact);
        if (AdhanScheduler.exactAllowed(this)) {
            warn.setVisibility(View.GONE);
        } else {
            warn.setVisibility(View.VISIBLE);
            warn.setOnClickListener(v -> {
                if (Build.VERSION.SDK_INT >= 31) {
                    try {
                        startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .setData(Uri.parse("package:" + getPackageName())));
                    } catch (Exception e) {
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.parse("package:" + getPackageName())));
                    }
                }
            });
        }

        // keep the offline badges / progress up to date while downloads run
        AdhanCache.migrate(this);
        restartTicker();
    }

    private void refreshVoiceViews() {
        int main = Store.adhanVoice(this);
        tVoice.setText(voiceLabel(main));
        int fajr = Store.adhanFajrVoice(this);
        if (fajr == -2) {
            tFajrVoice.setText(getString(R.string.adhan_inherit_main_voice, voiceLabel(main)));
        } else {
            tFajrVoice.setText(voiceLabel(fajr));
        }

        TextView all = findViewById(R.id.btn_offline_all);
        if (all != null) {
            int ready = AdhanCache.offlineCount(this);
            int total = Muezzins.count();
            boolean busy = false;
            for (int i = 0; i < total; i++) {
                if (AdhanCache.isDownloading(i)) {
                    busy = true;
                    break;
                }
            }
            all.setEnabled(!busy && ready < total);
            all.setText(getString(R.string.adhan_dl_all, Ui.digits(ready), Ui.digits(total)));
        }
        tOffline.setText(offlineStateText(main));
    }

    private String offlineStateText(int idx) {
        if (idx < 0) return getString(R.string.adhan_notifications_only);
        int p = AdhanCache.progress(idx);
        if (AdhanCache.isDownloading(idx) && p >= 0) {
            return getString(R.string.adhan_downloading_pct, Ui.digits(p));
        }
        if (AdhanCache.isDownloading(idx)) return getString(R.string.adhan_downloading);
        if (AdhanCache.isOfflineReady(this, idx)) {
            return getString(R.string.adhan_offline_bundled);
        }
        return getString(R.string.adhan_offline_download);
    }

    private String voiceLabel(int idx) {
        if (idx < 0) return getString(R.string.adhan_notifications_only);
        String mark = AdhanCache.isOfflineReady(this, idx) ? " ✓" : "";
        return Muezzins.voiceLabel(idx) + mark;
    }

    // ---------- voice pickers ----------

    private void pickVoice() {
        showVoiceDialog(R.string.adhan_muezzin, Store.adhanVoice(this), true, voice -> {
            Store.setAdhanVoice(this, voice);
            afterVoiceChange(voice);
        });
    }

    private void pickFajrVoice() {
        showVoiceDialog(R.string.adhan_fajr_voice, Store.adhanFajrVoice(this), false, voice -> {
            Store.setAdhanFajrVoice(this, voice);
            afterVoiceChange(voice);
        });
    }

    /** -2 = inherit main voice, -1 = silent, i >= 0 = muezzin index. */
    private void showVoiceDialog(int titleRes, int current, boolean mainList,
                                 VoiceChosen onChosen) {
        Muezzins.Voice[] vs = Muezzins.all();
        final List<Integer> values = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        if (!mainList) {
            values.add(-2);
            labels.add(getString(R.string.adhan_inherit_main_voice,
                    Muezzins.voiceLabel(Store.adhanVoice(this))));
        }
        values.add(-1);
        labels.add(getString(R.string.adhan_notifications_only));
        for (int i = 0; i < vs.length; i++) {
            values.add(i);
            labels.add(vs[i].ar + (AdhanCache.isOfflineReady(this, i) ? "  ✓" : "  ⤓"));
        }
        int checked = Math.max(0, values.indexOf(current));

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_single_choice, labels) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                if (v instanceof TextView) {
                    TextView tv = (TextView) v;
                    boolean offline = position < labels.size()
                            && position < values.size()
                            && AdhanCache.isOfflineReady(AdhanSettingsActivity.this, values.get(position));
                    tv.setTextColor(offline
                            ? androidx.core.content.ContextCompat.getColor(
                                    AdhanSettingsActivity.this, R.color.gold)
                            : tv.getCurrentTextColor());
                }
                return v;
            }
        };

        new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setSingleChoiceItems(adapter, checked, (d, w) -> {
                    onChosen.on(values.get(Math.max(0, Math.min(values.size() - 1, w))));
                    d.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private interface VoiceChosen {
        void on(int voice);
    }

    private void afterVoiceChange(int voice) {
        if (voice >= 0 && !AdhanCache.isOfflineReady(this, voice)) {
            AdhanCache.ensure(this, voice);
            Ui.toast(this, getString(R.string.adhan_downloading_voice,
                    Muezzins.voiceLabel(voice)));
        }
        refreshVoiceViews();
        restartTicker();
        stopTest();
    }

    private void downloadAll() {
        AdhanCache.ensureAll(this);
        Ui.toast(this, R.string.adhan_downloading);
        refreshVoiceViews();
        restartTicker();
    }

    // ---------- other rows ----------

    private void pickPre() {
        final int[] opts = {0, 5, 10, 15, 20};
        String[] names = new String[opts.length];
        for (int i = 0; i < opts.length; i++) names[i] = preLabel(opts[i]);
        new AlertDialog.Builder(this)
                .setTitle(R.string.adhan_pre)
                .setItems(names, (d, w) -> {
                    Store.setAdhanPreMin(this, opts[w]);
                    tPre.setText(preLabel(opts[w]));
                    AdhanScheduler.rescheduleAll(this);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void pickStop() {
        final int[] opts = {1, 2, 3, 5, 10};
        String[] names = new String[opts.length];
        for (int i = 0; i < opts.length; i++) names[i] = stopLabel(opts[i]);
        new AlertDialog.Builder(this)
                .setTitle(R.string.adhan_autostop)
                .setItems(names, (d, w) -> {
                    Store.setAdhanStopMin(this, opts[w]);
                    tStop.setText(stopLabel(opts[w]));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String preLabel(int m) {
        return m <= 0 ? getString(R.string.adhan_pre_off)
                : getString(R.string.adhan_minutes, Ui.digits("" + m));
    }

    private String stopLabel(int m) {
        return getString(R.string.adhan_minutes, Ui.digits("" + m));
    }

    // ---------- preview (now works offline + online + streaming fallback) ----------

    private void toggleTest() {
        if (test != null) {
            stopTest();
            return;
        }
        int voice = Store.adhanVoice(this);
        if (voice < 0) {
            Ui.toast(this, R.string.adhan_notifications_only);
            AdhanService.vibrate(this);
            return;
        }
        AdhanCache.migrate(this);
        String offline = AdhanCache.offlineSource(this, voice);
        String toPlay = offline;
        boolean isOfflineSource = true;

        if (toPlay == null) {
            if (Net.online(this)) {
                // Allow immediate streaming test even before download finishes
                toPlay = AdhanCache.streamSource(voice);
                isOfflineSource = false;
                AdhanCache.ensure(this, voice);
                Ui.toast(this, getString(R.string.adhan_downloading_voice,
                        Muezzins.voiceLabel(voice)));
            } else {
                AdhanCache.ensure(this, voice);
                Ui.toast(this, getString(R.string.adhan_downloading_voice,
                        Muezzins.voiceLabel(voice)));
                refreshVoiceViews();
                restartTicker();
                return;
            }
        }

        if (toPlay == null) {
            Ui.toast(this, R.string.error_network);
            return;
        }

        triedFallback = false;
        final boolean wasOffline = isOfflineSource;
        try {
            test = new MediaPlayer();
            test.setAudioStreamType(AudioManager.STREAM_MUSIC);
            setTestSource(toPlay);
            test.setOnPreparedListener(MediaPlayer::start);
            test.setOnCompletionListener(mp -> stopTest());
            test.setOnErrorListener((mp, w, e) -> {
                // First try fallback URL, then try offline if we were streaming
                String fb = Muezzins.fallbackFor(voice);
                if (fb != null && !triedFallback) {
                    triedFallback = true;
                    mp.reset();
                    try {
                        setTestSource(fb);
                        mp.prepareAsync();
                        return true;
                    } catch (Exception ignored) {}
                }
                if (!wasOffline) {
                    // streaming failed → try offline if now available
                    String off = AdhanCache.offlineSource(AdhanSettingsActivity.this, voice);
                    if (off != null) {
                        mp.reset();
                        try {
                            setTestSource(off);
                            mp.prepareAsync();
                            return true;
                        } catch (Exception ignored) {}
                    }
                }
                Ui.toast(AdhanSettingsActivity.this,
                        Net.online(AdhanSettingsActivity.this) ? R.string.error_network : R.string.error_generic);
                stopTest();
                return true;
            });
            test.prepareAsync();
            tTestBtn.setText(R.string.adhan_stop_now);
        } catch (Exception e) {
            Ui.toast(this, R.string.error_generic);
            stopTest();
        }
    }

    private void setTestSource(String src) throws Exception {
        if (test == null) return;
        if (src.startsWith("file:///android_asset/") || src.startsWith("file://")
                || src.startsWith("content://")) {
            test.setDataSource(this, Uri.parse(src));
        } else {
            test.setDataSource(src);
        }
    }

    private void stopTest() {
        triedFallback = false;
        tTestBtn.setText(R.string.adhan_test);
        if (test != null) {
            try {
                test.reset();
                test.release();
            } catch (Exception ignored) {}
            test = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshVoiceViews();
        restartTicker();
    }

    @Override
    protected void onPause() {
        super.onPause();
        AdhanScheduler.rescheduleAll(this);
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacks(refreshTicker);
        stopTest();
        super.onDestroy();
    }
}
