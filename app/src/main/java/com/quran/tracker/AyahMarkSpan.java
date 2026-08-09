package com.quran.tracker;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.style.ReplacementSpan;

/**
 * The small ring around an ayah number, drawn on the Canvas rather than
 * composed from any font glyph or Unicode combining mark.
 *
 * The WebView build tried U+06DD (which needs shaping support the renderer
 * did not have — it left a placeholder box) and an SVG-mask ring (which
 * rendered as a dotted circle on the device it was tested on). Neither
 * failure is possible here: this is two drawCircle/drawText calls against a
 * Paint, with no dependency on which face ends up active for the digits.
 */
final class AyahMarkSpan extends ReplacementSpan {

    private final String label;
    private final int ringColor;
    private final int textColor;
    private final float scale;

    AyahMarkSpan(String label, int ringColor, int textColor, float scale) {
        this.label = label;
        this.ringColor = ringColor;
        this.textColor = textColor;
        this.scale = scale;
    }

    private float diameter(Paint paint) {
        return paint.getTextSize() * scale;
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        int d = Math.round(diameter(paint) * 1.15f);   // a little breathing room each side
        if (fm != null) {
            Paint.FontMetricsInt pfm = paint.getFontMetricsInt();
            fm.ascent = pfm.ascent;
            fm.descent = pfm.descent;
            fm.top = pfm.top;
            fm.bottom = pfm.bottom;
        }
        return d;
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x,
                      int top, int y, int bottom, Paint paint) {
        float d = diameter(paint);
        float cx = x + d * 1.15f / 2f;
        float cy = y - d * 0.32f;                     // sit on the baseline like a mushaf mark

        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Math.max(1.5f, d * 0.06f));
        ring.setColor(ringColor);
        canvas.drawOval(new RectF(cx - d / 2f, cy - d / 2f, cx + d / 2f, cy + d / 2f), ring);

        Paint digits = new Paint(Paint.ANTI_ALIAS_FLAG);
        digits.setColor(textColor);
        digits.setTextSize(d * 0.5f);
        digits.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = digits.getFontMetrics();
        float textY = cy - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(label, cx, textY, digits);
    }
}
