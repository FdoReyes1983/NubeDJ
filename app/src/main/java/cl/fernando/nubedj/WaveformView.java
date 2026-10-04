package cl.fernando.nubedj;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

public final class WaveformView extends View {
    private final Paint wave = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overview = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint playhead = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final DjDeck deck;
    private final int accent;
    private boolean scrubbing;

    public WaveformView(Context context, DjDeck deck, int accent) {
        super(context);
        this.deck = deck;
        this.accent = accent;
        wave.setStrokeWidth(dp(2));
        wave.setStrokeCap(Paint.Cap.ROUND);
        wave.setColor(accent);
        overview.setStrokeWidth(dp(1));
        overview.setStrokeCap(Paint.Cap.ROUND);
        overview.setColor(accent);
        grid.setStrokeWidth(dp(1));
        grid.setColor(0x2AFFFFFF);
        playhead.setStrokeWidth(dp(2));
        playhead.setColor(0xFFFFFFFF);
        text.setColor(0x99FFFFFF);
        text.setTextSize(dp(9));
        text.setTypeface(Typeface.DEFAULT_BOLD);
        setBackgroundColor(0xFF090F18);
        setClickable(true);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        float overviewTop = h * 0.80f;
        float mainHeight = overviewTop - dp(2);
        for (int i = 1; i < 8; i++) {
            float x = i * w / 8f;
            c.drawLine(x, 0, x, mainHeight, grid);
        }

        float[] peaks = deck.getPeaks();
        long duration = Math.max(deck.getDuration(), deck.getAnalysisDuration());
        if (peaks.length < 8 || duration <= 0) {
            c.drawText(deck.isAnalyzing() ? "ANALIZANDO PISTA…" : "CARGA UNA PISTA PARA VER EL WAVEFORM", dp(10), mainHeight / 2f, text);
            c.drawLine(0, mainHeight / 2f, w, mainHeight / 2f, grid);
            return;
        }

        long pos = deck.getCurrentPosition();
        long windowMs = Math.min(32_000L, Math.max(12_000L, duration / 8));
        long start = pos - windowMs / 2;
        int bars = Math.max(40, Math.round(w / Math.max(2f, dp(3))));
        float center = mainHeight / 2f;
        for (int i = 0; i < bars; i++) {
            long t = start + (long) (windowMs * i / (double) Math.max(1, bars - 1));
            int idx = (int) Math.floor((t / (double) duration) * peaks.length);
            float amp = (idx < 0 || idx >= peaks.length) ? 0.03f : Math.max(0.03f, peaks[idx]);
            float x = i * w / (float) Math.max(1, bars - 1);
            float half = amp * (mainHeight * 0.42f);
            wave.setAlpha(t < pos ? 255 : 150);
            c.drawLine(x, center - half, x, center + half, wave);
        }
        c.drawLine(w / 2f, 0, w / 2f, mainHeight, playhead);

        // Vista general de toda la pista. Tocar aquí salta a cualquier punto.
        float oy = overviewTop + (h - overviewTop) / 2f;
        float oh = (h - overviewTop) * 0.40f;
        int overviewBars = Math.min(peaks.length, Math.max(60, w / Math.max(1, Math.round(dp(2)))));
        overview.setAlpha(145);
        for (int i = 0; i < overviewBars; i++) {
            int idx = (int) ((long) i * peaks.length / Math.max(1, overviewBars));
            idx = Math.min(peaks.length - 1, idx);
            float amp = Math.max(0.05f, peaks[idx]);
            float x = i * w / (float) Math.max(1, overviewBars - 1);
            c.drawLine(x, oy - amp * oh, x, oy + amp * oh, overview);
        }
        float absoluteX = duration <= 0 ? 0 : (pos / (float) duration) * w;
        c.drawLine(absoluteX, overviewTop, absoluteX, h, playhead);
        c.drawLine(0, overviewTop, w, overviewTop, grid);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (deck.getDuration() <= 0 && deck.getAnalysisDuration() <= 0) return true;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                scrubbing = true;
                seekFromTouch(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (scrubbing) seekFromTouch(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (scrubbing) seekFromTouch(event.getX(), event.getY());
                scrubbing = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                performClick();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void seekFromTouch(float x, float y) {
        long duration = Math.max(deck.getDuration(), deck.getAnalysisDuration());
        if (duration <= 0 || getWidth() <= 0) return;
        x = Math.max(0f, Math.min(getWidth(), x));
        float overviewTop = getHeight() * 0.80f;
        long target;
        if (y >= overviewTop) {
            target = (long) (duration * (x / getWidth()));
        } else {
            long pos = deck.getCurrentPosition();
            long windowMs = Math.min(32_000L, Math.max(12_000L, duration / 8));
            long start = pos - windowMs / 2;
            target = start + (long) (windowMs * (x / getWidth()));
        }
        deck.seekTo(Math.max(0L, Math.min(duration, target)));
        invalidate();
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
