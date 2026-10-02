package cl.fernando.nubedj;

import android.content.Context;
import android.net.Uri;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

public final class DjDeck {
    public interface Listener { void onStateChanged(); }

    private final ExoPlayer player;
    private final TapTempo tapTempo = new TapTempo();
    private Listener listener;
    private String trackName = "Sin pista";
    private long cuePositionMs = 0L;
    private float deckVolume = 1f;
    private float crossfadeGain = 1f;
    private float tempo = 1f;
    private Double bpm;

    public DjDeck(Context context) {
        player = new ExoPlayer.Builder(context).build();
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();
        // false: ambos decks pueden sonar simultáneamente sin robarse el audio focus.
        player.setAudioAttributes(attrs, false);
        player.setHandleAudioBecomingNoisy(false);
        player.setWakeMode(C.WAKE_MODE_LOCAL);
        player.setRepeatMode(Player.REPEAT_MODE_OFF);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int playbackState) { notifyListener(); }
            @Override public void onIsPlayingChanged(boolean isPlaying) { notifyListener(); }
        });
    }

    public void setListener(Listener listener) { this.listener = listener; }

    public void load(Uri uri, String name) {
        trackName = name == null || name.trim().isEmpty() ? "Pista" : name;
        cuePositionMs = 0L;
        tapTempo.reset();
        bpm = null;
        player.setMediaItem(MediaItem.fromUri(uri));
        player.prepare();
        notifyListener();
    }

    public void togglePlay() {
        if (player.isPlaying()) player.pause(); else player.play();
    }

    public void setCue() { cuePositionMs = Math.max(0L, player.getCurrentPosition()); }

    public void cue() {
        player.pause();
        player.seekTo(cuePositionMs);
    }

    public void seekTo(long ms) { player.seekTo(Math.max(0L, ms)); }

    public void setDeckVolume(float value01) {
        deckVolume = clamp(value01, 0f, 1f);
        applyVolume();
    }

    public void setCrossfadeGain(float value01) {
        crossfadeGain = clamp(value01, 0f, 1f);
        applyVolume();
    }

    private void applyVolume() { player.setVolume(deckVolume * crossfadeGain); }

    public void setTempo(float speed) {
        tempo = clamp(speed, 0.75f, 1.25f);
        player.setPlaybackParameters(new PlaybackParameters(tempo));
        notifyListener();
    }

    public Double tapBpm() {
        bpm = tapTempo.tap();
        notifyListener();
        return bpm;
    }

    public void setBpm(Double bpm) {
        this.bpm = bpm;
        notifyListener();
    }

    public boolean syncTo(DjDeck master) {
        if (master.bpm == null || bpm == null || bpm <= 0) return false;
        float wanted = (float) (master.bpm / bpm);
        setTempo(wanted);
        return true;
    }

    public String getTrackName() { return trackName; }
    public long getCurrentPosition() { return player.getCurrentPosition(); }
    public long getDuration() { return Math.max(0L, player.getDuration()); }
    public boolean isPlaying() { return player.isPlaying(); }
    public float getTempo() { return tempo; }
    public Double getBpm() { return bpm; }
    public float getDeckVolume() { return deckVolume; }
    public float getCrossfadeGain() { return crossfadeGain; }
    public void release() { player.release(); }

    private void notifyListener() { if (listener != null) listener.onStateChanged(); }
    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
}
