package cl.fernando.nubedj;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Background audio analysis. Playback never waits for these methods. */
public final class AudioAnalyzer {
    private static final int PEAKS = 1400;
    private static final int ENVELOPE_HZ = 100;
    private static final long BPM_ANALYSIS_US = 55_000_000L;
    private static final long BPM_SKIP_INTRO_US = 8_000_000L;

    private AudioAnalyzer() {}

    /** Fast pass: only decodes ~55 seconds and returns BPM. */
    public static Double analyzeBpm(Context context, Uri uri) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try {
            extractor.setDataSource(context, uri, null);
            TrackInfo ti = findAudio(extractor);
            extractor.selectTrack(ti.index);
            long startUs = ti.durationUs > BPM_SKIP_INTRO_US + 15_000_000L ? BPM_SKIP_INTRO_US : 0L;
            if (startUs > 0) extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            long endUs = Math.min(ti.durationUs > 0 ? ti.durationUs : startUs + BPM_ANALYSIS_US, startUs + BPM_ANALYSIS_US);
            int envelopeSize = (int)Math.max(1000, ((endUs - startUs) / 1_000_000.0) * ENVELOPE_HZ + 10);
            float[] envelope = new float[envelopeSize];

            codec = MediaCodec.createDecoderByType(ti.mime);
            codec.configure(ti.format, null, null, 0);
            codec.start();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone=false, outputDone=false;
            int pcmEncoding=android.media.AudioFormat.ENCODING_PCM_16BIT;

            while(!outputDone){
                if(!inputDone){
                    int ii=codec.dequeueInputBuffer(10_000);
                    if(ii>=0){
                        ByteBuffer input=codec.getInputBuffer(ii);
                        if(input!=null){
                            long pts=extractor.getSampleTime();
                            if(pts<0 || pts>endUs){
                                codec.queueInputBuffer(ii,0,0,Math.max(startUs,pts),MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputDone=true;
                            }else{
                                int size=extractor.readSampleData(input,0);
                                if(size<0){codec.queueInputBuffer(ii,0,0,Math.max(startUs,pts),MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true;}
                                else{codec.queueInputBuffer(ii,0,size,pts,0);extractor.advance();}
                            }
                        }
                    }
                }
                int oi=codec.dequeueOutputBuffer(info,10_000);
                if(oi==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                    MediaFormat out=codec.getOutputFormat();
                    if(out.containsKey(MediaFormat.KEY_PCM_ENCODING))pcmEncoding=out.getInteger(MediaFormat.KEY_PCM_ENCODING);
                }else if(oi>=0){
                    ByteBuffer out=codec.getOutputBuffer(oi);
                    if(out!=null&&info.size>0&&info.presentationTimeUs>=startUs){
                        out.position(info.offset);out.limit(info.offset+info.size);
                        float energy=rms(out.slice().order(ByteOrder.LITTLE_ENDIAN),pcmEncoding);
                        int e=(int)Math.min(envelope.length-1,Math.max(0,(info.presentationTimeUs-startUs)*ENVELOPE_HZ/1_000_000L));
                        envelope[e]=Math.max(envelope[e],energy);
                    }
                    outputDone=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                    codec.releaseOutputBuffer(oi,false);
                }
            }
            return BpmEstimator.estimate(envelope,ENVELOPE_HZ);
        } catch(RuntimeException e){throw new IOException("No se pudo analizar BPM",e);}
        finally{release(codec,extractor);}
    }

    /** Slower pass: builds a detailed full-track waveform, but does not spend time on BPM. */
    public static TrackAnalysis analyzeWaveform(Context context, Uri uri) throws IOException {
        MediaExtractor extractor=new MediaExtractor();MediaCodec codec=null;
        try{
            extractor.setDataSource(context,uri,null);TrackInfo ti=findAudio(extractor);extractor.selectTrack(ti.index);
            float[] peaks=new float[PEAKS];
            codec=MediaCodec.createDecoderByType(ti.mime);codec.configure(ti.format,null,null,0);codec.start();
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();boolean inputDone=false,outputDone=false;int pcmEncoding=android.media.AudioFormat.ENCODING_PCM_16BIT;
            long lastPts=0;
            while(!outputDone){
                if(!inputDone){int ii=codec.dequeueInputBuffer(10_000);if(ii>=0){ByteBuffer input=codec.getInputBuffer(ii);if(input!=null){int size=extractor.readSampleData(input,0);if(size<0){codec.queueInputBuffer(ii,0,0,lastPts,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true;}else{long pts=Math.max(0,extractor.getSampleTime());lastPts=pts;codec.queueInputBuffer(ii,0,size,pts,0);extractor.advance();}}}}
                int oi=codec.dequeueOutputBuffer(info,10_000);
                if(oi==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){MediaFormat out=codec.getOutputFormat();if(out.containsKey(MediaFormat.KEY_PCM_ENCODING))pcmEncoding=out.getInteger(MediaFormat.KEY_PCM_ENCODING);}
                else if(oi>=0){ByteBuffer out=codec.getOutputBuffer(oi);if(out!=null&&info.size>0){out.position(info.offset);out.limit(info.offset+info.size);float amp=peakAmplitude(out.slice().order(ByteOrder.LITTLE_ENDIAN),pcmEncoding);long duration=ti.durationUs>0?ti.durationUs:Math.max(lastPts,1);int p=(int)Math.min(PEAKS-1,Math.max(0,info.presentationTimeUs*(long)PEAKS/duration));peaks[p]=Math.max(peaks[p],amp);}outputDone=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;codec.releaseOutputBuffer(oi,false);}
            }
            normalize(peaks);fillGaps(peaks);return new TrackAnalysis(null,peaks,Math.max(0L,ti.durationUs/1000L));
        }catch(RuntimeException e){throw new IOException("No se pudo generar waveform",e);}finally{release(codec,extractor);}
    }

    /** Compatibility helper. */
    public static TrackAnalysis analyze(Context context, Uri uri) throws IOException {
        Double bpm=analyzeBpm(context,uri);TrackAnalysis wave=analyzeWaveform(context,uri);return new TrackAnalysis(bpm,wave.peaks,wave.durationMs);
    }

    private static TrackInfo findAudio(MediaExtractor extractor)throws IOException{
        for(int i=0;i<extractor.getTrackCount();i++){MediaFormat f=extractor.getTrackFormat(i);String mime=f.getString(MediaFormat.KEY_MIME);if(mime!=null&&mime.startsWith("audio/")){long d=f.containsKey(MediaFormat.KEY_DURATION)?f.getLong(MediaFormat.KEY_DURATION):0L;return new TrackInfo(i,f,mime,d);}}
        throw new IOException("No se encontró audio compatible");
    }

    private static float rms(ByteBuffer b,int encoding){double sum=0;int count=0;if(encoding==android.media.AudioFormat.ENCODING_PCM_FLOAT){while(b.remaining()>=4){float v=b.getFloat();if(Float.isFinite(v)){sum+=v*v;count++;}}}else if(encoding==android.media.AudioFormat.ENCODING_PCM_8BIT){while(b.hasRemaining()){double v=((b.get()&0xff)-128)/128.0;sum+=v*v;count++;}}else{while(b.remaining()>=2){double v=b.getShort()/32768.0;sum+=v*v;count++;}}return count==0?0f:(float)Math.min(1.0,Math.sqrt(sum/count)*2.5);}
    private static float peakAmplitude(ByteBuffer b,int encoding){float max=0f;if(encoding==android.media.AudioFormat.ENCODING_PCM_FLOAT){while(b.remaining()>=4){float v=Math.abs(b.getFloat());if(Float.isFinite(v))max=Math.max(max,Math.min(1f,v));}}else if(encoding==android.media.AudioFormat.ENCODING_PCM_8BIT){while(b.hasRemaining())max=Math.max(max,Math.abs((b.get()&0xff)-128)/128f);}else{while(b.remaining()>=2)max=Math.max(max,Math.abs(b.getShort()/32768f));}return Math.min(1f,max);}
    private static void normalize(float[] peaks){float max=0f;for(float p:peaks)max=Math.max(max,p);if(max<0.0001f)return;for(int i=0;i<peaks.length;i++){float n=peaks[i]/max;peaks[i]=(float)Math.pow(Math.min(1f,n),0.65);}}
    private static void fillGaps(float[] peaks){float last=0f;for(int i=0;i<peaks.length;i++){if(peaks[i]==0f)peaks[i]=last*0.94f;else last=peaks[i];}last=0f;for(int i=peaks.length-1;i>=0;i--){if(peaks[i]==0f)peaks[i]=last*0.94f;else last=Math.max(last*0.93f,peaks[i]);}}
    private static void release(MediaCodec codec,MediaExtractor extractor){if(codec!=null){try{codec.stop();}catch(Exception ignored){}try{codec.release();}catch(Exception ignored){}}try{extractor.release();}catch(Exception ignored){}}
    private static final class TrackInfo{final int index;final MediaFormat format;final String mime;final long durationUs;TrackInfo(int i,MediaFormat f,String m,long d){index=i;format=f;mime=m;durationUs=d;}}
}
