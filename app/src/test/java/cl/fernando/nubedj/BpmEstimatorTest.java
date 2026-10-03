package cl.fernando.nubedj;

import org.junit.Test;
import static org.junit.Assert.*;

public class BpmEstimatorTest {
    @Test public void detects120BpmPulseTrain() {
        float[] e = new float[50 * 45];
        for (int i = 0; i < e.length; i += 25) { e[i] = 1f; if (i + 1 < e.length) e[i + 1] = .5f; }
        Double bpm = BpmEstimator.estimate(e, 50);
        assertNotNull(bpm);
        assertEquals(120.0, bpm, 1.5);
    }
}
