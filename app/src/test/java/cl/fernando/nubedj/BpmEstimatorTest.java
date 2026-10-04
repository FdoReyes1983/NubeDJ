package cl.fernando.nubedj;

import org.junit.Test;
import static org.junit.Assert.*;

public class BpmEstimatorTest {
    @Test public void detectsCommonDjTempos() {
        for (double expected : new double[]{77, 100, 120, 128, 140, 150, 172}) {
            Double bpm = BpmEstimator.estimate(pulse(expected, 100, 60), 100);
            assertNotNull("No BPM for " + expected, bpm);
            assertEquals(expected, bpm, 1.8);
        }
    }

    private static float[] pulse(double bpm, int hz, int seconds) {
        float[] e = new float[hz * seconds];
        double period = 60.0 / bpm;
        for (int i = 0; i < e.length; i++) {
            double phase = (i / (double) hz) % period;
            if (phase < 0.035) e[i] = 1f;
            else if (phase < 0.07) e[i] = .35f;
            else e[i] = .02f;
        }
        return e;
    }
}
