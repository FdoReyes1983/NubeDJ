package cl.fernando.nubedj;

import android.content.Context;
import android.media.audiofx.Equalizer;
import android.net.Uri;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;

import java.util.Collections;
import java.util.Map;

@UnstableApi
public final class DjDeck {
    public interface Listener { void onStateChanged(); }

    private final ExoPlayer player;
    private final TapTempo tapTempo = new TapTempo();
    private Listener listener;
    private String trackName = "Sin pista";
    private String status = "LISTO";
    private long cuePositionMs = 0L;
    private float deckVolume = 1f, crossfadeGain = 1f;
    private float pitchPercent = 0f;
    private Double bpm;
    private float[] peaks = new float[0];
    private long analysisDurationMs;
    private boolean analyzing;
    private Equalizer equalizer;
    private float lowDb, midDb, highDb;
    private boolean cueHeld;

    private boolean scratchWasPlaying;
    private boolean nudging;
    private boolean loopEnabled;
    private int loopBeats = 4;
    private long loopStartMs, loopEndMs;

    public DjDeck(Context context) {
        player = new ExoPlayer.Builder(context).build();
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();
        // false: deck A and B can play simultaneously without stealing focus from each other.
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
        resetForTrack(name);
        player.setMediaItem(MediaItem.fromUri(uri));
        player.prepare();
        notifyListener();
    }

    public void loadRemote(Uri uri, Map<String,String> headers, String name) {
        resetForTrack(name);
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(12_000)
                .setReadTimeoutMs(30_000)
                .setUserAgent("NubeDJ/0.3");
        http.setDefaultRequestProperties(headers == null ? Collections.emptyMap() : headers);
        ProgressiveMediaSource source = new ProgressiveMediaSource.Factory(http)
                .createMediaSource(MediaItem.fromUri(uri));
        player.setMediaSource(source);
        player.prepare();
        status = "STREAMING · ANALIZANDO EN SEGUNDO PLANO";
        notifyListener();
    }

    private void resetForTrack(String name) {
        trackName = name == null || name.trim().isEmpty() ? "Pista" : name;
        cuePositionMs = 0L;
        tapTempo.reset();
        bpm = null;
        peaks = new float[0];
        analysisDurationMs = 0L;
        analyzing = true;
        loopEnabled = false;
        loopStartMs = loopEndMs = 0L;
        status = "CARGANDO · ANALIZANDO EN SEGUNDO PLANO";
    }

    public void setAnalysis(TrackAnalysis analysis) {
        analyzing = false;
        if (analysis != null) {
            if (analysis.peaks.length > 0) peaks = analysis.peaks;
            if (analysis.durationMs > 0) analysisDurationMs = analysis.durationMs;
            if (analysis.bpm != null) bpm = analysis.bpm;
            status = analysis.bpm == null ? "WAVEFORM LISTO · BPM MANUAL" : "LISTO";
        } else status = "REPRODUCCIÓN LISTA · ANÁLISIS NO DISPONIBLE";
        notifyListener();
    }

    public void setBpm(Double value) {
        if (value != null && value > 0) bpm = value;
        notifyListener();
    }

    public void setWaveform(float[] values, long durationMs) {
        if (values != null && values.length > 0) peaks = values;
        if (durationMs > 0) analysisDurationMs = durationMs;
        notifyListener();
    }

    public void setAnalyzing(boolean value) { analyzing = value; notifyListener(); }
    public void setStatus(String text) { status = text == null ? "" : text; notifyListener(); }
    public void togglePlay() { if (player.isPlaying()) player.pause(); else player.play(); }

    public void setCue() {
        cuePositionMs = Math.max(0L, player.getCurrentPosition());
        status = "CUE " + TimeFormat.ms(cuePositionMs);
        notifyListener();
    }

    public void cue() {
        player.pause();
        player.seekTo(cuePositionMs);
        status = "CUE";
        notifyListener();
    }

    /** CDJ-style momentary cue: hold = play from cue, release = return to cue. */
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

    // --- Jog / scratch / nudge ------------------------------------------------
    public void beginScratch() {
        scratchWasPlaying = player.isPlaying();
        player.pause();
        status = "SCRATCH";
        notifyListener();
    }

    public void scratchBy(long deltaMs) {
        seekTo(getCurrentPosition() + deltaMs);
        status = "SCRATCH";
    }

    public void endScratch() {
        if (scratchWasPlaying) player.play();
        status = scratchWasPlaying ? "PLAY" : "PAUSA";
        notifyListener();
    }

    public void beginNudge() {
        nudging = true;
        status = "PITCH BEND";
    }

    public void nudge(float amount) {
        if (!nudging) return;
        float base = baseSpeed();
        float speed = clamp(base * (1f + clamp(amount, -0.10f, 0.10f)), 0.50f, 1.50f);
        player.setPlaybackParameters(new PlaybackParameters(speed, 1f));
    }

    public void endNudge() {
        nudging = false;
        applyPitch();
        status = player.isPlaying() ? "PLAY" : "PAUSA";
        notifyListener();
    }

    // Compatibility with v0.2.1 jog calls.
    public void beginJog() { beginScratch(); }
    public void scrubBy(long deltaMs) { scratchBy(deltaMs); }
    public void endJog() { endScratch(); }

    // --- Mixer / pitch ---------------------------------------------------------
    public void setDeckVolume(float value01) { deckVolume = clamp(value01, 0f, 1f); applyVolume(); }
    public void setCrossfadeGain(float value01) { crossfadeGain = clamp(value01, 0f, 1f); applyVolume(); }
    private void applyVolume() { player.setVolume(deckVolume * crossfadeGain); }

    public void setPitchPercent(float percent) {
        pitchPercent = clamp(percent, -50f, 50f);
        if (!nudging) applyPitch();
        notifyListener();
    }

    public void setTempo(float speed) {
        setPitchPercent((clamp(speed, 0.50f, 1.50f) - 1f) * 100f);
    }

    private float baseSpeed() { return clamp(1f + pitchPercent / 100f, 0.50f, 1.50f); }
    private void applyPitch() { player.setPlaybackParameters(new PlaybackParameters(baseSpeed(), 1f)); }

    public Double tapBpm() {
        bpm = tapTempo.tap();
        status = bpm == null ? "TAP…" : "BPM AJUSTADO";
        notifyListener();
        return bpm;
    }

    public boolean syncTo(DjDeck master) {
        if (master.bpm == null || bpm == null || bpm <= 0) {
            status = "FALTA BPM PARA SYNC";
            notifyListener();
            return false;
        }
        float target = (float) (master.effectiveBpm() / bpm);
        setTempo(target);
        status = "SYNC";
        notifyListener();
        return true;
    }

    public double effectiveBpm() { return bpm == null ? 0.0 : bpm * baseSpeed(); }

    // --- Loop -----------------------------------------------------------------
    public void setLoopBeats(int beats) {
        loopBeats = Math.max(1, Math.min(32, beats));
        if (loopEnabled) armLoop();
        notifyListener();
    }

    public void changeLoopBeats(int direction) {
        int[] sizes = {1,2,4,8,16,32};
        int index = 0;
        for (int i=0;i<sizes.length;i++) if (sizes[i] == loopBeats) { index=i; break; }
        index = Math.max(0, Math.min(sizes.length-1, index + (direction < 0 ? -1 : 1)));
        setLoopBeats(sizes[index]);
    }

    public void toggleLoop() {
        loopEnabled = !loopEnabled;
        if (loopEnabled) armLoop();
        status = loopEnabled ? "LOOP " + loopBeats : "LOOP OFF";
        notifyListener();
    }

    private void armLoop() {
        double useBpm = effectiveBpm();
        if (useBpm <= 0) useBpm = 120.0;
        long beatMs = Math.max(120L, Math.round(60_000.0 / useBpm));
        loopStartMs = getCurrentPosition();
        loopEndMs = loopStartMs + beatMs * loopBeats;
    }

    public void tick() {
        if (loopEnabled && loopEndMs > loopStartMs && getCurrentPosition() >= loopEndMs - 8L) {
            player.seekTo(loopStartMs);
        }
    }

    // --- EQ -------------------------------------------------------------------
    public void setEq(float lowDb,float midDb,float highDb){this.lowDb=lowDb;this.midDb=midDb;this.highDb=highDb;applyEq();}
    private void attachEqualizer(int id){
        if(id<=0)return;
        try{if(equalizer!=null)equalizer.release();equalizer=new Equalizer(0,id);equalizer.setEnabled(true);applyEq();}catch(Exception ignored){equalizer=null;}
    }
    private void applyEq(){
        if(equalizer==null)return;try{setNearestBand(100,lowDb);setNearestBand(1000,midDb);setNearestBand(10000,highDb);}catch(Exception ignored){}
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

    public String getTrackName(){return trackName;}
    public String getStatus(){return status;}
    public long getCurrentPosition(){return Math.max(0L,player.getCurrentPosition());}
    public long getDuration(){long d=player.getDuration();return d==C.TIME_UNSET?Math.max(0L,analysisDurationMs):Math.max(0L,d);}
    public boolean isPlaying(){return player.isPlaying();}
    public float getTempo(){return baseSpeed();}
    public float getPitchPercent(){return pitchPercent;}
    public Double getBpm(){return bpm;}
    public float getDeckVolume(){return deckVolume;}
    public float getCrossfadeGain(){return crossfadeGain;}
    public float[] getPeaks(){return peaks;}
    public long getAnalysisDuration(){return analysisDurationMs;}
    public boolean isAnalyzing(){return analyzing;}
    public boolean isLoopEnabled(){return loopEnabled;}
    public int getLoopBeats(){return loopBeats;}
    public long getCuePositionMs(){return cuePositionMs;}

    public void release(){if(equalizer!=null)try{equalizer.release();}catch(Exception ignored){} player.release();}
    private void notifyListener(){if(listener!=null)listener.onStateChanged();}
    private static float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}
}
