package cl.fernando.nubedj;

/** Lightweight autocorrelation BPM estimator for a rectified onset envelope. */
public final class BpmEstimator {
    private BpmEstimator() {}

    public static Double estimate(float[] amplitude, double samplesPerSecond) {
        if (amplitude == null || amplitude.length < samplesPerSecond * 8 || samplesPerSecond <= 0) return null;
        float[] onset = new float[amplitude.length];
        float smooth = amplitude[0];
        for (int i = 1; i < amplitude.length; i++) {
            smooth = 0.93f * smooth + 0.07f * amplitude[i];
            onset[i] = Math.max(0f, amplitude[i] - smooth);
        }
        for (int i = 2; i < onset.length - 2; i++) {
            onset[i] = (onset[i - 2] + 2 * onset[i - 1] + 3 * onset[i] + 2 * onset[i + 1] + onset[i + 2]) / 9f;
        }

        final int minBpm = 70, maxBpm = 180;
        int minLag = Math.max(1, (int) Math.floor(samplesPerSecond * 60.0 / maxBpm));
        int maxLag = Math.max(minLag + 1, (int) Math.ceil(samplesPerSecond * 60.0 / minBpm));
        int bestLag = -1;
        double bestScore = 0;
        for (int lag = minLag; lag <= maxLag; lag++) {
            double bpm = samplesPerSecond * 60.0 / lag;
            if (bpm < minBpm || bpm > maxBpm) continue;
            double score = correlation(onset, lag);
            if (lag * 2 < onset.length) score += 0.45 * correlation(onset, lag * 2);
            if (lag * 3 < onset.length) score += 0.20 * correlation(onset, lag * 3);
            if (bpm >= 82 && bpm <= 150) score *= 1.04;
            if (score > bestScore) { bestScore = score; bestLag = lag; }
        }
        if (bestLag <= 0 || bestScore <= 0) return null;

        // Parabolic interpolation of the autocorrelation peak gives sub-lag precision.
        double delta = 0;
        if (bestLag > minLag && bestLag < maxLag) {
            double y1 = combinedScore(onset, bestLag - 1);
            double y2 = combinedScore(onset, bestLag);
            double y3 = combinedScore(onset, bestLag + 1);
            double den = y1 - 2 * y2 + y3;
            if (Math.abs(den) > 1e-9) delta = 0.5 * (y1 - y3) / den;
            delta = Math.max(-0.5, Math.min(0.5, delta));
        }
        double bpm = samplesPerSecond * 60.0 / (bestLag + delta);
        return Math.round(bpm * 10.0) / 10.0;
    }

    private static double combinedScore(float[] x, int lag) {
        double score = correlation(x, lag);
        if (lag * 2 < x.length) score += 0.45 * correlation(x, lag * 2);
        if (lag * 3 < x.length) score += 0.20 * correlation(x, lag * 3);
        return score;
    }

    private static double correlation(float[] x, int lag) {
        if (lag <= 0 || lag >= x.length) return 0;
        double sum = 0, a2 = 0, b2 = 0;
        for (int i = lag; i < x.length; i++) {
            double a = x[i], b = x[i - lag];
            sum += a * b;
            a2 += a * a;
            b2 += b * b;
        }
        return a2 == 0 || b2 == 0 ? 0 : sum / Math.sqrt(a2 * b2);
    }
}
