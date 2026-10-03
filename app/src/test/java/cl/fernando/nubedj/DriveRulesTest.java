package cl.fernando.nubedj;

import org.junit.Test;
import static org.junit.Assert.*;

public class DriveRulesTest {
    @Test public void identifiesAudioAndEscapesSearch() {
        assertTrue(DriveRules.isAudio("mix.MP3", "application/octet-stream"));
        assertFalse(DriveRules.isAudio("video.mkv", "video/x-matroska"));
        assertTrue(DriveRules.query("root", false, "DJ's").contains("DJ\\'s"));
    }
}
