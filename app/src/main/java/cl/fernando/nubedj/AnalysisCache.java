package cl.fernando.nubedj;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Small persistent cache so BPM/waveform do not need to be recalculated on every load. */
public final class AnalysisCache {
    private static final int MAGIC = 0x4E444A33; // NDJ3
    private AnalysisCache() {}

    public static TrackAnalysis read(Context context, Uri uri) {
        File f = file(context, uri);
        if (!f.isFile()) return null;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)))) {
            if (in.readInt() != MAGIC) return null;
            long saved = in.readLong();
            if (System.currentTimeMillis() - saved > 45L * 24L * 60L * 60L * 1000L) return null;
            boolean hasBpm = in.readBoolean();
            Double bpm = hasBpm ? in.readDouble() : null;
            long duration = in.readLong();
            int n = in.readInt();
            if (n < 0 || n > 5000) return null;
            float[] peaks = new float[n];
            for (int i = 0; i < n; i++) peaks[i] = in.readFloat();
            return new TrackAnalysis(bpm, peaks, duration);
        } catch (Exception ignored) { return null; }
    }

    public static void write(Context context, Uri uri, TrackAnalysis a) {
        if (a == null) return;
        File dir = new File(context.getFilesDir(), "analysis-v3");
        if (!dir.exists() && !dir.mkdirs()) return;
        File f = file(context, uri);
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f)))) {
            out.writeInt(MAGIC);
            out.writeLong(System.currentTimeMillis());
            out.writeBoolean(a.bpm != null);
            if (a.bpm != null) out.writeDouble(a.bpm);
            out.writeLong(a.durationMs);
            out.writeInt(a.peaks.length);
            for (float p : a.peaks) out.writeFloat(p);
        } catch (Exception ignored) { }
    }

    private static File file(Context context, Uri uri) {
        File dir = new File(context.getFilesDir(), "analysis-v3");
        return new File(dir, sha256(uri == null ? "null" : uri.toString()) + ".bin");
    }

    private static String sha256(String s) {
        try {
            byte[] b = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < 16; i++) out.append(String.format(java.util.Locale.US, "%02x", b[i]));
            return out.toString();
        } catch (Exception e) { return Integer.toHexString(s.hashCode()); }
    }
}
