package cl.fernando.nubedj;

import android.content.Intent;
import android.content.IntentSender;
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

import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity {
    private static final int BG=Color.rgb(6,10,17),PANEL=Color.rgb(14,22,34),PANEL2=Color.rgb(18,28,43),TEXT=Color.rgb(238,246,255),MUTED=Color.rgb(139,159,181),CYAN=Color.rgb(76,223,246),PINK=Color.rgb(244,74,194),GREEN=Color.rgb(78,217,157),YELLOW=Color.rgb(241,201,77);

    private DjDeck deckA,deckB; private DeckWidgets uiA,uiB; private TextView crossLabel,driveAccount; private VuMeterView vuA,vuB;
    private int pendingDeck=0; private long generationA=0,generationB=0;
    private final Handler ticker=new Handler(Looper.getMainLooper());
    private final ExecutorService io= Executors.newFixedThreadPool(3);
    private ActivityResultLauncher<String[]> openTrack; private ActivityResultLauncher<IntentSenderRequest> authorization;
    private AuthManager auth; private DriveRepository drive; private Runnable afterAuthorization;

    private final Runnable tickerTask=new Runnable(){@Override public void run(){refreshDeck(uiA,deckA);refreshDeck(uiB,deckB);if(vuA!=null)vuA.setLevel(deckA.visualLevel());if(vuB!=null)vuB.setLevel(deckB.visualLevel());ticker.postDelayed(this,80L);}};

    @Override protected void onCreate(@Nullable Bundle state){super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);auth=new AuthManager(this);drive=new DriveRepository(auth);
        deckA=new DjDeck(this);deckB=new DjDeck(this);deckA.setListener(()->refreshDeck(uiA,deckA));deckB.setListener(()->refreshDeck(uiB,deckB));
        openTrack=registerForActivityResult(new ActivityResultContracts.OpenDocument(),uri->{if(uri==null||pendingDeck==0)return;try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}int d=pendingDeck;pendingDeck=0;loadDeckUri(d,uri,displayName(uri));});
        authorization=registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(),result->{if(result.getResultCode()!=RESULT_OK||result.getData()==null){showDriveError(null);return;}try{AuthorizationResult ar=authResult(result.getData());auth.acceptInteractive(ar);updateDriveAccount();Runnable next=afterAuthorization;afterAuthorization=null;if(next!=null)next.run();}catch(Exception e){showDriveError(e);}});
        setContentView(buildConsole());applyCrossfader(50);updateDriveAccount();ticker.post(tickerTask);
    }

    @Override protected void onDestroy(){ticker.removeCallbacksAndMessages(null);io.shutdownNow();deckA.release();deckB.release();super.onDestroy();}

    private View buildConsole(){LinearLayout page=col();page.setBackgroundColor(BG);page.setPadding(dp(8),dp(6),dp(8),dp(6));
        LinearLayout header=row();TextView title=label("NUBE DJ",24,TEXT,true);header.addView(title);TextView ver=label("  v0.2.0  ·  CLOUD MIX",11,MUTED,false);header.addView(ver);View spacer=new View(this);header.addView(spacer,new LinearLayout.LayoutParams(0,1,1));driveAccount=label("Google Drive",10,MUTED,false);driveAccount.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);header.addView(driveAccount,new LinearLayout.LayoutParams(dp(190),dp(36)));Button library=button("☁  BIBLIOTECA",CYAN,true);library.setOnClickListener(v->new DriveLibraryDialog(this,auth,drive).show());header.addView(library,new LinearLayout.LayoutParams(dp(140),dp(36)));page.addView(header,new LinearLayout.LayoutParams(-1,dp(40)));
        LinearLayout console=row();uiA=buildDeck(deckA,"DECK A",CYAN,1);uiB=buildDeck(deckB,"DECK B",PINK,2);console.addView(uiA.root,new LinearLayout.LayoutParams(0,-1,1));console.addView(buildMixer(),new LinearLayout.LayoutParams(dp(205),-1));console.addView(uiB.root,new LinearLayout.LayoutParams(0,-1,1));page.addView(console,new LinearLayout.LayoutParams(-1,0,1));return page;}

    private DeckWidgets buildDeck(DjDeck deck,String deckName,int accent,int number){DeckWidgets w=new DeckWidgets();w.root=col();w.root.setPadding(dp(8),dp(4),dp(8),dp(4));w.root.setBackgroundColor(PANEL);
        LinearLayout top=row();TextView tag=label(deckName,15,accent,true);top.addView(tag,new LinearLayout.LayoutParams(dp(78),dp(24)));w.track=label("Sin pista",13,TEXT,true);w.track.setSingleLine(true);w.track.setEllipsize(android.text.TextUtils.TruncateAt.END);top.addView(w.track,new LinearLayout.LayoutParams(0,dp(24),1));w.root.addView(top);
        w.wave=new WaveformView(this,deck,accent);w.root.addView(w.wave,new LinearLayout.LayoutParams(-1,0,1.05f));
        LinearLayout detail=row();w.jog=new JogWheelView(this,deck,accent);detail.addView(w.jog,new LinearLayout.LayoutParams(dp(84),dp(84)));LinearLayout stats=col();stats.setPadding(dp(8),0,0,0);LinearLayout statsTop=row();w.bpm=label("--.- BPM",21,TEXT,true);statsTop.addView(w.bpm,new LinearLayout.LayoutParams(0,dp(32),1));w.time=label("00:00 / 00:00",11,MUTED,false);w.time.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);statsTop.addView(w.time,new LinearLayout.LayoutParams(dp(105),dp(32)));stats.addView(statsTop);w.tempoLabel=label("TEMPO 100.0%",11,MUTED,true);stats.addView(w.tempoLabel,new LinearLayout.LayoutParams(-1,dp(20)));w.tempo=new SeekBar(this);w.tempo.setMin(75);w.tempo.setMax(125);w.tempo.setProgress(100);tint(w.tempo,accent);w.tempo.setOnSeekBarChangeListener(new SimpleSeekListener(p->deck.setTempo(p/100f)));stats.addView(w.tempo,new LinearLayout.LayoutParams(-1,dp(30)));w.status=label("LISTO",9,accent,true);w.status.setSingleLine(true);stats.addView(w.status,new LinearLayout.LayoutParams(-1,dp(18)));detail.addView(stats,new LinearLayout.LayoutParams(0,dp(84),1));w.root.addView(detail,new LinearLayout.LayoutParams(-1,dp(86)));
        LinearLayout transport=row();Button load=button("ARCHIVO",accent,false);Button cue=button("CUE",YELLOW,false);Button set=button("SET",MUTED,false);w.play=button("PLAY",GREEN,true);load.setOnClickListener(v->chooseTrack(number));cue.setOnClickListener(v->deck.cue());set.setOnClickListener(v->deck.setCue());w.play.setOnClickListener(v->deck.togglePlay());addWeighted(transport,load);addWeighted(transport,cue);addWeighted(transport,set);addWeighted(transport,w.play);w.root.addView(transport,new LinearLayout.LayoutParams(-1,dp(42)));
        LinearLayout performance=row();w.sync=button("SYNC",accent,false);w.sync.setOnClickListener(v->{DjDeck master=number==1?deckB:deckA;if(deck.syncTo(master))setTempoUi(w,deck);});Button tap=button("TAP",MUTED,false);tap.setOnClickListener(v->deck.tapBpm());TextView pitch=label("PITCH",9,MUTED,true);pitch.setGravity(Gravity.CENTER);performance.addView(w.sync,new LinearLayout.LayoutParams(dp(74),dp(36)));performance.addView(tap,new LinearLayout.LayoutParams(dp(66),dp(36)));performance.addView(pitch,new LinearLayout.LayoutParams(dp(52),dp(36)));SeekBar vol=new SeekBar(this);vol.setMax(100);vol.setProgress(100);tint(vol,accent);vol.setOnSeekBarChangeListener(new SimpleSeekListener(p->deck.setDeckVolume(p/100f)));performance.addView(vol,new LinearLayout.LayoutParams(0,dp(36),1));w.root.addView(performance,new LinearLayout.LayoutParams(-1,dp(38)));return w;}

    private View buildMixer(){LinearLayout mix=col();mix.setPadding(dp(8),dp(5),dp(8),dp(5));mix.setBackgroundColor(PANEL2);TextView t=label("MIXER",16,TEXT,true);t.setGravity(Gravity.CENTER);mix.addView(t,new LinearLayout.LayoutParams(-1,dp(26)));
        LinearLayout meters=row();TextView a=label("A",10,CYAN,true);a.setGravity(Gravity.CENTER);TextView b=label("B",10,PINK,true);b.setGravity(Gravity.CENTER);vuA=new VuMeterView(this);vuB=new VuMeterView(this);LinearLayout va=col();va.addView(a,new LinearLayout.LayoutParams(-1,dp(18)));va.addView(vuA,new LinearLayout.LayoutParams(-1,0,1));LinearLayout vb=col();vb.addView(b,new LinearLayout.LayoutParams(-1,dp(18)));vb.addView(vuB,new LinearLayout.LayoutParams(-1,0,1));meters.addView(va,new LinearLayout.LayoutParams(0,dp(70),1));TextView db=label("VU",9,MUTED,true);db.setGravity(Gravity.CENTER);meters.addView(db,new LinearLayout.LayoutParams(dp(35),dp(70)));meters.addView(vb,new LinearLayout.LayoutParams(0,dp(70),1));mix.addView(meters,new LinearLayout.LayoutParams(-1,dp(72)));
        TextView cf=label("CROSSFADER",9,MUTED,true);cf.setGravity(Gravity.CENTER);mix.addView(cf,new LinearLayout.LayoutParams(-1,dp(18)));SeekBar cross=new SeekBar(this);cross.setMax(100);cross.setProgress(50);tint(cross,GREEN);cross.setThumbTintList(ColorStateList.valueOf(Color.WHITE));cross.setOnSeekBarChangeListener(new SimpleSeekListener(this::applyCrossfader));mix.addView(cross,new LinearLayout.LayoutParams(-1,dp(32)));crossLabel=label("A 71  ·  B 71",9,TEXT,true);crossLabel.setGravity(Gravity.CENTER);mix.addView(crossLabel,new LinearLayout.LayoutParams(-1,dp(20)));
        TextView eq=label("EQ  LOW · MID · HIGH",9,MUTED,true);eq.setGravity(Gravity.CENTER);mix.addView(eq,new LinearLayout.LayoutParams(-1,dp(18)));mix.addView(eqRow(deckA,CYAN,"A"),new LinearLayout.LayoutParams(-1,dp(58)));mix.addView(eqRow(deckB,PINK,"B"),new LinearLayout.LayoutParams(-1,dp(58)));Button center=button("CENTRAR",GREEN,false);center.setOnClickListener(v->cross.setProgress(50));mix.addView(center,new LinearLayout.LayoutParams(-1,dp(34)));return mix;}

    private View eqRow(DjDeck deck,int accent,String name){LinearLayout row=row();TextView n=label(name,11,accent,true);n.setGravity(Gravity.CENTER);row.addView(n,new LinearLayout.LayoutParams(dp(20),-1));float[] values={0,0,0};for(int i=0;i<3;i++){final int index=i;LinearLayout cell=col();TextView lab=label(i==0?"LOW":i==1?"MID":"HIGH",8,MUTED,false);lab.setGravity(Gravity.CENTER);SeekBar s=new SeekBar(this);s.setMin(-12);s.setMax(12);s.setProgress(0);tint(s,accent);s.setOnSeekBarChangeListener(new SimpleSeekListener(p->{values[index]=p;deck.setEq(values[0],values[1],values[2]);}));cell.addView(lab,new LinearLayout.LayoutParams(-1,dp(17)));cell.addView(s,new LinearLayout.LayoutParams(-1,dp(36)));row.addView(cell,new LinearLayout.LayoutParams(0,-1,1));}return row;}

    private void chooseTrack(int which){pendingDeck=which;openTrack.launch(new String[]{"audio/*"});}

    private void loadDeckUri(int which,Uri uri,String name){DjDeck deck=which==1?deckA:deckB;DeckWidgets ui=which==1?uiA:uiB;long gen=which==1?++generationA:++generationB;deck.load(uri,name);ui.track.setText(name);io.execute(()->{TrackAnalysis result=null;try{result=AudioAnalyzer.analyze(this,uri);}catch(Exception ignored){}TrackAnalysis finalResult=result;runOnUiThread(()->{if(isDestroyed())return;long current=which==1?generationA:generationB;if(current==gen)deck.setAnalysis(finalResult);});});}

    public void loadDriveTrack(int which,DriveTrack track){DjDeck deck=which==1?deckA:deckB;deck.setStatus("DESCARGANDO DESDE DRIVE…");io.execute(()->{try{File file=DriveCache.download(this,auth,track);runOnUiThread(()->loadDeckUri(which,Uri.fromFile(file),track.name));}catch(Exception e){runOnUiThread(()->deck.setStatus("ERROR DRIVE · "+shortMessage(e)));}});}

    public void connectDrive(Runnable after){afterAuthorization=after;auth.authorize(this).addOnSuccessListener(this,result->{if(result.hasResolution()){if(result.getPendingIntent()==null){showDriveError(null);return;}try{authorization.launch(new IntentSenderRequest.Builder(result.getPendingIntent().getIntentSender()).build());}catch(RuntimeException e){showDriveError(e);}}else{try{auth.acceptInteractive(result);updateDriveAccount();Runnable next=afterAuthorization;afterAuthorization=null;if(next!=null)next.run();}catch(Exception e){showDriveError(e);}}}).addOnFailureListener(this,this::showDriveError);}

    private AuthorizationResult authResult(Intent data)throws ApiException{return com.google.android.gms.auth.api.identity.Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data);}

    private void updateDriveAccount(){driveAccount.setText(auth.isConnected()?auth.accountLabel():"Drive sin conectar");if(auth.isConnected())io.execute(()->{try{String email=drive.accountEmail();auth.rememberAccount(email);runOnUiThread(()->driveAccount.setText(auth.accountLabel()));}catch(Exception ignored){}});}

    private void showDriveError(@Nullable Exception error){String msg="No se pudo conectar Google Drive.";if(error instanceof ApiException){int code=((ApiException)error).getStatusCode();msg+="\nCódigo Google: "+code+" ("+ CommonStatusCodes.getStatusCodeString(code)+")";if(code==CommonStatusCodes.DEVELOPER_ERROR)msg+="\n\nRegistra el paquete cl.fernando.nubedj y la huella SHA-1 de esta app en tu cliente OAuth de Android.";}new MaterialAlertDialogBuilder(this).setTitle("Google Drive").setMessage(msg).setPositiveButton("Aceptar",null).show();}

    private void applyCrossfader(int progress){float[] g=CrossfaderMath.equalPower(progress/100f);deckA.setCrossfadeGain(g[0]);deckB.setCrossfadeGain(g[1]);if(crossLabel!=null)crossLabel.setText(String.format(Locale.US,"A %d  ·  B %d",Math.round(g[0]*100),Math.round(g[1]*100)));}
    private void refreshDeck(DeckWidgets w,DjDeck deck){if(w==null||deck==null)return;w.track.setText(deck.getTrackName());w.time.setText(TimeFormat.ms(deck.getCurrentPosition())+" / "+TimeFormat.ms(deck.getDuration()));w.bpm.setText(deck.getBpm()==null?"--.- BPM":String.format(Locale.US,"%.1f BPM",deck.getBpm()));w.tempoLabel.setText(String.format(Locale.US,"TEMPO %.1f%%",deck.getTempo()*100f));w.status.setText(deck.getStatus());w.play.setText(deck.isPlaying()?"PAUSA":"PLAY");if(!w.tempo.isPressed())w.tempo.setProgress(Math.round(deck.getTempo()*100));w.wave.invalidate();w.jog.invalidate();}
    private void setTempoUi(DeckWidgets w,DjDeck deck){w.tempo.setProgress(Math.round(deck.getTempo()*100));}
    private String displayName(Uri uri){try(android.database.Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}return uri.getLastPathSegment()==null?"Pista":uri.getLastPathSegment();}

    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;} private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView label(String s,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private Button button(String text,int accent,boolean solid){Button b=new Button(this);b.setText(text);b.setTextSize(10);b.setAllCaps(false);b.setTextColor(solid?BG:TEXT);b.setPadding(dp(6),0,dp(6),0);GradientDrawable d=new GradientDrawable();d.setCornerRadius(dp(7));d.setColor(solid?accent:Color.rgb(24,34,49));d.setStroke(dp(1),accent);b.setBackground(d);return b;}
    private void addWeighted(LinearLayout row,View v){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.setMargins(dp(2),0,dp(2),0);row.addView(v,lp);}private void tint(SeekBar b,int color){b.setProgressTintList(ColorStateList.valueOf(color));b.setThumbTintList(ColorStateList.valueOf(color));}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}private static String shortMessage(Exception e){String s=e.getMessage();return s==null?"No se pudo cargar":s.length()>50?s.substring(0,50):s;}

    private static final class DeckWidgets{LinearLayout root;TextView track,time,bpm,tempoLabel,status;Button play,sync;SeekBar tempo;WaveformView wave;JogWheelView jog;}
    private static final class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener{interface C{void changed(int p);}private final C c;SimpleSeekListener(C c){this.c=c;}@Override public void onProgressChanged(SeekBar s,int p,boolean user){if(user)c.changed(p);}@Override public void onStartTrackingTouch(SeekBar s){}@Override public void onStopTrackingTouch(SeekBar s){c.changed(s.getProgress());}}
}
