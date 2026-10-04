package cl.fernando.nubedj;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/** Large touch waveform: drag to scrub, tap overview to jump, pinch to zoom. */
public final class WaveformView extends View {
    private final Paint wave = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overview = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint playhead = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cue = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final DjDeck deck;
    private final int accent;
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    private boolean scrubbing;
    private long windowMs = 18_000L;

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
        cue.setStrokeWidth(dp(2));
        cue.setColor(0xFFFFD451);
        text.setColor(0x99FFFFFF);
        text.setTextSize(dp(8));
        text.setTypeface(Typeface.DEFAULT_BOLD);
        setBackgroundColor(0xFF090F18);
        setClickable(true);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                if (factor > 0f) windowMs = clampWindow((long)(windowMs / factor));
                invalidate();
                return true;
            }
        });
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDoubleTap(MotionEvent e) {
                windowMs = 18_000L;
                invalidate();
                return true;
            }
            @Override public boolean onDown(MotionEvent e) { return true; }
        });
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        float overviewTop = h * 0.83f;
        float mainHeight = overviewTop - dp(2);
        for (int i = 1; i < 8; i++) {
            float x = i * w / 8f;
            c.drawLine(x, 0, x, mainHeight, grid);
        }

        float[] peaks = deck.getPeaks();
        long duration = Math.max(deck.getDuration(), deck.getAnalysisDuration());
        if (peaks.length < 8 || duration <= 0) {
            c.drawText(deck.isAnalyzing() ? "ANALIZANDO WAVEFORM · YA PUEDES REPRODUCIR" : "CARGA UNA PISTA", dp(10), mainHeight / 2f, text);
            c.drawLine(0, mainHeight / 2f, w, mainHeight / 2f, grid);
            return;
        }

        long pos = deck.getCurrentPosition();
        long useWindow = Math.min(duration, clampWindow(windowMs));
        long start = pos - useWindow / 2;
        int bars = Math.max(80, Math.round(w / Math.max(1.6f, dp(2.2f))));
        float center = mainHeight / 2f;
        for (int i = 0; i < bars; i++) {
            long t = start + (long) (useWindow * i / (double) Math.max(1, bars - 1));
            int idx = (int) Math.floor((t / (double) duration) * peaks.length);
            float amp = (idx < 0 || idx >= peaks.length) ? 0.02f : Math.max(0.02f, peaks[idx]);
            float x = i * w / (float) Math.max(1, bars - 1);
            float half = amp * (mainHeight * 0.46f);
            wave.setAlpha(t < pos ? 255 : 155);
            c.drawLine(x, center - half, x, center + half, wave);
        }

        // Cue marker when it falls inside the visible zoom window.
        long cueMs = deck.getCuePositionMs();
        if (cueMs >= start && cueMs <= start + useWindow) {
            float cueX = (cueMs - start) / (float) useWindow * w;
            c.drawLine(cueX, 0, cueX, mainHeight, cue);
            c.drawText("CUE", Math.max(dp(3), cueX + dp(3)), dp(11), text);
        }

        c.drawLine(w / 2f, 0, w / 2f, mainHeight, playhead);
        text.setTextAlign(Paint.Align.RIGHT);
        c.drawText(String.format(java.util.Locale.US, "ZOOM %.0fs", useWindow / 1000f), w - dp(5), dp(11), text);
        text.setTextAlign(Paint.Align.LEFT);

        // Overview strip: entire song, always absolute seek.
        float oy = overviewTop + (h - overviewTop) / 2f;
        float oh = (h - overviewTop) * 0.40f;
        int overviewBars = Math.min(peaks.length, Math.max(90, w / Math.max(1, Math.round(dp(1.5f)))));
        overview.setAlpha(155);
        for (int i = 0; i < overviewBars; i++) {
            int idx = (int) ((long) i * peaks.length / Math.max(1, overviewBars));
            idx = Math.min(peaks.length - 1, idx);
            float amp = Math.max(0.04f, peaks[idx]);
            float x = i * w / (float) Math.max(1, overviewBars - 1);
            c.drawLine(x, oy - amp * oh, x, oy + amp * oh, overview);
        }
        float absoluteX = duration <= 0 ? 0 : (pos / (float) duration) * w;
        c.drawLine(absoluteX, overviewTop, absoluteX, h, playhead);
        float cueOverviewX = duration <= 0 ? 0 : (cueMs / (float) duration) * w;
        c.drawLine(cueOverviewX, overviewTop, cueOverviewX, h, cue);
        c.drawLine(0, overviewTop, w, overviewTop, grid);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        if (scaleDetector.isInProgress()) return true;
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
        float overviewTop = getHeight() * 0.83f;
        long target;
        if (y >= overviewTop) {
            target = (long) (duration * (x / getWidth()));
        } else {
            long useWindow = Math.min(duration, clampWindow(windowMs));
            long pos = deck.getCurrentPosition();
            long start = pos - useWindow / 2;
            target = start + (long) (useWindow * (x / getWidth()));
        }
        deck.seekTo(Math.max(0L, Math.min(duration, target)));
        invalidate();
    }

    private static long clampWindow(long ms) { return Math.max(6_000L, Math.min(64_000L, ms)); }
    @Override public boolean performClick() { super.performClick(); return true; }
    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
