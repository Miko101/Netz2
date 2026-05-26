package com.example.netzv2.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.netzv2.R;

import java.util.Collections;
import java.util.Date;
import java.util.List;

public class MilestoneLineView extends View {

    private List<MainViewModel.Milestone> milestones = Collections.emptyList();
    @Nullable private Date netz;
    private long nowMs = System.currentTimeMillis();

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotEmptyFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotEmptyOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelDimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final float density;
    private final float scaledDensity;

    public MilestoneLineView(Context ctx) { this(ctx, null); }

    public MilestoneLineView(Context ctx, @Nullable AttributeSet attrs) {
        super(ctx, attrs);
        density = ctx.getResources().getDisplayMetrics().density;
        scaledDensity = ctx.getResources().getDisplayMetrics().scaledDensity;

        int trackColor = ContextCompat.getColor(ctx, R.color.surface_border);
        int progressColor = ContextCompat.getColor(ctx, R.color.accent_gold);
        int dotColor = ContextCompat.getColor(ctx, R.color.text_primary);
        int dotEmptyFill = bgCardColor(ctx);
        int labelColor = ContextCompat.getColor(ctx, R.color.text_primary);
        int labelDimColor = ContextCompat.getColor(ctx, R.color.text_secondary);
        int markerColor = ContextCompat.getColor(ctx, R.color.accent_gold);

        trackPaint.setColor(trackColor);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(dp(3));
        trackPaint.setStrokeCap(Paint.Cap.ROUND);

        progressPaint.setColor(progressColor);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(dp(3));
        progressPaint.setStrokeCap(Paint.Cap.ROUND);

        dotFillPaint.setColor(dotColor);
        dotFillPaint.setStyle(Paint.Style.FILL);

        dotEmptyFillPaint.setColor(dotEmptyFill);
        dotEmptyFillPaint.setStyle(Paint.Style.FILL);

        dotEmptyOutlinePaint.setColor(trackColor);
        dotEmptyOutlinePaint.setStyle(Paint.Style.STROKE);
        dotEmptyOutlinePaint.setStrokeWidth(dp(2));

        labelPaint.setColor(labelColor);
        labelPaint.setTextSize(sp(15));

        labelDimPaint.setColor(labelDimColor);
        labelDimPaint.setTextSize(sp(15));

        markerPaint.setColor(markerColor);
        markerPaint.setStyle(Paint.Style.FILL);

        markerRingPaint.setColor(markerColor);
        markerRingPaint.setAlpha(80);
        markerRingPaint.setStyle(Paint.Style.STROKE);
        markerRingPaint.setStrokeWidth(dp(2));
    }

    private static int bgCardColor(Context ctx) {
        return Color.argb(40, 0, 0, 0);
    }

    public void setData(@NonNull List<MainViewModel.Milestone> milestones, @Nullable Date netz) {
        this.milestones = milestones;
        this.netz = netz;
        invalidate();
    }

    public void setNow(long ms) {
        if (ms == nowMs) return;
        this.nowMs = ms;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (milestones.isEmpty() || netz == null) return;

        long start = milestones.get(0).time.getTime();
        long end = netz.getTime();
        if (end <= start) return;

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        boolean horizontal = w > h;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;

        if (horizontal) {
            drawHorizontal(canvas, w, h, start, end, rtl);
        } else {
            drawVertical(canvas, w, h, start, end, rtl);
        }
    }

    private void drawVertical(Canvas canvas, int w, int h, long start, long end, boolean rtl) {
        float dotR = dp(7);
        float markerR = dp(10);
        float trackInset = dp(28);
        float lineX = rtl ? w - getPaddingRight() - trackInset : getPaddingLeft() + trackInset;
        float topInset = getPaddingTop() + dp(12) + markerR;
        float botInset = getPaddingBottom() + dp(12) + markerR;
        float lineTop = topInset;
        float lineBottom = h - botInset;
        if (lineBottom <= lineTop) return;
        float lineLen = lineBottom - lineTop;

        canvas.drawLine(lineX, lineTop, lineX, lineBottom, trackPaint);

        float nowP = clamp01((nowMs - start) / (float)(end - start));
        float nowY = lineTop + nowP * lineLen;
        if (nowP > 0f) canvas.drawLine(lineX, lineTop, lineX, nowY, progressPaint);

        for (MainViewModel.Milestone m : milestones) {
            float p = clamp01((m.time.getTime() - start) / (float)(end - start));
            float dotY = lineTop + p * lineLen;
            boolean passed = nowMs >= m.time.getTime();
            drawDot(canvas, lineX, dotY, dotR, passed);

            String label = getContext().getString(m.labelRes);
            Paint pnt = passed ? labelPaint : labelDimPaint;
            float labelX;
            if (rtl) {
                pnt.setTextAlign(Paint.Align.RIGHT);
                labelX = lineX - dotR - dp(12);
            } else {
                pnt.setTextAlign(Paint.Align.LEFT);
                labelX = lineX + dotR + dp(12);
            }
            float labelY = baselineFor(pnt, dotY);
            canvas.drawText(label, labelX, labelY, pnt);
        }

        drawMarker(canvas, lineX, nowY, markerR);
    }

    private void drawHorizontal(Canvas canvas, int w, int h, long start, long end, boolean rtl) {
        float dotR = dp(6);
        float markerR = dp(9);
        float sideInset = dp(20);
        float lineLeft = getPaddingLeft() + sideInset;
        float lineRight = w - getPaddingRight() - sideInset;
        if (lineRight <= lineLeft) return;
        float lineLen = lineRight - lineLeft;
        // Place the track in the upper portion so labels (alternating above/below) fit.
        float lineY = getPaddingTop() + dp(28);

        canvas.drawLine(lineLeft, lineY, lineRight, lineY, trackPaint);

        float nowP = clamp01((nowMs - start) / (float)(end - start));
        float nowX = rtl ? lineRight - nowP * lineLen : lineLeft + nowP * lineLen;
        if (nowP > 0f) {
            float progressStartX = rtl ? lineRight : lineLeft;
            canvas.drawLine(progressStartX, lineY, nowX, lineY, progressPaint);
        }

        int i = 0;
        for (MainViewModel.Milestone m : milestones) {
            float p = clamp01((m.time.getTime() - start) / (float)(end - start));
            float dotX = rtl ? lineRight - p * lineLen : lineLeft + p * lineLen;
            boolean passed = nowMs >= m.time.getTime();
            drawDot(canvas, dotX, lineY, dotR, passed);

            String label = getContext().getString(m.labelRes);
            Paint pnt = passed ? labelPaint : labelDimPaint;
            pnt.setTextAlign(Paint.Align.CENTER);
            boolean below = (i % 2 == 0);
            float labelY = below
                    ? lineY + dotR + dp(8) - pnt.getFontMetrics().ascent
                    : lineY - dotR - dp(8) - pnt.getFontMetrics().descent;
            canvas.drawText(label, dotX, labelY, pnt);
            i++;
        }

        drawMarker(canvas, nowX, lineY, markerR);
    }

    private void drawDot(Canvas canvas, float cx, float cy, float r, boolean passed) {
        if (passed) {
            canvas.drawCircle(cx, cy, r, dotFillPaint);
        } else {
            canvas.drawCircle(cx, cy, r, dotEmptyFillPaint);
            canvas.drawCircle(cx, cy, r, dotEmptyOutlinePaint);
        }
    }

    private void drawMarker(Canvas canvas, float cx, float cy, float r) {
        canvas.drawCircle(cx, cy, r * 1.6f, markerRingPaint);
        canvas.drawCircle(cx, cy, r, markerPaint);
    }

    private static float baselineFor(Paint p, float centerY) {
        Paint.FontMetrics fm = p.getFontMetrics();
        return centerY - (fm.ascent + fm.descent) / 2f;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private float dp(float v) { return v * density; }
    private float sp(float v) { return v * scaledDensity; }
}
