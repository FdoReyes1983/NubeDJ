package cl.fernando.nubedj;

import android.content.Context;
import android.media.audiofx.Equalizer;
import android.net.Uri;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.common.util.UnstableApi;

@UnstableApi
public final class DjDeck {
    public interface Listener { void onStateChanged(); }

    private final ExoPlayer player;
    private final TapTempo tapTempo = new TapTempo();
    private Listener listener;
    private String trackName = "Sin pista";
    private String status = "LISTO";
    private long cuePositionMs = 0L;
    private float deckVolume = 1f, crossfadeGain = 1f, tempo = 1f;
    private Double bpm;
    private float[] peaks = new float[0];
    private long analysisDurationMs;
    private boolean analyzing;
    private Equalizer equalizer;
    private float lowDb, midDb, highDb;
    private boolean cueHeld;
    private boolean jogWasPlaying;

    public DjDeck(Context context) {
        player = new ExoPlayer.Builder(context).build();
        AudioAttributes attrs = new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build();
        player.setAudioAttributes(attrs, false);
        player.setHandleAudioBecomingNoisy(false);
        player.setWakeMode(C.WAKE_MODE_LOCAL);
        player.setRepeatMode(Player.REPEAT_MODE_OFF);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int playbackState) { notifyListener(); }
            @Override public void onIsPlayingChanged(boolean isPlaying) { notifyListener(); }
            @Override public void onAudioSessionIdChanged(int audioSessionId) { attachEqualizer(audioSessionId); }
        });
    }

    public void setListener(Listener listener) { this.listener = listener; }

    public void load(Uri uri, String name) {
        trackName = name == null || name.trim().isEmpty() ? "Pista" : name;
        cuePositionMs = 0L; tapTempo.reset(); bpm = null; peaks = new float[0]; analysisDurationMs = 0L;
        analyzing = true; status = "ANALIZANDO BPM Y WAVEFORM";
        player.setMediaItem(MediaItem.fromUri(uri)); player.prepare(); notifyListener();
    }

    public void setAnalysis(TrackAnalysis analysis) {
        analyzing = false;
        if (analysis != null) {
            peaks = analysis.peaks;
            analysisDurationMs = analysis.durationMs;
            if (analysis.bpm != null) bpm = analysis.bpm;
            status = analysis.bpm == null ? "WAVEFORM LISTO · BPM NO DETECTADO" : "ANÁLISIS LISTO";
        } else status = "NO SE PUDO ANALIZAR";
        notifyListener();
    }

    public void setStatus(String text) { status = text == null ? "" : text; notifyListener(); }
    public void togglePlay() { if (player.isPlaying()) player.pause(); else player.play(); }
    public void setCue() { cuePositionMs = Math.max(0L, player.getCurrentPosition()); status = "CUE " + TimeFormat.ms(cuePositionMs); notifyListener(); }
    public void cue() { player.pause(); player.seekTo(cuePositionMs); status = "CUE"; notifyListener(); }
    public void cuePressStart() {
        cueHeld = true;
        player.pause();
        player.seekTo(cuePositionMs);
        player.play();
        status = "CUE PREVIEW";
        notifyListener();
    }
    public void cuePressEnd() {
        if (!cueHeld) return;
        cueHeld = false;
        player.pause();
        player.seekTo(cuePositionMs);
        status = "CUE";
        notifyListener();
    }
    public void seekTo(long ms) {
        long duration = getDuration();
        long target = duration > 0 ? Math.min(duration, Math.max(0L, ms)) : Math.max(0L, ms);
        player.seekTo(target);
        notifyListener();
    }
    public void beginJog() {
        jogWasPlaying = player.isPlaying();
        status = "JOG";
        notifyListener();
    }
    public void scrubBy(long deltaMs) {
        seekTo(getCurrentPosition() + deltaMs);
        status = "SCRUB";
    }
    public void endJog() {
        if (jogWasPlaying && !player.isPlaying()) player.play();
        status = jogWasPlaying ? "PLAY" : "PAUSA";
        notifyListener();
    }

    public void setDeckVolume(float value01) { deckVolume = clamp(value01, 0f, 1f); applyVolume(); }
    public void setCrossfadeGain(float value01) { crossfadeGain = clamp(value01, 0f, 1f); applyVolume(); }
    private void applyVolume() { player.setVolume(deckVolume * crossfadeGain); }

    public void setTempo(float speed) { tempo = clamp(speed, 0.75f, 1.25f); player.setPlaybackParameters(new PlaybackParameters(tempo)); notifyListener(); }
    public Double tapBpm() { bpm = tapTempo.tap(); status = bpm == null ? "TAP…" : "BPM AJUSTADO"; notifyListener(); return bpm; }

    public boolean syncTo(DjDeck master) {
        if (master.bpm == null || bpm == null || bpm <= 0) { status = "FALTA BPM PARA SYNC"; notifyListener(); return false; }
        setTempo((float) (master.bpm / bpm)); status = "SYNC"; notifyListener(); return true;
    }

    public void setEq(float lowDb,float midDb,float highDb){this.lowDb=lowDb;this.midDb=midDb;this.highDb=highDb;applyEq();}
    private void attachEqualizer(int id){
        if(id<=0)return;
        try{if(equalizer!=null)equalizer.release();equalizer=new Equalizer(0,id);equalizer.setEnabled(true);applyEq();}catch(Exception ignored){equalizer=null;}
    }
    private void applyEq(){
        if(equalizer==null)return;try{
            setNearestBand(100,lowDb);setNearestBand(1000,midDb);setNearestBand(10000,highDb);
        }catch(Exception ignored){}
    }
    private void setNearestBand(int hz,float db){
        short bands=equalizer.getNumberOfBands(),best=0;long bestDistance=Long.MAX_VALUE;
        for(short b=0;b<bands;b++){long center=equalizer.getCenterFreq(b)/1000L,dist=Math.abs(center-hz);if(dist<bestDistance){bestDistance=dist;best=b;}}
        short[] range=equalizer.getBandLevelRange();int mb=Math.round(db*100f);mb=Math.max(range[0],Math.min(range[1],mb));equalizer.setBandLevel(best,(short)mb);
    }

    public float visualLevel(){
        if(peaks.length==0)return isPlaying()?0.12f:0f;long d=Math.max(getDuration(),analysisDurationMs);if(d<=0)return 0f;
        int i=(int)Math.max(0,Math.min(peaks.length-1,getCurrentPosition()*(long)peaks.length/d));return Math.min(1f,peaks[i]*deckVolume*crossfadeGain*(isPlaying()?1f:0.2f));
    }

    public String getTrackName(){return trackName;} public String getStatus(){return status;}
    public long getCurrentPosition(){return Math.max(0L,player.getCurrentPosition());} public long getDuration(){return Math.max(0L,player.getDuration());}
    public boolean isPlaying(){return player.isPlaying();} public float getTempo(){return tempo;} public Double getBpm(){return bpm;}
    public float getDeckVolume(){return deckVolume;} public float getCrossfadeGain(){return crossfadeGain;} public float[] getPeaks(){return peaks;}
    public long getAnalysisDuration(){return analysisDurationMs;} public boolean isAnalyzing(){return analyzing;}
    public void release(){if(equalizer!=null)try{equalizer.release();}catch(Exception ignored){} player.release();}

    private void notifyListener(){if(listener!=null)listener.onStateChanged();}
    private static float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}
}
