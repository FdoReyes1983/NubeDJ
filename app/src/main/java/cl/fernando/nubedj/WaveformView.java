package cl.fernando.nubedj;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

public final class WaveformView extends View {
    private final Paint wave = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint playhead = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private DjDeck deck;
    private int accent;

    public WaveformView(Context context, DjDeck deck, int accent) {
        super(context);
        this.deck = deck;
        this.accent = accent;
        wave.setStrokeWidth(dp(2)); wave.setStrokeCap(Paint.Cap.ROUND); wave.setColor(accent);
        grid.setStrokeWidth(dp(1)); grid.setColor(0x2AFFFFFF);
        playhead.setStrokeWidth(dp(2)); playhead.setColor(0xFFFFFFFF);
        text.setColor(0x99FFFFFF); text.setTextSize(dp(10)); text.setTypeface(Typeface.DEFAULT_BOLD);
        setBackgroundColor(0xFF090F18);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        for (int i = 1; i < 8; i++) {
            float x = i * w / 8f; c.drawLine(x, 0, x, h, grid);
        }
        float[] peaks = deck.getPeaks();
        long duration = Math.max(deck.getDuration(), deck.getAnalysisDuration());
        if (peaks.length < 8 || duration <= 0) {
            c.drawText(deck.isAnalyzing() ? "ANALIZANDO PISTA…" : "CARGA UNA PISTA PARA VER EL WAVEFORM", dp(10), h / 2f, text);
            c.drawLine(0, h / 2f, w, h / 2f, grid);
            return;
        }
        long pos = deck.getCurrentPosition();
        long windowMs = Math.min(32_000L, Math.max(12_000L, duration / 8));
        long start = pos - windowMs / 2;
        int bars = Math.max(40, Math.round(w / Math.max(2f, dp(3))));
        float center = h / 2f;
        for (int i = 0; i < bars; i++) {
            long t = start + (long) (windowMs * i / (double) (bars - 1));
            int idx = (int) Math.floor((t / (double) duration) * peaks.length);
            float amp = (idx < 0 || idx >= peaks.length) ? 0.03f : Math.max(0.03f, peaks[idx]);
            float x = i * w / (float) (bars - 1);
            float half = amp * (h * 0.42f);
            wave.setAlpha(t < pos ? 255 : 150);
            c.drawLine(x, center - half, x, center + half, wave);
        }
        c.drawLine(w / 2f, 0, w / 2f, h, playhead);
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
