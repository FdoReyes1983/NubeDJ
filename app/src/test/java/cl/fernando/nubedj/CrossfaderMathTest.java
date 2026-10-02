package cl.fernando.nubedj;

import org.junit.Test;
import static org.junit.Assert.*;

public class CrossfaderMathTest {
    @Test public void endpointsAndCenterAreEqualPower() {
        float[] left = CrossfaderMath.equalPower(0f);
        float[] center = CrossfaderMath.equalPower(0.5f);
        float[] right = CrossfaderMath.equalPower(1f);
        assertEquals(1f, left[0], 0.001f);
        assertEquals(0f, left[1], 0.001f);
        assertEquals(0.7071f, center[0], 0.002f);
        assertEquals(0.7071f, center[1], 0.002f);
        assertEquals(0f, right[0], 0.001f);
        assertEquals(1f, right[1], 0.001f);
    }
}
