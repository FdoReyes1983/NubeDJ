package cl.fernando.nubedj;

import java.util.Locale;

public final class TimeFormat {
    private TimeFormat() {}
    public static String ms(long value) {
        if (value < 0) value = 0;
        long total = value / 1000;
        long minutes = total / 60;
        long seconds = total % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }
}
