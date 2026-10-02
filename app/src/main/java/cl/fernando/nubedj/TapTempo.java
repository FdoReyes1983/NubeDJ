package cl.fernando.nubedj;

import java.util.ArrayDeque;
import java.util.Deque;

public final class TapTempo {
    private final Deque<Long> taps = new ArrayDeque<>();
    private static final int MAX_TAPS = 6;
    private static final long RESET_GAP_MS = 2500;

    public Double tap() { return tapAt(System.currentTimeMillis()); }

    public Double tapAt(long nowMs) {
        if (!taps.isEmpty() && nowMs - taps.getLast() > RESET_GAP_MS) taps.clear();
        taps.addLast(nowMs);
        while (taps.size() > MAX_TAPS) taps.removeFirst();
        if (taps.size() < 2) return null;

        Long previous = null;
        double sum = 0;
        int intervals = 0;
        for (Long value : taps) {
            if (previous != null) {
                long delta = value - previous;
                if (delta > 0) {
                    sum += delta;
                    intervals++;
                }
            }
            previous = value;
        }
        if (intervals == 0) return null;
        return 60000.0 / (sum / intervals);
    }

    public void reset() { taps.clear(); }
}
