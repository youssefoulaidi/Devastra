package com.quranpro.app.ui;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.quranpro.app.R;
import com.quranpro.app.data.Store;
import com.quranpro.app.pray.AdhanScheduler;
import com.quranpro.app.util.Ui;

/** Shared location chooser: GPS fix, preset cities, or manual coordinates. */
public final class PlacePicker {
    private PlacePicker() {
    }

    public interface OnPicked {
        void picked();
    }

    /** name, lat, lon */
    private static final String[][] CITIES = {
            {"مراكش، المغرب", "31.6295", "-7.9811"},
            {"الدار البيضاء، المغرب", "33.5731", "-7.5898"},
            {"الرباط، المغرب", "34.0209", "-6.8416"},
            {"فاس، المغرب", "34.0331", "-5.0003"},
            {"طنجة، المغرب", "35.7595", "-5.8340"},
            {"أكادير، المغرب", "30.4278", "-9.5981"},
            {"القدس، فلسطين", "31.7683", "35.2137"},
            {"مكة المكرمة، السعودية", "21.3891", "39.8579"},
            {"المدينة المنورة، السعودية", "24.5247", "39.5692"},
            {"القاهرة، مصر", "30.0444", "31.2357"},
            {"دبي، الإمارات", "25.2048", "55.2708"},
            {"إستانبول، تركيا", "41.0082", "28.9784"},
            {"عمّان، الأردن", "31.9539", "35.9106"},
            {"بغداد، العراق", "33.3152", "44.3661"},
            {"كوالالمبور، ماليزيا", "3.1390", "101.6869"},
            {"لندن، بريطانيا", "51.5074", "-0.1278"},
            {"باريس، فرنسا", "48.8566", "2.3522"},
    };

    public static void show(final Activity a, final OnPicked cb) {
        String[] opts = {
                a.getString(R.string.pt_gps),
                a.getString(R.string.pt_pick_city),
                a.getString(R.string.pt_manual),
        };
        new AlertDialog.Builder(a)
                .setTitle(R.string.pt_location)
                .setItems(opts, (d, w) -> {
                    if (w == 0) useGps(a, cb);
                    else if (w == 1) pickCity(a, cb);
                    else manual(a, cb);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static void pickCity(final Activity a, final OnPicked cb) {
        String[] names = new String[CITIES.length];
        for (int i = 0; i < CITIES.length; i++) names[i] = CITIES[i][0];
        new AlertDialog.Builder(a)
                .setTitle(R.string.pt_pick_city)
                .setItems(names, (d, w) -> {
                    save(a, Double.parseDouble(CITIES[w][1]),
                            Double.parseDouble(CITIES[w][2]), CITIES[w][0]);
                    if (cb != null) cb.picked();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static void manual(final Activity a, final OnPicked cb) {
        float density = a.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int) (20 * density), (int) (8 * density),
                (int) (20 * density), 0);

        final TextInputLayout l1 = new TextInputLayout(a);
        final TextInputEditText e1 = new TextInputEditText(l1);
        e1.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        l1.setHint(a.getString(R.string.pt_lat));
        l1.addView(e1);

        final TextInputLayout l2 = new TextInputLayout(a);
        final TextInputEditText e2 = new TextInputEditText(l2);
        e2.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        l2.setHint(a.getString(R.string.pt_lon));
        l2.addView(e2);

        final TextInputLayout l3 = new TextInputLayout(a);
        final TextInputEditText e3 = new TextInputEditText(l3);
        l3.setHint(a.getString(R.string.pt_city_name));
        l3.addView(e3);

        box.addView(l1);
        box.addView(l2);
        box.addView(l3);

        new AlertDialog.Builder(a)
                .setTitle(R.string.pt_manual)
                .setView(box)
                .setPositiveButton(R.string.pt_save, (d, w) -> {
                    try {
                        double lat = Double.parseDouble(val(e1).trim());
                        double lon = Double.parseDouble(val(e2).trim());
                        String name = val(e3).trim();
                        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                            Ui.toast(a, R.string.error_generic);
                            return;
                        }
                        save(a, lat, lon, name.isEmpty() ? (lat + "," + lon) : name);
                        if (cb != null) cb.picked();
                    } catch (Exception e) {
                        Ui.toast(a, R.string.error_generic);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static String val(EditText e) {
        return e.getText() == null ? "" : e.getText().toString();
    }

    private static void useGps(final Activity a, final OnPicked cb) {
        if (ContextCompat.checkSelfPermission(a, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(a, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, 41);
            Ui.toast(a, R.string.pt_gps_perm);
            return;
        }
        fix(a, cb);
    }

    /** Called by the activity when permission is granted (retry path). */
    public static void afterPermission(Activity a, OnPicked cb) {
        if (ContextCompat.checkSelfPermission(a, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) fix(a, cb);
    }

    private static void fix(final Activity a, final OnPicked cb) {
        Ui.toast(a, R.string.pt_wait_gps);
        LocationManager lm = (LocationManager) a.getSystemService(Activity.LOCATION_SERVICE);
        if (lm == null) {
            Ui.toast(a, R.string.pt_gps_fail);
            return;
        }
        // last known first (instant), then one fresh fix as upgrade
        try {
            Location last = null;
            for (String p : new String[]{LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER}) {
                try {
                    if (lm.isProviderEnabled(p)) {
                        Location l = lm.getLastKnownLocation(p);
                        if (l != null && (last == null || l.getTime() > last.getTime())) last = l;
                    }
                } catch (Exception ignored) {}
            }
            if (last != null) {
                save(a, last.getLatitude(), last.getLongitude(),
                        a.getString(R.string.pt_gps));
                if (cb != null) cb.picked();
            }
            // refresh with a live fix shortly after
            final LocationListener[] holder = new LocationListener[1];
            holder[0] = new LocationListener() {
                @Override
                public void onLocationChanged(Location loc) {
                    try {
                        lm.removeUpdates(holder[0]);
                    } catch (Exception ignored) {}
                    save(a, loc.getLatitude(), loc.getLongitude(),
                            a.getString(R.string.pt_gps));
                    if (cb != null) cb.picked();
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {
                }

                @Override
                public void onProviderEnabled(String provider) {
                }

                @Override
                public void onProviderDisabled(String provider) {
                }
            };
            for (String p : new String[]{LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER}) {
                try {
                    if (lm.isProviderEnabled(p)) {
                        lm.requestLocationUpdates(p, 4000L, 10f, holder[0]);
                        break;
                    }
                } catch (Exception ignored) {}
            }
        } catch (SecurityException e) {
            Ui.toast(a, R.string.pt_gps_fail);
        }
    }

    private static void save(final Activity a, double lat, double lon, String label) {
        Store.setLocation(a, lat, lon, label);
        // immediately refresh times + alarms for the new place
        com.quranpro.app.data.Api.fetchPrayerTimes(a, lat, lon,
                Store.prayerMethod(a), null,
                new com.quranpro.app.data.Api.Cb<String>() {
                    @Override
                    public void ok(String v) {
                        Store.setPrTimes(a, v, AdhanScheduler.todayKey());
                        AdhanScheduler.rescheduleAll(a);
                        Ui.toast(a, R.string.pt_loaded);
                    }

                    @Override
                    public void err(String m) {
                        AdhanScheduler.rescheduleAll(a);
                    }
                });
    }
}
