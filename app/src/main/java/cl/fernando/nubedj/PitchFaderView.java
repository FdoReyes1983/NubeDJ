package cl.fernando.nubedj;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/** Vertical DJ-style pitch fader. Range is symmetrical around zero. */
public final class PitchFaderView extends View {
    public interface Listener { void onPitchChanged(float percent); }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int accent;
    private float valuePercent;
    private float rangePercent = 8f;
    private Listener listener;
    private long lastTap;

    public PitchFaderView(Context context, int accent) {
        super(context);
        this.accent = accent;
        setClickable(true);
    }

    public void setListener(Listener listener) { this.listener = listener; }
    public void setRangePercent(float range) { rangePercent = Math.max(2f, Math.min(50f, range)); invalidate(); }
    public float getRangePercent() { return rangePercent; }
    public float getValuePercent() { return valuePercent; }
    public void setValuePercent(float value) { valuePercent = clamp(value, -rangePercent, rangePercent); invalidate(); }
    public void reset() { updateValue(0f, true); }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float cx = w * 0.50f;
        float top = dp(15), bottom = h - dp(17);

        p.setStrokeWidth(dp(2));
        p.setColor(0xFF2E3742);
        c.drawLine(cx, top, cx, bottom, p);

        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(dp(8));
        p.setTextAlign(Paint.Align.LEFT);
        for (int i = -4; i <= 4; i++) {
            float y = top + (i + 4) * (bottom - top) / 8f;
            boolean center = i == 0;
            p.setStrokeWidth(center ? dp(2) : dp(1));
            p.setColor(center ? 0xCCFFFFFF : 0x668FA2B5);
            float len = center ? dp(10) : dp(6);
            c.drawLine(cx - len, y, cx + len, y, p);
        }

        float normalized = (valuePercent + rangePercent) / (2f * rangePercent);
        float knobY = bottom - normalized * (bottom - top);
        p.setColor(0xFF111820);
        c.drawRoundRect(cx - dp(17), knobY - dp(7), cx + dp(17), knobY + dp(7), dp(5), dp(5), p);
        p.setColor(accent);
        c.drawRoundRect(cx - dp(13), knobY - dp(2), cx + dp(13), knobY + dp(2), dp(2), dp(2), p);

        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(dp(8));
        p.setColor(0xFFB6C3D2);
        c.drawText(String.format(java.util.Locale.US, "%+.1f%%", valuePercent), cx, h - dp(3), p);
        p.setTextSize(dp(7));
        p.setColor(0xFF7F91A5);
        c.drawText("PITCH", cx, dp(9), p);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                long now = android.os.SystemClock.uptimeMillis();
                if (now - lastTap < 280) {
                    lastTap = 0;
                    reset();
                    performClick();
                    return true;
                }
                lastTap = now;
                updateFromY(e.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                updateFromY(e.getY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                updateFromY(e.getY());
                getParent().requestDisallowInterceptTouchEvent(false);
                performClick();
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    private void updateFromY(float y) {
        float top = dp(15), bottom = getHeight() - dp(17);
        if (bottom <= top) return;
        float n = 1f - (clamp(y, top, bottom) - top) / (bottom - top);
        float value = (n * 2f - 1f) * rangePercent;
        // Small magnetic center zone, like a physical zero detent.
        if (Math.abs(value) < rangePercent * 0.035f) value = 0f;
        updateValue(value, true);
    }

    private void updateValue(float value, boolean notify) {
        valuePercent = clamp(value, -rangePercent, rangePercent);
        invalidate();
        if (notify && listener != null) listener.onPitchChanged(valuePercent);
    }

    @Override public boolean performClick() { super.performClick(); return true; }
    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
}
