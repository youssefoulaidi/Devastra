package com.quranpro.app.ui;

import android.os.Bundle;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.quranpro.app.R;
import com.quranpro.app.data.Store;
import com.quranpro.app.util.Ui;

import java.io.File;

/** Theme, text size, autoplay, cache, share, about. */
public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_close);
        toolbar.setNavigationOnClickListener(v -> finish());

        RadioGroup rg = findViewById(R.id.rg_theme);
        int pref = Store.themePref(this);
        if (pref == 1) rg.check(R.id.r_light);
        else if (pref == 2) rg.check(R.id.r_dark);
        else rg.check(R.id.r_system);
        rg.setOnCheckedChangeListener((g, id) -> {
            if (id == R.id.r_light) Store.setThemePref(this, 1);
            else if (id == R.id.r_dark) Store.setThemePref(this, 2);
            else Store.setThemePref(this, 0);
        });

        SeekBar seek = findViewById(R.id.seek_text);
        seek.setProgress((int) (Store.textSize(this) - 14));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) Store.setTextSize(SettingsActivity.this, 14 + p);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        SwitchMaterial autoplay = findViewById(R.id.sw_autoplay);
        autoplay.setChecked(Store.autoplay(this));
        autoplay.setOnCheckedChangeListener((b, checked) -> Store.setAutoplay(this, checked));

        findViewById(R.id.row_cache).setOnClickListener(v -> {
            deleteRecursive(new File(getCacheDir(), "api"));
            deleteRecursive(new File(getCacheDir(), "img"));
            Ui.toast(this, R.string.cache_cleared);
        });
        findViewById(R.id.row_share).setOnClickListener(v ->
                Ui.shareText(this, getString(R.string.share_app_text)));
        findViewById(R.id.row_rate).setOnClickListener(v -> {
            try {
                Ui.openUrl(this, "market://details?id="
                        + getPackageName().replace(".debug", ""));
            } catch (Exception e) {
                Ui.openUrl(this, "https://play.google.com/store/apps/details?id="
                        + getPackageName().replace(".debug", ""));
            }
        });
        findViewById(R.id.row_about).setOnClickListener(v ->
                InfoActivity.open(this, InfoActivity.PAGE_ABOUT));

        String ver = "1.1";
        try {
            ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {}
        ((TextView) findViewById(R.id.t_version)).setText(getString(R.string.version, ver));
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        f.delete();
    }
}
