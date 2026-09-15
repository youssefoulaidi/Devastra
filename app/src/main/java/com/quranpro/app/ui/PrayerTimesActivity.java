package com.quranpro.app.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.quranpro.app.R;
import com.quranpro.app.data.Api;
import com.quranpro.app.data.PrayerCalc;
import com.quranpro.app.data.PrayerTimes;
import com.quranpro.app.data.Store;
import com.quranpro.app.pray.AdhanScheduler;
import com.quranpro.app.util.Ui;

import java.util.Calendar;
import java.util.Locale;

/**
 * مواقيت الصلاة — Aladhan timings for the saved place + method, live countdown
 * to the next salat, entry point to the muezzin (adhan) settings.
 */
public class PrayerTimesActivity extends BaseActivity {

    /** {aladhan method id, Arabic label, English label} */
    private static final Object[][] METHODS = {
            {3, "رابطة العالم الإسلامي", "Muslim World League"},
            {4, "أم القرى — مكة المكرمة", "Umm al-Qura, Makkah"},
            {5, "الهيئة المصرية العامة للمساحة", "Egyptian General Authority"},
            {1, "جامعة العلوم الإسلامية — كراتشي", "Karachi"},
            {2, "الجمعية الإسلامية لأمريكا الشمالية", "ISNA (N. America)"},
            {9, "معهد البحوث الكويتي", "Kuwait"},
            {10, "هيئة قطر للأوقاف", "Qatar"},
            {13, "رئاسة الشؤون الدينية — تركيا", "Diyanet, Turkey"},
            {12, "اتحاد المنظمات الإسلامية في فرنسا", "France"},
            {8, "إقليم الخليج", "Gulf Region"},
            {7, "معهد الجيوفيزياء — طهران", "Tehran"},
    };

    private TextView tDate, tHijri, tNextName, tNextTime, tCountdown, tLoc, tMethod;
    private final TextView[] timeViews = new TextView[6];
    private final View[] rows = new View[6];
    private PrayerTimes pt;
    private View emptyState;

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateCountdown();
            h.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_prayer_times);

        MaterialToolbar tb = findViewById(R.id.toolbar);
        tb.setNavigationIcon(R.drawable.ic_close);
        tb.setNavigationOnClickListener(v -> finish());

        tDate = findViewById(R.id.t_date);
        tHijri = findViewById(R.id.t_hijri);
        tNextName = findViewById(R.id.t_next_name);
        tNextTime = findViewById(R.id.t_next_time);
        tCountdown = findViewById(R.id.t_countdown);
        tLoc = findViewById(R.id.t_location);
        tMethod = findViewById(R.id.t_method);
        emptyState = findViewById(R.id.empty_state);

        int[] ids = {R.id.row_fajr, R.id.row_sunrise, R.id.row_dhuhr,
                R.id.row_asr, R.id.row_maghrib, R.id.row_isha};
        int[] tv = {R.id.t_fajr, R.id.t_sunrise, R.id.t_dhuhr,
                R.id.t_asr, R.id.t_maghrib, R.id.t_isha};
        for (int i = 0; i < 6; i++) {
            rows[i] = findViewById(ids[i]);
            timeViews[i] = findViewById(tv[i]);
        }

        findViewById(R.id.row_pick_location).setOnClickListener(v ->
                PlacePicker.show(this, this::reloadAfterLocation));
        findViewById(R.id.row_pick_method).setOnClickListener(v -> pickMethod());
        findViewById(R.id.btn_refresh).setOnClickListener(v -> fetch(true));
        findViewById(R.id.btn_adhan_settings).setOnClickListener(v ->
                startActivity(new android.content.Intent(this, AdhanSettingsActivity.class)));
        findViewById(R.id.btn_enable_location).setOnClickListener(v ->
                PlacePicker.show(this, this::reloadAfterLocation));
    }

    private void reloadAfterLocation() {
        fetch(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // cached view first (instant), then silent network refresh
        loadCache();
        if (pt == null) fetch(false);
        h.post(ticker);
    }

    @Override
    protected void onPause() {
        h.removeCallbacks(ticker);
        super.onPause();
    }

    private void loadCache() {
        double[] loc = Store.location(this);
        if (loc == null) {
            pt = null;
            render();
            return;
        }
        pt = PrayerCalc.day(this, 0);
        if (pt != null && !pt.hasTimes()) pt = null;
        render();
    }

    private void fetch(final boolean announce) {
        double[] loc = Store.location(this);
        if (loc == null) {
            render();
            return;
        }
        Ui.toast(this, R.string.loading);
        Api.fetchPrayerTimes(this, loc[0], loc[1], Store.prayerMethod(this), null,
                new Api.Cb<String>() {
                    @Override public void ok(String v) {
                        Store.setPrTimes(PrayerTimesActivity.this, v,
                                AdhanScheduler.todayKey());
                        loadCache();
                        AdhanScheduler.rescheduleAll(PrayerTimesActivity.this);
                        if (announce) Ui.toast(PrayerTimesActivity.this, R.string.pt_loaded);
                    }

                    @Override public void err(String m) {
                        // offline: keep the on-device calculation up to date
                        loadCache();
                        AdhanScheduler.rescheduleAll(PrayerTimesActivity.this);
                        if (announce) Ui.toast(PrayerTimesActivity.this, R.string.error_network);
                    }
                });
    }

    private void pickMethod() {
        boolean ar = Ui.isArabic();
        String[] names = new String[METHODS.length + 1];
        for (int i = 0; i < METHODS.length; i++) {
            names[i] = ar ? (String) METHODS[i][1] : (String) METHODS[i][2];
        }
        names[METHODS.length] = getString(R.string.pt_asr_school) + ": "
                + getString(Store.asrSchool(this) == 1
                ? R.string.pt_asr_hanafi : R.string.pt_asr_standard);
        new AlertDialog.Builder(this)
                .setTitle(R.string.pt_method)
                .setItems(names, (d, w) -> {
                    if (w == METHODS.length) {
                        Store.setAsrSchool(this, Store.asrSchool(this) == 1 ? 0 : 1);
                    } else {
                        Store.setPrayerMethod(this, (int) METHODS[w][0]);
                    }
                    // recompute locally right away, then refresh from the API
                    loadCache();
                    AdhanScheduler.rescheduleAll(this);
                    fetch(true);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void render() {
        double[] loc = Store.location(this);
        boolean hasLoc = loc != null;
        boolean ready = hasLoc && pt != null;
        emptyState.setVisibility(ready ? View.GONE : View.VISIBLE);
        findViewById(R.id.body).setVisibility(ready ? View.VISIBLE : View.GONE);

        if (!hasLoc) {
            tLoc.setText(R.string.pt_not_set);
            return;
        }
        String label = Store.locationLabel(this);
        tLoc.setText(label == null || label.isEmpty()
                ? String.format(Locale.US, "%.2f, %.2f", loc[0], loc[1]) : label);
        tMethod.setText(methodLabel());
        if (pt == null) return;

        String gDate = Ui.isArabic() ? pt.readableLocalized() : pt.readable;
        tDate.setText(Ui.isArabic() ? Ui.digits(gDate) : gDate);
        tHijri.setText(pt.hijri.isEmpty()
                ? getString(R.string.pt_offline_calc)
                : getString(R.string.pt_hijri_date,
                        Ui.isArabic() ? Ui.digits(pt.hijri) : pt.hijri));
        for (int i = 0; i < 6; i++) {
            String t = pt.raw[i];
            timeViews[i].setText(t.isEmpty() ? "—" : Ui.digits(t));
        }
        int cur = pt.currentIndex(System.currentTimeMillis());
        for (int i = 0; i < 6; i++) {
            rows[i].setSelected(false);
            rows[i].setBackgroundResource(i == cur
                    ? R.drawable.bg_row_active : 0);
        }
        updateCountdown();
    }

    private void updateCountdown() {
        if (pt == null) return;
        long now = System.currentTimeMillis();
        int next = pt.nextIndex(now);
        long nextAt = pt.nextTime(now);
        if (nextAt < 0) {
            // after isha — next is tomorrow's fajr (rough schedule from today + 24h)
            long tmr = pt.times[PrayerTimes.FAJR] + 24L * 3600_000L;
            if (pt.times[PrayerTimes.FAJR] <= 0) return;
            tNextName.setText(R.string.pt_fajr);
            tNextTime.setText(Ui.digits(pt.raw[PrayerTimes.FAJR]));
            tCountdown.setText(getString(R.string.pt_in, remain(tmr - now)));
            return;
        }
        if (nextAt - now < 60_000L) {
            tNextName.setText(getString(AdhanScheduler.nameRes(next)));
            tNextTime.setText("•");
            tCountdown.setText(getString(R.string.pt_now,
                    getString(AdhanScheduler.nameRes(next))));
            return;
        }
        tNextName.setText(getString(AdhanScheduler.nameRes(next)));
        tNextTime.setText(Ui.digits(pt.raw[next]));
        tCountdown.setText(getString(R.string.pt_in, remain(nextAt - now)));
    }

    private static String remain(long ms) {
        long s = Math.max(0, ms / 1000);
        long hh = s / 3600, mm = (s % 3600) / 60, ss = s % 60;
        return Ui.digits(String.format(Locale.US, "%02d:%02d:%02d", hh, mm, ss));
    }

    private String methodLabel() {
        int id = Store.prayerMethod(this);
        boolean ar = Ui.isArabic();
        String base = id + "";
        for (Object[] m : METHODS) {
            if ((int) m[0] == id) {
                base = ar ? (String) m[1] : (String) m[2];
                break;
            }
        }
        String school = getString(Store.asrSchool(this) == 1
                ? R.string.pt_asr_hanafi : R.string.pt_asr_standard);
        return base + " • " + school;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 41) {
            PlacePicker.afterPermission(this, this::reloadAfterLocation);
        }
    }
}
