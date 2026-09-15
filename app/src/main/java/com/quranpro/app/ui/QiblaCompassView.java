package com.quranpro.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.quranpro.app.R;

/**
 * Compass dial: rotates opposite the device heading. The golden Kaaba marker
 * sits at the qibla bearing; when the user aligns (marker meets the top
 * pointer) the ring glows emerald.
 */
public class QiblaCompassView extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tri = new Path();
    private final RectF oval = new RectF();

    private float heading;    // device heading, deg from true north
    private float qibla = -1; // qibla bearing, deg from true north
    private boolean aligned;

    private final int gold, brand, muted, surfaceC;

    public QiblaCompassView(Context c, @Nullable AttributeSet a) {
        super(c, a);
        gold = ContextCompat.getColor(c, R.color.gold);
        brand = ContextCompat.getColor(c, R.color.brand);
        muted = ContextCompat.getColor(c, R.color.muted);
        surfaceC = ContextCompat.getColor(c, R.color.surface);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        text.setTextAlign(Paint.Align.CENTER);
    }

    public void setHeading(float deg) {
        heading = norm360(deg);
        aligned = qibla >= 0 && Math.abs(diff()) < 5f;
        invalidate();
    }

    public void setQibla(float deg) {
        qibla = norm360(deg);
        aligned = Math.abs(diff()) < 5f;
        invalidate();
    }

    private static float norm360(float a) {
        float r = a % 360f;
        return r < 0 ? r + 360f : r;
    }

    /** Signed shortest angle from heading to qibla (-180..180). */
    public float diff() {
        if (qibla < 0) return 0;
        return ((qibla - heading + 540f) % 360f) - 180f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) / 2f - dp(14);
        if (r <= dp(20)) return;

        // plate
        fill.setColor(surfaceC);
        fill.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, r + dp(8), fill);

        // ring
        stroke.setColor(aligned ? brand : gold);
        stroke.setStrokeWidth(dp(aligned ? 4.5f : 2.5f));
        canvas.drawCircle(cx, cy, r + dp(6), stroke);

        // rotating dial
        canvas.save();
        canvas.rotate(-heading, cx, cy);

        // ticks
        for (int a = 0; a < 360; a += 5) {
            double rad = Math.toRadians(a - 90);
            float big = (a % 45 == 0) ? dp(12) : (a % 15 == 0 ? dp(7) : dp(3.5f));
            float x1 = cx + (float) Math.cos(rad) * r;
            float y1 = cy + (float) Math.sin(rad) * r;
            float x2 = cx + (float) Math.cos(rad) * (r - big);
            float y2 = cy + (float) Math.sin(rad) * (r - big);
            stroke.setColor((a % 45 == 0) ? muted : Color.argb(70, 120, 120, 120));
            stroke.setStrokeWidth(dp(a % 45 == 0 ? 2f : 1f));
            canvas.drawLine(x1, y1, x2, y2, stroke);
        }

        // cardinal letters
        boolean ar = isArabic();
        float tr = r - dp(26);
        String[] labels = ar
                ? new String[]{"ش", "ج", "ق", "غ"}
                : new String[]{"N", "E", "S", "W"};
        text.setTextSize(dp(15));
        for (int i = 0; i < 4; i++) {
            double rad = Math.toRadians(i * 90 - 90);
            float x = cx + (float) Math.cos(rad) * tr;
            float y = cy + (float) Math.sin(rad) * tr + text.getTextSize() / 3f;
            text.setColor(i == 0 ? gold : muted);
            canvas.drawText(labels[i], x, y, text);
        }

        // qibla marker (little kaaba) on the dial + needle
        if (qibla >= 0) {
            double rad = Math.toRadians(qibla - 90);
            float mx = cx + (float) Math.cos(rad) * (r - dp(22));
            float my = cy + (float) Math.sin(rad) * (r - dp(22));
            stroke.setColor(aligned ? brand : gold);
            stroke.setStrokeWidth(dp(3f));
            canvas.drawLine(cx, cy, mx, my, stroke);
            drawKaaba(canvas, mx, my);
        }
        canvas.restore();

        // fixed top pointer
        tri.reset();
        tri.moveTo(cx, cy - r - dp(14));
        tri.lineTo(cx - dp(9), cy - r - dp(27));
        tri.lineTo(cx + dp(9), cy - r - dp(27));
        tri.close();
        fill.setColor(aligned ? brand : gold);
        fill.setStyle(Paint.Style.FILL);
        canvas.drawPath(tri, fill);

        // hub
        fill.setColor(gold);
        canvas.drawCircle(cx, cy, dp(6), fill);
        fill.setColor(surfaceC);
        canvas.drawCircle(cx, cy, dp(2.6f), fill);
    }

    private void drawKaaba(Canvas canvas, float x, float y) {
        float s = dp(13);
        fill.setColor(Color.parseColor("#10231B"));
        fill.setStyle(Paint.Style.FILL);
        oval.set(x - s, y - s, x + s, y + s);
        canvas.drawRoundRect(oval, dp(3), dp(3), fill);
        fill.setColor(gold);
        canvas.drawRect(x - s, y - s * 0.35f, x + s, y - s * 0.05f, fill);
    }

    private boolean isArabic() {
        return com.quranpro.app.util.Ui.isArabic();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
