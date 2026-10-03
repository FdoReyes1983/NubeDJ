package cl.fernando.nubedj;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public final class VuMeterView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float level;
    public VuMeterView(Context context) { super(context); setBackgroundColor(0xFF0A1018); }
    public void setLevel(float v) { level = Math.max(0f, Math.min(1f, v)); invalidate(); }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int segments = 14; float gap = dp(2); float sh = (getHeight() - gap * (segments - 1)) / segments;
        for (int i = 0; i < segments; i++) {
            float threshold = (i + 1f) / segments;
            if (threshold > 0.82f) paint.setColor(0xFFE95B62);
            else if (threshold > 0.62f) paint.setColor(0xFFF1CB55);
            else paint.setColor(0xFF55D89B);
            paint.setAlpha(level >= threshold ? 255 : 45);
            float bottom = getHeight() - i * (sh + gap);
            c.drawRoundRect(dp(2), bottom - sh, getWidth() - dp(2), bottom, dp(2), dp(2), paint);
        }
    }
    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
