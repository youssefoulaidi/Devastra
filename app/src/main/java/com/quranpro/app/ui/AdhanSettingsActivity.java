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
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.quranpro.app.R;
import com.quranpro.app.data.PrayerTimes;
import com.quranpro.app.data.Store;
import com.quranpro.app.pray.AdhanScheduler;
import com.quranpro.app.pray.Muezzins;
import com.quranpro.app.util.Ui;

/** Muezzin settings: voice, per-prayer switches, pre-adhan alert, auto-stop. */
public class AdhanSettingsActivity extends AppCompatActivity {

    private TextView tVoice, tPre, tStop;
    private TextView tTestBtn;
    private MediaPlayer test;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_adhan_settings);

        MaterialToolbar tb = findViewById(R.id.toolbar);
        tb.setNavigationIcon(R.drawable.ic_close);
        tb.setNavigationOnClickListener(v -> finish());

        tVoice = findViewById(R.id.t_voice);
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
        tVoice.setText(Muezzins.voiceLabel(Store.adhanVoice(this)));

        findViewById(R.id.row_pre).setOnClickListener(v -> pickPre());
        tPre.setText(preLabel(Store.adhanPreMin(this)));

        findViewById(R.id.row_stop).setOnClickListener(v -> pickStop());
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

        findViewById(R.id.btn_stop_test).setVisibility(View.GONE);
        findViewById(R.id.btn_stop_test).setOnClickListener(v -> stopTest());

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

    // ---------- pickers ----------

    private void pickVoice() {
        Muezzins.Voice[] vs = Muezzins.all();
        final String[] names = new String[vs.length + 1];
        names[0] = getString(R.string.adhan_notifications_only);
        for (int i = 0; i < vs.length; i++) names[i + 1] = vs[i].ar;
        new AlertDialog.Builder(this)
                .setTitle(R.string.adhan_muezzin)
                .setItems(names, (d, w) -> {
                    Store.setAdhanVoice(this, w - 1);
                    tVoice.setText(Muezzins.voiceLabel(w - 1));
                    stopTest();
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

    // ---------- test player ----------

    private void toggleTest() {
        if (test != null) {
            stopTest();
            return;
        }
        int voice = Store.adhanVoice(this);
        if (voice < 0) {
            Ui.toast(this, R.string.adhan_notifications_only);
            com.quranpro.app.pray.AdhanService.vibrate(this);
            return;
        }
        try {
            test = new MediaPlayer();
            test.setAudioStreamType(AudioManager.STREAM_MUSIC);
            test.setDataSource(Muezzins.urlFor(voice));
            test.setOnPreparedListener(MediaPlayer::start);
            test.setOnCompletionListener(mp -> stopTest());
            test.setOnErrorListener((mp, w, e) -> {
                String fb = Muezzins.fallbackFor(voice);
                if (fb != null && !triedFallback) {
                    triedFallback = true;
                    mp.reset();
                    try {
                        mp.setDataSource(fb);
                        mp.prepareAsync();
                    } catch (Exception ignored) {}
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

    private boolean triedFallback;

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
