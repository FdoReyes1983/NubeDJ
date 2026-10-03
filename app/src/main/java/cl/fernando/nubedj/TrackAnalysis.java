package cl.fernando.nubedj;

public final class TrackAnalysis {
    public final Double bpm;
    public final float[] peaks;
    public final long durationMs;

    public TrackAnalysis(Double bpm, float[] peaks, long durationMs) {
        this.bpm = bpm;
        this.peaks = peaks == null ? new float[0] : peaks;
        this.durationMs = Math.max(0L, durationMs);
    }
}
