package com.quranpro.app.ui;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.Surface;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.quranpro.app.R;
import com.quranpro.app.data.Store;
import com.quranpro.app.util.Ui;

import java.util.Locale;

/** Qibla compass: bearing to the Kaaba from the saved location + live device heading. */
public class QiblaActivity extends AppCompatActivity implements SensorEventListener {

    // Kaaba — المسجد الحرام، مكة المكرمة
    public static final double KA_LAT = 21.4224779, KA_LON = 39.8251832;

    private QiblaCompassView compass;
    private TextView tBearing, tDistance, tStatus, tLocation, tHint, tHeading;

    private SensorManager sm;
    private Sensor rotSensor, accSensor, magSensor;
    private final float[] rotM = new float[9];
    private final float[] tmpM = new float[9];
    private final float[] I = new float[9];
    private final float[] ori = new float[3];
    private float[] acc, mag;
    private boolean hasAcc, hasMag;
    private float qiblaDeg = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qibla);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_close);
        toolbar.setNavigationOnClickListener(v -> finish());

        compass = findViewById(R.id.compass);
        tBearing = findViewById(R.id.t_bearing);
        tDistance = findViewById(R.id.t_distance);
        tStatus = findViewById(R.id.t_status);
        tLocation = findViewById(R.id.t_location);
        tHeading = findViewById(R.id.t_heading);
        tHint = findViewById(R.id.t_hint);

        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sm != null) {
            rotSensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (rotSensor == null) {
                accSensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                magSensor = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            }
        }
        if (rotSensor == null && (accSensor == null || magSensor == null)) {
            tHint.setText(R.string.qibla_no_sensor);
        }

        findViewById(R.id.row_location).setOnClickListener(v ->
                PlacePicker.show(this, this::refreshBearing));

        refreshBearing();
    }

    private void refreshBearing() {
        double[] loc = Store.location(this);
        String label = Store.locationLabel(this);
        if (loc == null) {
            tLocation.setText(R.string.pt_not_set);
            Ui.toast(this, R.string.qibla_need_location);
            PlacePicker.show(this, this::refreshBearing);
            return;
        }
        tLocation.setText(label == null || label.isEmpty()
                ? String.format(Locale.US, "%.3f, %.3f", loc[0], loc[1]) : label);

        qiblaDeg = (float) qiblaBearing(loc[0], loc[1]);
        compass.setQibla(qiblaDeg);
        tBearing.setText(getString(R.string.qibla_bearing,
                Ui.digits(String.format(Locale.US, "%.0f", qiblaDeg))));
        tDistance.setText(getString(R.string.qibla_distance,
                Ui.digits(String.format(Locale.US, "%.0f", distanceKm(loc[0], loc[1])))));
        updateStatus(0);
    }

    /** Great-circle initial bearing to the Kaaba, degrees from true north. */
    public static double qiblaBearing(double lat, double lon) {
        double phi1 = Math.toRadians(lat);
        double phi2 = Math.toRadians(KA_LAT);
        double dl = Math.toRadians(KA_LON - lon);
        double y = Math.sin(dl);
        double x = Math.cos(phi1) * Math.tan(phi2) - Math.sin(phi1) * Math.cos(dl);
        double deg = Math.toDegrees(Math.atan2(y, x));
        return (deg + 360) % 360;
    }

    /** Haversine distance to the Kaaba in km. */
    public static double distanceKm(double lat, double lon) {
        double r = 6371.0;
        double dLat = Math.toRadians(KA_LAT - lat);
        double dLon = Math.toRadians(KA_LON - lon);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(KA_LAT))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sm == null) return;
        if (rotSensor != null) {
            sm.registerListener(this, rotSensor, SensorManager.SENSOR_DELAY_UI);
        } else {
            if (accSensor != null) {
                sm.registerListener(this, accSensor, SensorManager.SENSOR_DELAY_UI);
            }
            if (magSensor != null) {
                sm.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_UI);
            }
        }
    }

    @Override
    protected void onPause() {
        if (sm != null) sm.unregisterListener(this);
        super.onPause();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int type = event.sensor.getType();
        if (type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotM, event.values);
            applyDisplayRemap();
            SensorManager.getOrientation(rotM, ori);
            updateHeading(ori[0]);
        } else if (type == Sensor.TYPE_ACCELEROMETER) {
            if (acc == null) acc = new float[3];
            System.arraycopy(event.values, 0, acc, 0, 3);
            hasAcc = true;
            fuseAccMag();
        } else if (type == Sensor.TYPE_MAGNETIC_FIELD) {
            if (mag == null) mag = new float[3];
            System.arraycopy(event.values, 0, mag, 0, 3);
            hasMag = true;
            fuseAccMag();
        }
    }

    private void fuseAccMag() {
        if (rotSensor != null || !hasAcc || !hasMag) return;
        if (SensorManager.getRotationMatrix(rotM, I, acc, mag)) {
            applyDisplayRemap();
            SensorManager.getOrientation(rotM, ori);
            updateHeading(ori[0]);
        }
    }

    /** Keep azimuth relative to the top edge for the current display rotation. */
    private void applyDisplayRemap() {
        int rot = getWindowManager().getDefaultDisplay().getRotation();
        boolean swapped = false;
        if (rot == Surface.ROTATION_90) {
            swapped = SensorManager.remapCoordinateSystem(rotM,
                    SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, tmpM);
        } else if (rot == Surface.ROTATION_270) {
            swapped = SensorManager.remapCoordinateSystem(rotM,
                    SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, tmpM);
        } else if (rot == Surface.ROTATION_180) {
            swapped = SensorManager.remapCoordinateSystem(rotM,
                    SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, tmpM);
        }
        if (swapped) {
            for (int i = 0; i < 9; i++) rotM[i] = tmpM[i];
        }
    }

    private void updateHeading(float azimuthRad) {
        float deg = (float) Math.toDegrees(azimuthRad);
        deg = (deg + 360f) % 360f;
        compass.setHeading(deg);
        tHeading.setText(getString(R.string.qibla_device_heading,
                Ui.digits(String.format(Locale.US, "%.0f", deg))));
        updateStatus(compass.diff());
    }

    private void updateStatus(float diff) {
        if (qiblaDeg < 0) return;
        if (Math.abs(diff) <= 5f) tStatus.setText(R.string.qibla_aligned);
        else tStatus.setText(diff > 0 ? R.string.qibla_turn_right : R.string.qibla_turn_left);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
