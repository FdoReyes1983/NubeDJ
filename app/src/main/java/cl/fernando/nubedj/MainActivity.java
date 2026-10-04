package cl.fernando.nubedj;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity {
    private static final int BG=Color.rgb(4,6,10), PANEL=Color.rgb(18,18,21), PANEL2=Color.rgb(33,31,34), TEXT=Color.rgb(241,245,249), MUTED=Color.rgb(144,151,160), CYAN=Color.rgb(68,211,238), PINK=Color.rgb(244,79,192), GREEN=Color.rgb(82,214,151), YELLOW=Color.rgb(245,199,68);

    private DjDeck deckA,deckB;
    private DeckWidgets uiA,uiB;
    private TextView crossLabel,driveAccount;
    private VuMeterView vuA,vuB;
    private int pendingDeck=0;
    private long generationA=0,generationB=0;
    private final Handler ticker=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newFixedThreadPool(3);
    private ActivityResultLauncher<String[]> openTrack;
    private ActivityResultLauncher<IntentSenderRequest> authorization;
    private AuthManager auth;
    private DriveRepository drive;
    private Runnable afterAuthorization;

    private final Runnable tickerTask=new Runnable(){@Override public void run(){
        deckA.tick();deckB.tick();
        refreshDeck(uiA,deckA);refreshDeck(uiB,deckB);
        if(vuA!=null)vuA.setLevel(deckA.visualLevel());if(vuB!=null)vuB.setLevel(deckB.visualLevel());
        ticker.postDelayed(this,55L);
    }};

    @Override protected void onCreate(@Nullable Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowCompat.setDecorFitsSystemWindows(getWindow(),false);
        WindowInsetsControllerCompat bars=WindowCompat.getInsetsController(getWindow(),getWindow().getDecorView());
        if(bars!=null){bars.hide(WindowInsetsCompat.Type.systemBars());bars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}

        auth=new AuthManager(this);drive=new DriveRepository(auth);
        deckA=new DjDeck(this);deckB=new DjDeck(this);
        deckA.setListener(()->refreshDeck(uiA,deckA));deckB.setListener(()->refreshDeck(uiB,deckB));

        openTrack=registerForActivityResult(new ActivityResultContracts.OpenDocument(),uri->{
            if(uri==null||pendingDeck==0)return;
            try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            int d=pendingDeck;pendingDeck=0;loadDeckUri(d,uri,displayName(uri));
        });
        authorization=registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(),result->{
            if(result.getResultCode()!=RESULT_OK||result.getData()==null){showDriveError(null);return;}
            try{AuthorizationResult ar=authResult(result.getData());auth.acceptInteractive(ar);updateDriveAccount();Runnable next=afterAuthorization;afterAuthorization=null;if(next!=null)next.run();}catch(Exception e){showDriveError(e);}
        });

        setContentView(buildConsole());
        applyCrossfader(50);updateDriveAccount();ticker.post(tickerTask);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus){super.onWindowFocusChanged(hasFocus);if(hasFocus){WindowInsetsControllerCompat bars=WindowCompat.getInsetsController(getWindow(),getWindow().getDecorView());if(bars!=null)bars.hide(WindowInsetsCompat.Type.systemBars());}}
    @Override protected void onDestroy(){ticker.removeCallbacksAndMessages(null);io.shutdownNow();deckA.release();deckB.release();super.onDestroy();}

    private View buildConsole(){
        LinearLayout page=col();page.setPadding(dp(5),dp(3),dp(5),dp(3));page.setBackgroundColor(BG);
        LinearLayout header=row();
        TextView title=label("NUBE DJ",20,TEXT,true);header.addView(title);
        header.addView(label("  v0.3.0 · PERFORMANCE",9,MUTED,false));
        View spacer=new View(this);header.addView(spacer,new LinearLayout.LayoutParams(0,1,1));
        driveAccount=label("Drive",9,MUTED,false);driveAccount.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);header.addView(driveAccount,new LinearLayout.LayoutParams(dp(105),-1));
        Button library=button("☁ LIBRARY",CYAN,true);library.setOnClickListener(v->new DriveLibraryDialog(this,auth,drive).show());header.addView(library,new LinearLayout.LayoutParams(dp(92),dp(29)));
        page.addView(header,new LinearLayout.LayoutParams(-1,dp(31)));

        uiA=buildDeck(deckA,"A",CYAN,1,false);uiB=buildDeck(deckB,"B",PINK,2,true);

        LinearLayout waveRow=row();
        waveRow.addView(uiA.waveRoot,new LinearLayout.LayoutParams(0,-1,1f));
        waveRow.addView(buildWaveCenter(),new LinearLayout.LayoutParams(0,-1,0.20f));
        waveRow.addView(uiB.waveRoot,new LinearLayout.LayoutParams(0,-1,1f));
        page.addView(waveRow,new LinearLayout.LayoutParams(-1,0,1.08f));

        LinearLayout controlRow=row();
        controlRow.addView(uiA.controlRoot,new LinearLayout.LayoutParams(0,-1,1f));
        controlRow.addView(buildMixer(),new LinearLayout.LayoutParams(0,-1,0.42f));
        controlRow.addView(uiB.controlRoot,new LinearLayout.LayoutParams(0,-1,1f));
        page.addView(controlRow,new LinearLayout.LayoutParams(-1,0,0.92f));
        return page;
    }

    private DeckWidgets buildDeck(DjDeck deck,String deckName,int accent,int number,boolean mirrored){
        DeckWidgets w=new DeckWidgets();
        w.waveRoot=col();w.waveRoot.setPadding(dp(4),dp(2),dp(4),dp(2));w.waveRoot.setBackgroundColor(PANEL);
        LinearLayout info=row();
        TextView tag=label("DECK "+deckName,11,accent,true);info.addView(tag,new LinearLayout.LayoutParams(dp(54),dp(20)));
        w.track=label("Tap to load",12,TEXT,true);w.track.setSingleLine(true);w.track.setEllipsize(android.text.TextUtils.TruncateAt.END);info.addView(w.track,new LinearLayout.LayoutParams(0,dp(20),1));
        w.bpm=label("--.-",15,TEXT,true);w.bpm.setGravity(Gravity.CENTER);info.addView(w.bpm,new LinearLayout.LayoutParams(dp(54),dp(20)));
        Button load=button("LOAD",accent,false);load.setOnClickListener(v->chooseTrack(number));info.addView(load,new LinearLayout.LayoutParams(dp(48),dp(24)));
        w.waveRoot.addView(info,new LinearLayout.LayoutParams(-1,dp(25)));
        w.wave=new WaveformView(this,deck,accent);w.waveRoot.addView(w.wave,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout meta=row();w.time=label("00:00 / 00:00",9,MUTED,false);meta.addView(w.time,new LinearLayout.LayoutParams(dp(92),dp(17)));w.status=label("LISTO",8,accent,true);w.status.setSingleLine(true);w.status.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);meta.addView(w.status,new LinearLayout.LayoutParams(0,dp(17),1));w.waveRoot.addView(meta,new LinearLayout.LayoutParams(-1,dp(18)));

        w.controlRoot=row();w.controlRoot.setPadding(dp(4),dp(3),dp(4),dp(3));w.controlRoot.setBackgroundColor(PANEL2);
        w.pitch=new PitchFaderView(this,accent);w.pitch.setRangePercent(8f);w.pitch.setListener(deck::setPitchPercent);
        w.jog=new JogWheelView(this,deck,accent);
        LinearLayout buttons=buildPerformanceButtons(w,deck,accent,number);
        LinearLayout.LayoutParams pitchLp=new LinearLayout.LayoutParams(dp(45),-1);LinearLayout.LayoutParams jogLp=new LinearLayout.LayoutParams(0,-1,0.90f);LinearLayout.LayoutParams buttonsLp=new LinearLayout.LayoutParams(0,-1,1.10f);
        if(!mirrored){w.controlRoot.addView(w.pitch,pitchLp);w.controlRoot.addView(w.jog,jogLp);w.controlRoot.addView(buttons,buttonsLp);}else{w.controlRoot.addView(buttons,buttonsLp);w.controlRoot.addView(w.jog,jogLp);w.controlRoot.addView(w.pitch,pitchLp);}
        return w;
    }

    private LinearLayout buildPerformanceButtons(DeckWidgets w,DjDeck deck,int accent,int number){
        LinearLayout box=col();box.setPadding(dp(3),0,dp(3),0);
        LinearLayout big=row();
        Button cue=button("CUE",YELLOW,true);w.play=button("▶ PLAY",GREEN,true);
        cue.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN){deck.cuePressStart();v.setPressed(true);return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){deck.cuePressEnd();v.setPressed(false);v.performClick();return true;}return true;});cue.setOnClickListener(v->{});w.play.setOnClickListener(v->deck.togglePlay());
        addWeighted(big,cue);addWeighted(big,w.play);box.addView(big,new LinearLayout.LayoutParams(-1,0,1.2f));

        LinearLayout mid=row();w.sync=button("SYNC",accent,false);w.sync.setOnClickListener(v->{DjDeck master=number==1?deckB:deckA;deck.syncTo(master);});w.loop=button("LOOP 4",accent,false);w.loop.setOnClickListener(v->deck.toggleLoop());addWeighted(mid,w.sync);addWeighted(mid,w.loop);box.addView(mid,new LinearLayout.LayoutParams(-1,0,1f));

        LinearLayout small=row();Button set=button("SET",MUTED,false);set.setOnClickListener(v->deck.setCue());Button tap=button("TAP",MUTED,false);tap.setOnClickListener(v->deck.tapBpm());Button minus=button("−",MUTED,false);minus.setOnClickListener(v->deck.changeLoopBeats(-1));Button plus=button("+",MUTED,false);plus.setOnClickListener(v->deck.changeLoopBeats(1));addWeighted(small,set);addWeighted(small,tap);addWeighted(small,minus);addWeighted(small,plus);box.addView(small,new LinearLayout.LayoutParams(-1,0,0.82f));

        LinearLayout pitchInfo=row();w.pitchLabel=label("PITCH +0.0%",8,MUTED,true);pitchInfo.addView(w.pitchLabel,new LinearLayout.LayoutParams(0,-1,1));w.rangeButton=button("±8",MUTED,false);w.rangeButton.setOnClickListener(v->cyclePitchRange(w,deck));pitchInfo.addView(w.rangeButton,new LinearLayout.LayoutParams(dp(40),dp(24)));box.addView(pitchInfo,new LinearLayout.LayoutParams(-1,dp(25)));
        return box;
    }

    private View buildWaveCenter(){LinearLayout center=col();center.setPadding(dp(2),dp(3),dp(2),dp(3));center.setBackgroundColor(BG);TextView icon=label("↕",16,MUTED,true);icon.setGravity(Gravity.CENTER);center.addView(icon,new LinearLayout.LayoutParams(-1,0,1));TextView hint=label("PINCH\nZOOM",7,MUTED,true);hint.setGravity(Gravity.CENTER);center.addView(hint,new LinearLayout.LayoutParams(-1,dp(34)));return center;}

    private View buildMixer(){
        LinearLayout mix=col();mix.setPadding(dp(4),dp(2),dp(4),dp(2));mix.setBackgroundColor(Color.rgb(39,37,40));
        LinearLayout meters=row();vuA=new VuMeterView(this);vuB=new VuMeterView(this);meters.addView(vuA,new LinearLayout.LayoutParams(0,-1,1));meters.addView(vuB,new LinearLayout.LayoutParams(0,-1,1));mix.addView(meters,new LinearLayout.LayoutParams(-1,0,1));
        Button eq=button("MIX",MUTED,false);eq.setOnClickListener(v->showMixerDialog());mix.addView(eq,new LinearLayout.LayoutParams(-1,dp(28)));
        TextView cf=label("X-FADER",7,MUTED,true);cf.setGravity(Gravity.CENTER);mix.addView(cf,new LinearLayout.LayoutParams(-1,dp(15)));
        SeekBar cross=new SeekBar(this);cross.setMax(100);cross.setProgress(50);cross.setMinHeight(0);cross.setPadding(0,0,0,0);tint(cross,GREEN);cross.setThumbTintList(ColorStateList.valueOf(Color.WHITE));cross.setOnSeekBarChangeListener(new SimpleSeekListener(this::applyCrossfader));mix.addView(cross,new LinearLayout.LayoutParams(-1,dp(28)));
        crossLabel=label("71|71",7,TEXT,true);crossLabel.setGravity(Gravity.CENTER);mix.addView(crossLabel,new LinearLayout.LayoutParams(-1,dp(15)));
        Button center=button("●",GREEN,false);center.setOnClickListener(v->cross.setProgress(50));mix.addView(center,new LinearLayout.LayoutParams(-1,dp(25)));
        return mix;
    }

    private void showMixerDialog(){
        LinearLayout root=col();root.setPadding(dp(12),dp(8),dp(12),dp(8));root.setBackgroundColor(PANEL);
        TextView title=label("MIXER · EQ / CHANNEL",16,TEXT,true);title.setGravity(Gravity.CENTER);root.addView(title,new LinearLayout.LayoutParams(-1,dp(34)));
        root.addView(mixerChannel(deckA,"DECK A",CYAN),new LinearLayout.LayoutParams(-1,dp(92)));
        root.addView(mixerChannel(deckB,"DECK B",PINK),new LinearLayout.LayoutParams(-1,dp(92)));
        new MaterialAlertDialogBuilder(this).setView(root).setPositiveButton("Cerrar",null).show();
    }

    private View mixerChannel(DjDeck deck,String name,int accent){
        LinearLayout c=col();TextView n=label(name,11,accent,true);c.addView(n,new LinearLayout.LayoutParams(-1,dp(20)));
        LinearLayout row=row();final float[] eq={0,0,0};String[] names={"LOW","MID","HIGH"};
        for(int i=0;i<3;i++){final int idx=i;LinearLayout cell=col();TextView l=label(names[i],8,MUTED,false);l.setGravity(Gravity.CENTER);SeekBar s=new SeekBar(this);s.setMin(-12);s.setMax(12);s.setProgress(0);s.setMinHeight(0);tint(s,accent);s.setOnSeekBarChangeListener(new SimpleSeekListener(p->{eq[idx]=p;deck.setEq(eq[0],eq[1],eq[2]);}));cell.addView(l,new LinearLayout.LayoutParams(-1,dp(18)));cell.addView(s,new LinearLayout.LayoutParams(-1,dp(34)));row.addView(cell,new LinearLayout.LayoutParams(0,-1,1));}
        LinearLayout volCell=col();TextView vl=label("VOL",8,MUTED,false);vl.setGravity(Gravity.CENTER);SeekBar vol=new SeekBar(this);vol.setMax(100);vol.setProgress(Math.round(deck.getDeckVolume()*100));vol.setMinHeight(0);tint(vol,accent);vol.setOnSeekBarChangeListener(new SimpleSeekListener(p->deck.setDeckVolume(p/100f)));volCell.addView(vl,new LinearLayout.LayoutParams(-1,dp(18)));volCell.addView(vol,new LinearLayout.LayoutParams(-1,dp(34)));row.addView(volCell,new LinearLayout.LayoutParams(0,-1,1));c.addView(row,new LinearLayout.LayoutParams(-1,0,1));return c;
    }

    private void cyclePitchRange(DeckWidgets w,DjDeck deck){float r=w.pitch.getRangePercent();float next=r<10?16f:r<20?50f:8f;w.pitch.setRangePercent(next);w.pitch.setValuePercent(Math.max(-next,Math.min(next,deck.getPitchPercent())));deck.setPitchPercent(w.pitch.getValuePercent());w.rangeButton.setText("±"+(int)next);}
    private void chooseTrack(int which){pendingDeck=which;openTrack.launch(new String[]{"audio/*"});}

    private void loadDeckUri(int which,Uri uri,String name){
        long gen=nextGeneration(which);DjDeck deck=which==1?deckA:deckB;deck.load(uri,name);analyzeDeck(which,uri,gen);
    }

    private void analyzeDeck(int which,Uri uri,long gen){
        DjDeck deck=which==1?deckA:deckB;
        io.execute(()->{
            TrackAnalysis cached=AnalysisCache.read(this,uri);
            if(cached!=null){runOnUiThread(()->{if(isCurrent(which,gen))deck.setAnalysis(cached);});return;}
            Double bpm=null;TrackAnalysis wave=null;
            try{bpm=AudioAnalyzer.analyzeBpm(this,uri);Double finalBpm=bpm;runOnUiThread(()->{if(isCurrent(which,gen)){deck.setBpm(finalBpm);deck.setStatus(finalBpm==null?"BPM NO DETECTADO · GENERANDO WAVEFORM":"BPM LISTO · GENERANDO WAVEFORM");}});}catch(Exception ignored){}
            try{wave=AudioAnalyzer.analyzeWaveform(this,uri);}catch(Exception ignored){}
            TrackAnalysis finalAnalysis=new TrackAnalysis(bpm,wave==null?new float[0]:wave.peaks,wave==null?0L:wave.durationMs);
            AnalysisCache.write(this,uri,finalAnalysis);
            runOnUiThread(()->{if(isCurrent(which,gen))deck.setAnalysis(finalAnalysis);});
        });
    }

    /** Drive v0.3 starts streaming immediately; download/cache/analysis continue in background. */
    public void loadDriveTrack(int which,DriveTrack track){
        long gen=nextGeneration(which);DjDeck deck=which==1?deckA:deckB;deck.setStatus("CONECTANDO DRIVE…");
        io.execute(()->{
            try{
                String token=auth.accessToken();
                Uri media=Uri.parse("https://www.googleapis.com/drive/v3/files").buildUpon().appendPath(track.id).appendQueryParameter("alt","media").appendQueryParameter("supportsAllDrives","true").build();
                Map<String,String> headers=new HashMap<>();headers.put("Authorization","Bearer "+token);if(track.resourceKey!=null&&!track.resourceKey.isEmpty())headers.put("X-Goog-Drive-Resource-Keys",track.id+"/"+track.resourceKey);
                runOnUiThread(()->{if(isCurrent(which,gen))deck.loadRemote(media,headers,track.name);});
                File file=DriveCache.download(this,auth,track);
                if(isCurrent(which,gen))analyzeDeck(which,Uri.fromFile(file),gen);
            }catch(Exception e){runOnUiThread(()->{if(isCurrent(which,gen))deck.setStatus("ERROR DRIVE · "+shortMessage(e));});}
        });
    }

    private long nextGeneration(int which){if(which==1)return ++generationA;return ++generationB;}
    private boolean isCurrent(int which,long gen){return which==1?generationA==gen:generationB==gen;}

    public void connectDrive(Runnable after){afterAuthorization=after;auth.authorize(this).addOnSuccessListener(this,result->{if(result.hasResolution()){if(result.getPendingIntent()==null){showDriveError(null);return;}try{authorization.launch(new IntentSenderRequest.Builder(result.getPendingIntent().getIntentSender()).build());}catch(RuntimeException e){showDriveError(e);}}else{try{auth.acceptInteractive(result);updateDriveAccount();Runnable next=afterAuthorization;afterAuthorization=null;if(next!=null)next.run();}catch(Exception e){showDriveError(e);}}}).addOnFailureListener(this,this::showDriveError);}
    private AuthorizationResult authResult(Intent data)throws ApiException{return com.google.android.gms.auth.api.identity.Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data);}
    private void updateDriveAccount(){driveAccount.setText(auth.isConnected()?auth.accountLabel():"Drive offline");if(auth.isConnected())io.execute(()->{try{String email=drive.accountEmail();auth.rememberAccount(email);runOnUiThread(()->driveAccount.setText(auth.accountLabel()));}catch(Exception ignored){}});}
    private void showDriveError(@Nullable Exception error){String msg="No se pudo conectar Google Drive.";if(error instanceof ApiException){int code=((ApiException)error).getStatusCode();msg+="\nCódigo Google: "+code+" ("+CommonStatusCodes.getStatusCodeString(code)+")";if(code==CommonStatusCodes.DEVELOPER_ERROR)msg+="\n\nRevisa el cliente OAuth Android de cl.fernando.nubedj.";}new MaterialAlertDialogBuilder(this).setTitle("Google Drive").setMessage(msg).setPositiveButton("Aceptar",null).show();}

    private void applyCrossfader(int progress){float[] g=CrossfaderMath.equalPower(progress/100f);deckA.setCrossfadeGain(g[0]);deckB.setCrossfadeGain(g[1]);if(crossLabel!=null)crossLabel.setText(String.format(Locale.US,"%d|%d",Math.round(g[0]*100),Math.round(g[1]*100)));}
    private void refreshDeck(DeckWidgets w,DjDeck deck){if(w==null||deck==null)return;w.track.setText(deck.getTrackName());w.time.setText(TimeFormat.ms(deck.getCurrentPosition())+" / "+TimeFormat.ms(deck.getDuration()));w.bpm.setText(deck.getBpm()==null?"--.-":String.format(Locale.US,"%.1f",deck.getBpm()));w.pitchLabel.setText(String.format(Locale.US,"PITCH %+.1f%%",deck.getPitchPercent()));w.status.setText(deck.getStatus());w.play.setText(deck.isPlaying()?"Ⅱ PAUSE":"▶ PLAY");w.loop.setText((deck.isLoopEnabled()?"● ":"")+"LOOP "+deck.getLoopBeats());if(!w.pitch.isPressed())w.pitch.setValuePercent(deck.getPitchPercent());w.wave.invalidate();w.jog.invalidate();}
    private String displayName(Uri uri){try(android.database.Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}return uri.getLastPathSegment()==null?"Pista":uri.getLastPathSegment();}

    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView label(String s,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private Button button(String text,int accent,boolean solid){Button b=new Button(this);b.setText(text);b.setTextSize(9);b.setAllCaps(false);b.setTextColor(solid?BG:TEXT);b.setPadding(dp(3),0,dp(3),0);b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);GradientDrawable d=new GradientDrawable();d.setCornerRadius(dp(5));d.setColor(solid?accent:Color.rgb(42,42,46));d.setStroke(dp(1),accent);b.setBackground(d);return b;}
    private void addWeighted(LinearLayout row,View v){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.setMargins(dp(1),dp(1),dp(1),dp(1));row.addView(v,lp);}
    private void tint(SeekBar b,int color){b.setProgressTintList(ColorStateList.valueOf(color));b.setThumbTintList(ColorStateList.valueOf(color));}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private static String shortMessage(Exception e){String s=e.getMessage();return s==null?"No se pudo cargar":s.length()>50?s.substring(0,50):s;}

    private static final class DeckWidgets{LinearLayout waveRoot,controlRoot;TextView track,time,bpm,pitchLabel,status;Button play,sync,loop,rangeButton;PitchFaderView pitch;WaveformView wave;JogWheelView jog;}
    private static final class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener{interface C{void changed(int p);}private final C c;SimpleSeekListener(C c){this.c=c;}@Override public void onProgressChanged(SeekBar s,int p,boolean user){if(user)c.changed(p);}@Override public void onStartTrackingTouch(SeekBar s){}@Override public void onStopTrackingTouch(SeekBar s){c.changed(s.getProgress());}}
}
