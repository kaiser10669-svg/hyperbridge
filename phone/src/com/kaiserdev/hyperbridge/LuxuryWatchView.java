package com.kaiserdev.hyperbridge;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LuxuryWatchView extends View {

    private Paint casePaint;
    private Paint caseBevelPaint;
    private Paint crownPaint;
    private Paint crownAccentPaint;
    private Paint electrodePaint;
    private Paint tickPaint;
    private Paint amoledPaint;
    private Paint glarePaint;
    private Paint cyberRingPaint;
    private Paint textTimePaint;
    private Paint textDatePaint;
    private Paint textMetricPaint;
    private Paint statusDotPaint;

    private Path glarePath;
    private RectF amoledBounds;

    // Live display states
    private String timeStr = "17:15";
    private String dateStr = "ВТОРНИК, 27 ОКТ.";
    private int heartRate = 0;
    private int steps = -1;
    private boolean isConnected = false;
    private float cyberAngle = 0f;

    public LuxuryWatchView(Context context) {
        super(context);
        init();
    }

    public LuxuryWatchView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LuxuryWatchView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Hardware accelerated native rendering
        casePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        casePaint.setStyle(Paint.Style.FILL);

        caseBevelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        caseBevelPaint.setStyle(Paint.Style.STROKE);
        caseBevelPaint.setStrokeWidth(2.5f);
        caseBevelPaint.setColor(Color.parseColor("#4B586E"));

        crownPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        crownPaint.setStyle(Paint.Style.FILL);
        crownPaint.setColor(Color.parseColor("#2A313D"));

        crownAccentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        crownAccentPaint.setStyle(Paint.Style.FILL);
        crownAccentPaint.setColor(Color.parseColor("#FF6D00")); // Signature Ultra Crown Ring

        electrodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        electrodePaint.setStyle(Paint.Style.FILL);
        electrodePaint.setColor(Color.parseColor("#374151"));

        tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);

        amoledPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        amoledPaint.setStyle(Paint.Style.FILL);
        amoledPaint.setColor(Color.parseColor("#020408"));

        glarePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glarePaint.setStyle(Paint.Style.FILL);

        cyberRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cyberRingPaint.setStyle(Paint.Style.STROKE);
        cyberRingPaint.setStrokeWidth(2f);

        textTimePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textTimePaint.setColor(Color.WHITE);
        textTimePaint.setTextAlign(Paint.Align.CENTER);
        textTimePaint.setFakeBoldText(true);
        textTimePaint.setShadowLayer(16f, 0, 0, Color.parseColor("#6600E5FF"));

        textDatePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textDatePaint.setColor(Color.parseColor("#00E5FF"));
        textDatePaint.setTextAlign(Paint.Align.CENTER);
        textDatePaint.setLetterSpacing(0.12f);
        textDatePaint.setFakeBoldText(true);

        textMetricPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textMetricPaint.setTextAlign(Paint.Align.CENTER);
        textMetricPaint.setFakeBoldText(true);

        statusDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        statusDotPaint.setStyle(Paint.Style.FILL);

        glarePath = new Path();
        amoledBounds = new RectF();
    }

    public void setTelemetry(String time, String date, int hr, int st, boolean connected) {
        if (time != null) this.timeStr = time;
        if (date != null) this.dateStr = date;
        this.heartRate = hr;
        this.steps = st;
        this.isConnected = connected;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float r = Math.min(w, h) * 0.44f;

        // 1. Titanium Digital Crown (Top-Right, 2 o'clock)
        float crownW = 12f;
        float crownH = 26f;
        float crownX = cx + r - 3f;
        float crownY = cy - 36f;
        RectF crownRect = new RectF(crownX, crownY, crownX + crownW, crownY + crownH);
        canvas.drawRoundRect(crownRect, 4f, 4f, crownPaint);
        // Crown accent band
        RectF crownAccent = new RectF(crownX + 3f, crownY + 2f, crownX + 6f, crownY + crownH - 2f);
        canvas.drawRoundRect(crownAccent, 2f, 2f, crownAccentPaint);

        // 2. Titanium ECG Sensor Electrode Button (Bottom-Right, 4 o'clock)
        float elecW = 10f;
        float elecH = 22f;
        float elecX = cx + r - 4f;
        float elecY = cy + 18f;
        RectF elecRect = new RectF(elecX, elecY, elecX + elecW, elecY + elecH);
        canvas.drawRoundRect(elecRect, 4f, 4f, electrodePaint);

        // 3. Multi-layer Titanium Watch Chassis
        LinearGradient caseShader = new LinearGradient(
                cx - r, cy - r, cx + r, cy + r,
                new int[]{
                        Color.parseColor("#434F63"),
                        Color.parseColor("#1B222C"),
                        Color.parseColor("#2D3644"),
                        Color.parseColor("#131820"),
                        Color.parseColor("#5A6982")
                },
                new float[]{0.0f, 0.25f, 0.5f, 0.75f, 1.0f},
                Shader.TileMode.CLAMP
        );
        casePaint.setShader(caseShader);
        canvas.drawCircle(cx, cy, r, casePaint);
        canvas.drawCircle(cx, cy, r, caseBevelPaint);

        // 4. Tachymeter & Minutes Bezel Ring
        float bezelR = r - 7f;
        for (int i = 0; i < 60; i++) {
            float angle = (float) (i * 6.0 * Math.PI / 180.0);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);

            boolean isHour = (i % 5 == 0);
            boolean isCardinal = (i % 15 == 0);

            float tickLen = isCardinal ? 9f : (isHour ? 7f : 4f);
            float outerX = cx + bezelR * cos;
            float outerY = cy + bezelR * sin;
            float innerX = cx + (bezelR - tickLen) * cos;
            float innerY = cy + (bezelR - tickLen) * sin;

            if (isCardinal) {
                tickPaint.setColor(Color.parseColor("#00E5FF"));
                tickPaint.setStrokeWidth(2.8f);
            } else if (isHour) {
                tickPaint.setColor(Color.parseColor("#D4E0F0"));
                tickPaint.setStrokeWidth(2.0f);
            } else {
                tickPaint.setColor(Color.parseColor("#263445"));
                tickPaint.setStrokeWidth(1.2f);
            }
            canvas.drawLine(outerX, outerY, innerX, innerY, tickPaint);
        }

        // 5. AMOLED Display Interior
        float amoledR = r - 22f;
        amoledBounds.set(cx - amoledR, cy - amoledR, cx + amoledR, cy + amoledR);
        canvas.drawCircle(cx, cy, amoledR, amoledPaint);

        // Inner glowing cyber radar ring
        cyberAngle += 1.5f;
        if (cyberAngle >= 360f) cyberAngle = 0f;
        SweepGradient cyberShader = new SweepGradient(cx, cy,
                new int[]{0x0000E5FF, 0xFF00E5FF, 0x0000E5FF},
                new float[]{0.0f, 0.35f, 1.0f}
        );
        cyberRingPaint.setShader(cyberShader);
        canvas.save();
        canvas.rotate(cyberAngle, cx, cy);
        canvas.drawCircle(cx, cy, amoledR - 3f, cyberRingPaint);
        canvas.restore();

        // 6. Curved Sapphire Glass Reflection Arc (Top Quadrant)
        glarePath.reset();
        glarePath.moveTo(cx - amoledR * 0.85f, cy - amoledR * 0.2f);
        glarePath.quadTo(cx, cy - amoledR * 0.95f, cx + amoledR * 0.85f, cy - amoledR * 0.2f);
        glarePath.quadTo(cx, cy - amoledR * 0.65f, cx - amoledR * 0.85f, cy - amoledR * 0.2f);
        glarePath.close();

        LinearGradient glareShader = new LinearGradient(
                cx, cy - amoledR, cx, cy,
                new int[]{0x2BFFFFFF, 0x05FFFFFF, 0x00FFFFFF},
                new float[]{0.0f, 0.6f, 1.0f},
                Shader.TileMode.CLAMP
        );
        glarePaint.setShader(glareShader);
        canvas.drawPath(glarePath, glarePaint);

        // 7. Watch Face Content
        // A. Date
        textDatePaint.setTextSize(amoledR * 0.12f);
        canvas.drawText(dateStr, cx, cy - amoledR * 0.44f, textDatePaint);

        // B. Big Bold AMOLED Digital Time
        textTimePaint.setTextSize(amoledR * 0.44f);
        canvas.drawText(timeStr, cx, cy + amoledR * 0.06f, textTimePaint);

        // C. Sub-Widgets (Pulse & Steps)
        textMetricPaint.setTextSize(amoledR * 0.13f);
        String hrText = (heartRate > 0) ? ("❤️ " + heartRate) : "❤️ --";
        String stText;
        if (steps > 0) {
            if (steps >= 1000) {
                stText = String.format(java.util.Locale.US, "👟 %.1fk", steps / 1000f);
            } else {
                stText = "👟 " + steps;
            }
        } else {
            stText = "👟 0";
        }
        textMetricPaint.setColor(Color.parseColor("#FF4D6D"));
        canvas.drawText(hrText, cx - amoledR * 0.38f, cy + amoledR * 0.38f, textMetricPaint);

        textMetricPaint.setColor(Color.parseColor("#00E5FF"));
        canvas.drawText(stText, cx + amoledR * 0.38f, cy + amoledR * 0.38f, textMetricPaint);

        // D. Connection Dot Indicator
        float dotR = 3.5f;
        float dotY = cy + amoledR * 0.62f;
        statusDotPaint.setColor(isConnected ? Color.parseColor("#00E676") : Color.parseColor("#FF9671"));
        canvas.drawCircle(cx, dotY, dotR, statusDotPaint);

        // Subtle animation loop
        postInvalidateOnAnimation();
    }
}
