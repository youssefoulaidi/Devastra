package com.quranpro.app.ui;

import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

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
import com.quranpro.app.util.Ui;

/** Muezzin settings: voice, per-prayer switches, pre-adhan alert, auto-stop. */
public class AdhanSettingsActivity extends BaseActivity {

    private TextView tVoice, tFajrVoice, tOffline, tPre, tStop, tTestBtn;
    private MediaPlayer test;
    private boolean triedFallback;

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
        if (main < 0) {
            tOffline.setText(R.string.adhan_notifications_only);
        } else if (Muezzins.isBundled(this, main)) {
            tOffline.setText(R.string.adhan_offline_bundled);
        } else {
            tOffline.setText(R.string.adhan_offline_download);
        }
    }

    private String voiceLabel(int idx) {
        if (idx < 0) return getString(R.string.adhan_notifications_only);
        return Muezzins.voiceLabel(idx) + (Muezzins.isBundled(this, idx) ? " ✓" : "");
    }

    private String voiceLabelForDialog(int idx) {
        return voiceLabel(idx);
    }

    private void pickVoice() {
        Muezzins.Voice[] vs = Muezzins.all();
        final String[] names = new String[vs.length + 1];
        names[0] = getString(R.string.adhan_notifications_only);
        for (int i = 0; i < vs.length; i++) names[i + 1] = voiceLabelForDialog(i);
        int checked = Math.max(0, Math.min(names.length - 1, Store.adhanVoice(this) + 1));
        new AlertDialog.Builder(this)
                .setTitle(R.string.adhan_muezzin)
                .setSingleChoiceItems(names, checked, (d, w) -> {
                    int voice = w - 1;
                    Store.setAdhanVoice(this, voice);
                    if (voice >= 0) AdhanCache.start(this, voice);
                    refreshVoiceViews();
                    stopTest();
                    d.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void pickFajrVoice() {
        Muezzins.Voice[] vs = Muezzins.all();
        final String[] names = new String[vs.length + 2];
        names[0] = getString(R.string.adhan_inherit_main_voice, voiceLabel(Store.adhanVoice(this)));
        names[1] = getString(R.string.adhan_notifications_only);
        for (int i = 0; i < vs.length; i++) names[i + 2] = voiceLabelForDialog(i);
        int cur = Store.adhanFajrVoice(this);
        int checked = cur == -2 ? 0 : (cur == -1 ? 1 : cur + 2);
        if (checked < 0 || checked >= names.length) checked = 0;
        new AlertDialog.Builder(this)
                .setTitle(R.string.adhan_fajr_voice)
                .setSingleChoiceItems(names, checked, (d, w) -> {
                    int voice = (w == 0) ? -2 : (w == 1 ? -1 : w - 2);
                    Store.setAdhanFajrVoice(this, voice);
                    if (voice >= 0) AdhanCache.start(this, voice);
                    refreshVoiceViews();
                    d.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

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
        AdhanCache.start(this, voice);
        triedFallback = false;
        try {
            test = new MediaPlayer();
            test.setAudioStreamType(AudioManager.STREAM_MUSIC);
            setTestSource(AdhanCache.playSource(this, voice));
            test.setOnPreparedListener(MediaPlayer::start);
            test.setOnCompletionListener(mp -> stopTest());
            test.setOnErrorListener((mp, w, e) -> {
                String fb = Muezzins.fallbackFor(voice);
                if (fb != null && !triedFallback) {
                    triedFallback = true;
                    mp.reset();
                    try {
                        setTestSource(fb);
                        mp.prepareAsync();
                    } catch (Exception ignored) {
                        Ui.toast(this, R.string.error_network);
                        stopTest();
                    }
                } else {
                    Ui.toast(this, R.string.error_network);
                    stopTest();
                }
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
        if (src.startsWith("file:///android_asset/") || src.startsWith("file://")) {
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
    protected void onPause() {
        super.onPause();
        AdhanScheduler.rescheduleAll(this);
    }

    @Override
    protected void onDestroy() {
        stopTest();
        super.onDestroy();
    }
}
