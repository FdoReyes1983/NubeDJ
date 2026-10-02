package cl.fernando.nubedj;

import org.junit.Test;
import static org.junit.Assert.*;

public class TapTempoTest {
    @Test public void calculates120BpmFrom500msTaps() {
        TapTempo t = new TapTempo();
        assertNull(t.tapAt(1000));
        assertEquals(120.0, t.tapAt(1500), 0.01);
        assertEquals(120.0, t.tapAt(2000), 0.01);
        assertEquals(120.0, t.tapAt(2500), 0.01);
    }
}
