package cl.fernando.nubedj;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Decodes audio off the UI thread to build a waveform and estimate BPM. */
public final class AudioAnalyzer {
    private static final int PEAKS = 900;
    private static final int ENVELOPE_HZ = 50;
    private static final long BPM_WINDOW_US = 120_000_000L;

    private AudioAnalyzer() {}

    public static TrackAnalysis analyze(Context context, Uri uri) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try {
            extractor.setDataSource(context, uri, null);
            int audioTrack = -1;
            MediaFormat inputFormat = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) { audioTrack = i; inputFormat = f; break; }
            }
            if (audioTrack < 0 || inputFormat == null) throw new IOException("No se encontró audio compatible");
            extractor.selectTrack(audioTrack);
            String mime = inputFormat.getString(MediaFormat.KEY_MIME);
            if (mime == null) throw new IOException("Formato de audio desconocido");
            long durationUs = inputFormat.containsKey(MediaFormat.KEY_DURATION) ? inputFormat.getLong(MediaFormat.KEY_DURATION) : 0L;
            float[] peaks = new float[PEAKS];
            int envelopeSize = (int) Math.max(500, Math.min(BPM_WINDOW_US, durationUs > 0 ? durationUs : BPM_WINDOW_US) / 1_000_000.0 * ENVELOPE_HZ + 2);
            float[] envelope = new float[envelopeSize];

            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(inputFormat, null, null, 0);
            codec.start();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false, outputDone = false;
            int pcmEncoding = android.media.AudioFormat.ENCODING_PCM_16BIT;

            while (!outputDone) {
                if (!inputDone) {
                    int inputIndex = codec.dequeueInputBuffer(10_000);
                    if (inputIndex >= 0) {
                        ByteBuffer input = codec.getInputBuffer(inputIndex);
                        if (input != null) {
                            int size = extractor.readSampleData(input, 0);
                            if (size < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputDone = true;
                            } else {
                                long pts = extractor.getSampleTime();
                                codec.queueInputBuffer(inputIndex, 0, size, Math.max(0, pts), 0);
                                extractor.advance();
                            }
                        }
                    }
                }

                int outputIndex = codec.dequeueOutputBuffer(info, 10_000);
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat out = codec.getOutputFormat();
                    if (out.containsKey(MediaFormat.KEY_PCM_ENCODING)) pcmEncoding = out.getInteger(MediaFormat.KEY_PCM_ENCODING);
                } else if (outputIndex >= 0) {
                    ByteBuffer out = codec.getOutputBuffer(outputIndex);
                    if (out != null && info.size > 0) {
                        out.position(info.offset);
                        out.limit(info.offset + info.size);
                        float amp = amplitude(out.slice().order(ByteOrder.LITTLE_ENDIAN), pcmEncoding);
                        if (durationUs > 0) {
                            int p = (int) Math.min(PEAKS - 1, Math.max(0, info.presentationTimeUs * (long) PEAKS / durationUs));
                            peaks[p] = Math.max(peaks[p], amp);
                        }
                        if (info.presentationTimeUs <= BPM_WINDOW_US) {
                            int e = (int) Math.min(envelope.length - 1, Math.max(0, info.presentationTimeUs * ENVELOPE_HZ / 1_000_000L));
                            envelope[e] = Math.max(envelope[e], amp);
                        }
                    }
                    outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    codec.releaseOutputBuffer(outputIndex, false);
                }
            }

            normalize(peaks);
            fillGaps(peaks);
            Double bpm = BpmEstimator.estimate(envelope, ENVELOPE_HZ);
            return new TrackAnalysis(bpm, peaks, Math.max(0L, durationUs / 1000L));
        } catch (RuntimeException e) {
            throw new IOException("No se pudo analizar esta pista", e);
        } finally {
            if (codec != null) {
                try { codec.stop(); } catch (Exception ignored) { }
                try { codec.release(); } catch (Exception ignored) { }
            }
            try { extractor.release(); } catch (Exception ignored) { }
        }
    }

    private static float amplitude(ByteBuffer b, int encoding) {
        double sum = 0;
        int count = 0;
        if (encoding == android.media.AudioFormat.ENCODING_PCM_FLOAT) {
            while (b.remaining() >= 4) {
                float v = Math.abs(b.getFloat());
                if (Float.isFinite(v)) { sum += Math.min(1f, v); count++; }
            }
        } else if (encoding == android.media.AudioFormat.ENCODING_PCM_8BIT) {
            while (b.hasRemaining()) { sum += Math.abs((b.get() & 0xff) - 128) / 128.0; count++; }
        } else {
            while (b.remaining() >= 2) { sum += Math.abs(b.getShort() / 32768.0); count++; }
        }
        return count == 0 ? 0f : (float) Math.min(1.0, (sum / count) * 3.2);
    }

    private static void normalize(float[] peaks) {
        float max = 0f;
        for (float p : peaks) max = Math.max(max, p);
        if (max < 0.0001f) return;
        for (int i = 0; i < peaks.length; i++) peaks[i] = Math.min(1f, peaks[i] / max);
    }

    private static void fillGaps(float[] peaks) {
        float last = 0f;
        for (int i = 0; i < peaks.length; i++) {
            if (peaks[i] == 0f) peaks[i] = last * 0.93f;
            else last = peaks[i];
        }
        last = 0f;
        for (int i = peaks.length - 1; i >= 0; i--) {
            if (peaks[i] == 0f) peaks[i] = last * 0.93f;
            else last = Math.max(last * 0.92f, peaks[i]);
        }
    }
}
