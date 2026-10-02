package cl.fernando.nubedj;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public final class MainActivity extends AppCompatActivity {
    private static final int BG = Color.rgb(8, 12, 20);
    private static final int PANEL = Color.rgb(16, 24, 38);
    private static final int PANEL_2 = Color.rgb(22, 32, 49);
    private static final int TEXT = Color.rgb(237, 246, 255);
    private static final int MUTED = Color.rgb(145, 164, 184);
    private static final int CYAN = Color.rgb(82, 229, 255);
    private static final int PINK = Color.rgb(255, 79, 216);
    private static final int GREEN = Color.rgb(105, 225, 191);
    private static final int YELLOW = Color.rgb(255, 215, 90);

    private DjDeck deckA;
    private DjDeck deckB;
    private DeckWidgets uiA;
    private DeckWidgets uiB;
    private TextView crossLabel;
    private int pendingDeck = 0;

    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable tickerTask = new Runnable() {
        @Override public void run() {
            refreshDeck(uiA, deckA);
            refreshDeck(uiB, deckB);
            ticker.postDelayed(this, 200L);
        }
    };

    private ActivityResultLauncher<String[]> openTrack;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        deckA = new DjDeck(this);
        deckB = new DjDeck(this);
        deckA.setListener(() -> refreshDeck(uiA, deckA));
        deckB.setListener(() -> refreshDeck(uiB, deckB));

        openTrack = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null || pendingDeck == 0) return;
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) { }
            String name = displayName(uri);
            if (pendingDeck == 1) deckA.load(uri, name); else deckB.load(uri, name);
            pendingDeck = 0;
        });

        setContentView(buildConsole());
        applyCrossfader(50);
        ticker.post(tickerTask);
    }

    private View buildConsole() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BG);
        page.setPadding(dp(12), dp(10), dp(12), dp(10));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("NUBE DJ", 24, TEXT, Typeface.BOLD);
        TextView version = text("  v0.1.0  •  MIX A/B", 12, MUTED, Typeface.NORMAL);
        header.addView(title);
        header.addView(version);
        page.addView(header, new LinearLayout.LayoutParams(-1, dp(38)));

        LinearLayout console = new LinearLayout(this);
        console.setOrientation(LinearLayout.HORIZONTAL);
        console.setGravity(Gravity.CENTER_VERTICAL);

        uiA = buildDeck(deckA, "DECK A", CYAN, 1);
        uiB = buildDeck(deckB, "DECK B", PINK, 2);
        console.addView(uiA.root, new LinearLayout.LayoutParams(0, -1, 1f));
        console.addView(buildMixer(), new LinearLayout.LayoutParams(dp(170), -1));
        console.addView(uiB.root, new LinearLayout.LayoutParams(0, -1, 1f));

        page.addView(console, new LinearLayout.LayoutParams(-1, 0, 1f));
        return page;
    }

    private DeckWidgets buildDeck(DjDeck deck, String title, int accent, int deckNumber) {
        DeckWidgets w = new DeckWidgets();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.setBackgroundColor(PANEL);
        w.root = root;

        TextView deckTitle = text(title, 20, accent, Typeface.BOLD);
        deckTitle.setGravity(Gravity.CENTER);
        root.addView(deckTitle, new LinearLayout.LayoutParams(-1, dp(30)));

        w.track = text("Sin pista", 15, TEXT, Typeface.BOLD);
        w.track.setSingleLine(true);
        w.track.setGravity(Gravity.CENTER);
        root.addView(w.track, new LinearLayout.LayoutParams(-1, dp(34)));

        w.time = text("00:00 / 00:00", 12, MUTED, Typeface.NORMAL);
        w.time.setGravity(Gravity.CENTER);
        root.addView(w.time, new LinearLayout.LayoutParams(-1, dp(24)));

        w.position = new SeekBar(this);
        w.position.setMax(1000);
        w.position.setProgressTintList(ColorStateList.valueOf(accent));
        w.position.setThumbTintList(ColorStateList.valueOf(accent));
        final boolean[] seeking = {false};
        w.position.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { seeking[0] = true; }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                long duration = deck.getDuration();
                if (duration > 0) deck.seekTo(duration * seekBar.getProgress() / 1000L);
                seeking[0] = false;
            }
        });
        w.userSeeking = seeking;
        root.addView(w.position, new LinearLayout.LayoutParams(-1, dp(38)));

        LinearLayout transport = row();
        Button load = button("CARGAR", accent);
        w.play = button("PLAY", GREEN);
        Button cue = button("CUE", YELLOW);
        Button setCue = button("SET", MUTED);
        load.setOnClickListener(v -> chooseTrack(deckNumber));
        w.play.setOnClickListener(v -> deck.togglePlay());
        cue.setOnClickListener(v -> deck.cue());
        setCue.setOnClickListener(v -> { deck.setCue(); toast(title + ": Cue guardado"); });
        addWeighted(transport, load); addWeighted(transport, cue); addWeighted(transport, setCue); addWeighted(transport, w.play);
        root.addView(transport, new LinearLayout.LayoutParams(-1, dp(52)));

        LinearLayout bpmRow = row();
        w.bpm = text("BPM --", 16, TEXT, Typeface.BOLD);
        w.bpm.setGravity(Gravity.CENTER);
        Button tap = button("TAP BPM", accent);
        tap.setOnClickListener(v -> deck.tapBpm());
        bpmRow.addView(w.bpm, new LinearLayout.LayoutParams(0, -1, 1f));
        bpmRow.addView(tap, new LinearLayout.LayoutParams(0, -1, 1f));
        root.addView(bpmRow, new LinearLayout.LayoutParams(-1, dp(48)));

        w.tempoLabel = text("TEMPO 100.0%", 12, MUTED, Typeface.BOLD);
        root.addView(w.tempoLabel, new LinearLayout.LayoutParams(-1, dp(24)));
        SeekBar tempo = new SeekBar(this);
        tempo.setMin(75); tempo.setMax(125); tempo.setProgress(100);
        tempo.setProgressTintList(ColorStateList.valueOf(accent));
        tempo.setThumbTintList(ColorStateList.valueOf(accent));
        tempo.setOnSeekBarChangeListener(new SimpleSeekListener(p -> deck.setTempo(p / 100f)));
        root.addView(tempo, new LinearLayout.LayoutParams(-1, dp(36)));
        w.tempo = tempo;

        w.volumeLabel = text("VOL 100%", 12, MUTED, Typeface.BOLD);
        root.addView(w.volumeLabel, new LinearLayout.LayoutParams(-1, dp(24)));
        SeekBar volume = new SeekBar(this);
        volume.setMax(100); volume.setProgress(100);
        volume.setProgressTintList(ColorStateList.valueOf(accent));
        volume.setThumbTintList(ColorStateList.valueOf(accent));
        volume.setOnSeekBarChangeListener(new SimpleSeekListener(p -> {
            deck.setDeckVolume(p / 100f);
            w.volumeLabel.setText(String.format(Locale.US, "VOL %d%%", p));
        }));
        root.addView(volume, new LinearLayout.LayoutParams(-1, dp(36)));
        w.volume = volume;

        TextView hint = text("Selector Android: local / Drive disponible como proveedor", 10, MUTED, Typeface.NORMAL);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, new LinearLayout.LayoutParams(-1, 0, 1f));
        return w;
    }

    private View buildMixer() {
        LinearLayout mix = new LinearLayout(this);
        mix.setOrientation(LinearLayout.VERTICAL);
        mix.setPadding(dp(10), dp(12), dp(10), dp(12));
        mix.setGravity(Gravity.CENTER);
        mix.setBackgroundColor(PANEL_2);

        TextView title = text("MIXER", 18, TEXT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        mix.addView(title, new LinearLayout.LayoutParams(-1, dp(32)));

        TextView cf = text("CROSSFADER", 11, MUTED, Typeface.BOLD);
        cf.setGravity(Gravity.CENTER);
        mix.addView(cf, new LinearLayout.LayoutParams(-1, dp(28)));

        SeekBar cross = new SeekBar(this);
        cross.setMax(100); cross.setProgress(50);
        cross.setProgressTintList(ColorStateList.valueOf(GREEN));
        cross.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        cross.setOnSeekBarChangeListener(new SimpleSeekListener(this::applyCrossfader));
        mix.addView(cross, new LinearLayout.LayoutParams(-1, dp(52)));

        crossLabel = text("A 71%  •  B 71%", 11, TEXT, Typeface.BOLD);
        crossLabel.setGravity(Gravity.CENTER);
        mix.addView(crossLabel, new LinearLayout.LayoutParams(-1, dp(34)));

        Button syncB = button("SYNC B ← A", CYAN);
        syncB.setOnClickListener(v -> {
            if (!deckB.syncTo(deckA)) toast("Marca BPM con TAP en ambos decks");
            else setTempoUi(uiB, deckB);
        });
        mix.addView(syncB, new LinearLayout.LayoutParams(-1, dp(48)));

        Button syncA = button("SYNC A ← B", PINK);
        syncA.setOnClickListener(v -> {
            if (!deckA.syncTo(deckB)) toast("Marca BPM con TAP en ambos decks");
            else setTempoUi(uiA, deckA);
        });
        mix.addView(syncA, new LinearLayout.LayoutParams(-1, dp(48)));

        Button center = button("CENTRAR", GREEN);
        center.setOnClickListener(v -> {
            cross.setProgress(50);
            applyCrossfader(50);
        });
        mix.addView(center, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView note = text("Curva equal-power\n(-3 dB al centro)", 10, MUTED, Typeface.NORMAL);
        note.setGravity(Gravity.CENTER);
        mix.addView(note, new LinearLayout.LayoutParams(-1, 0, 1f));
        return mix;
    }

    private void chooseTrack(int which) {
        pendingDeck = which;
        openTrack.launch(new String[] {"audio/*"});
    }

    private void applyCrossfader(int progress) {
        float[] gains = CrossfaderMath.equalPower(progress / 100f);
        deckA.setCrossfadeGain(gains[0]);
        deckB.setCrossfadeGain(gains[1]);
        if (crossLabel != null) {
            crossLabel.setText(String.format(Locale.US, "A %.0f%%  •  B %.0f%%", gains[0] * 100, gains[1] * 100));
        }
    }

    private void refreshDeck(DeckWidgets w, DjDeck deck) {
        if (w == null || deck == null) return;
        w.track.setText(deck.getTrackName());
        long pos = deck.getCurrentPosition();
        long dur = deck.getDuration();
        w.time.setText(TimeFormat.ms(pos) + " / " + TimeFormat.ms(dur));
        if (!w.userSeeking[0] && dur > 0) w.position.setProgress((int) Math.min(1000, pos * 1000L / dur));
        w.play.setText(deck.isPlaying() ? "PAUSA" : "PLAY");
        w.bpm.setText(deck.getBpm() == null ? "BPM --" : String.format(Locale.US, "BPM %.1f", deck.getBpm()));
        setTempoUi(w, deck);
    }

    private void setTempoUi(DeckWidgets w, DjDeck deck) {
        int progress = Math.round(deck.getTempo() * 100f);
        if (w.tempo.getProgress() != progress) w.tempo.setProgress(progress);
        w.tempoLabel.setText(String.format(Locale.US, "TEMPO %.1f%%", deck.getTempo() * 100f));
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return c.getString(i);
            }
        } catch (Exception ignored) { }
        String last = uri.getLastPathSegment();
        return last == null ? "Pista" : last;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private void addWeighted(LinearLayout row, View view) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.setMargins(dp(2), dp(3), dp(2), dp(3));
        row.addView(view, lp);
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans", style));
        return t;
    }

    private Button button(String value, int accent) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(11);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setBackgroundTintList(ColorStateList.valueOf(blend(accent, PANEL, 0.36f)));
        return b;
    }

    private static int blend(int a, int b, float amountA) {
        float q = 1f - amountA;
        return Color.rgb(
                Math.round(Color.red(a) * amountA + Color.red(b) * q),
                Math.round(Color.green(a) * amountA + Color.green(b) * q),
                Math.round(Color.blue(a) * amountA + Color.blue(b) * q));
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show(); }

    @Override protected void onDestroy() {
        ticker.removeCallbacksAndMessages(null);
        if (deckA != null) deckA.release();
        if (deckB != null) deckB.release();
        super.onDestroy();
    }

    private static final class DeckWidgets {
        LinearLayout root;
        TextView track;
        TextView time;
        TextView bpm;
        TextView tempoLabel;
        TextView volumeLabel;
        Button play;
        SeekBar position;
        SeekBar tempo;
        SeekBar volume;
        boolean[] userSeeking;
    }

    private static final class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        interface Callback { void changed(int progress); }
        private final Callback callback;
        SimpleSeekListener(Callback callback) { this.callback = callback; }
        @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { callback.changed(progress); }
        @Override public void onStartTrackingTouch(SeekBar seekBar) { }
        @Override public void onStopTrackingTouch(SeekBar seekBar) { }
    }
}
