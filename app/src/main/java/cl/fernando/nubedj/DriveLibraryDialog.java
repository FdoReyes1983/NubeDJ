package cl.fernando.nubedj;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DriveLibraryDialog extends Dialog {
    private static final int BG=Color.rgb(7,11,18),CARD=Color.rgb(17,25,38),TEXT=Color.rgb(238,245,253),MUTED=Color.rgb(145,164,184),CYAN=Color.rgb(76,223,246),PINK=Color.rgb(244,74,194);
    private final MainActivity host;
    private final AuthManager auth;
    private final DriveRepository repo;
    private final ExecutorService io= Executors.newSingleThreadExecutor();
    private final ArrayDeque<String> parentStack=new ArrayDeque<>(),nameStack=new ArrayDeque<>();
    private LinearLayout rows; private EditText search; private TextView location,status,account; private ProgressBar spinner; private Button more;
    private String parent="root",next=""; private boolean shared=false; private final List<DriveTrack> items=new ArrayList<>();

    public DriveLibraryDialog(MainActivity host,AuthManager auth,DriveRepository repo){super(host);this.host=host;this.auth=auth;this.repo=repo;}

    @Override protected void onCreate(Bundle savedInstanceState){super.onCreate(savedInstanceState);requestWindowFeature(Window.FEATURE_NO_TITLE);setContentView(build());Window w=getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT);}if(auth.isConnected())load(false);else showConnect();}
    @Override public void onAttachedToWindow(){super.onAttachedToWindow();Window w=getWindow();if(w!=null){w.setLayout((int)(host.getResources().getDisplayMetrics().widthPixels*0.95f),(int)(host.getResources().getDisplayMetrics().heightPixels*0.92f));w.setDimAmount(0.7f);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);}}
    @Override public void dismiss(){io.shutdownNow();super.dismiss();}

    private View build(){
        LinearLayout root=col();root.setPadding(dp(14),dp(10),dp(14),dp(10));root.setBackgroundColor(BG);
        LinearLayout top=row();TextView title=txt("BIBLIOTECA · GOOGLE DRIVE",20,TEXT,true);top.addView(title,new LinearLayout.LayoutParams(0,dp(42),1));account=txt(auth.accountLabel(),11,MUTED,false);account.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);top.addView(account,new LinearLayout.LayoutParams(dp(220),dp(42)));Button close=button("✕",MUTED);close.setOnClickListener(v->dismiss());top.addView(close,new LinearLayout.LayoutParams(dp(50),dp(40)));root.addView(top);
        LinearLayout nav=row();Button my=button("MI DRIVE",CYAN);Button sh=button("COMPARTIDOS",PINK);Button back=button("←",MUTED);my.setOnClickListener(v->{shared=false;parent="root";parentStack.clear();nameStack.clear();location.setText("Mi Drive");load(false);});sh.setOnClickListener(v->{shared=true;parent="root";parentStack.clear();nameStack.clear();location.setText("Compartido conmigo");load(false);});back.setOnClickListener(v->goBack());nav.addView(my,new LinearLayout.LayoutParams(0,dp(40),1));nav.addView(sh,new LinearLayout.LayoutParams(0,dp(40),1));nav.addView(back,new LinearLayout.LayoutParams(dp(54),dp(40)));root.addView(nav);
        location=txt("Mi Drive",13,TEXT,true);root.addView(location,new LinearLayout.LayoutParams(-1,dp(30)));
        LinearLayout sr=row();search=new EditText(host);search.setSingleLine();search.setTextColor(TEXT);search.setHintTextColor(MUTED);search.setHint("Buscar canciones o carpetas");search.setTextSize(14);search.setPadding(dp(12),0,dp(12),0);search.setBackgroundColor(CARD);search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);search.setOnEditorActionListener((v,a,e)->{if(a==EditorInfo.IME_ACTION_SEARCH){load(false);return true;}return false;});Button find=button("BUSCAR",CYAN);find.setOnClickListener(v->load(false));sr.addView(search,new LinearLayout.LayoutParams(0,dp(42),1));sr.addView(find,new LinearLayout.LayoutParams(dp(92),dp(42)));root.addView(sr);
        status=txt("",11,MUTED,false);status.setGravity(Gravity.CENTER_VERTICAL);spinner=new ProgressBar(host);spinner.setIndeterminateTintList(ColorStateList.valueOf(CYAN));LinearLayout info=row();info.addView(status,new LinearLayout.LayoutParams(0,dp(30),1));info.addView(spinner,new LinearLayout.LayoutParams(dp(26),dp(26)));root.addView(info);spinner.setVisibility(View.GONE);
        ScrollView scroll=new ScrollView(host);rows=col();scroll.addView(rows);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        more=button("CARGAR MÁS",MUTED);more.setVisibility(View.GONE);more.setOnClickListener(v->load(true));root.addView(more,new LinearLayout.LayoutParams(-1,dp(40)));
        return root;
    }

    private void showConnect(){rows.removeAllViews();TextView t=txt("Conecta tu Google Drive para buscar y cargar música directamente en Deck A o Deck B.",16,TEXT,false);t.setGravity(Gravity.CENTER);rows.addView(t,new LinearLayout.LayoutParams(-1,dp(100)));Button b=button("CONECTAR GOOGLE DRIVE",CYAN);b.setOnClickListener(v->host.connectDrive(()->{account.setText(auth.accountLabel());load(false);}));rows.addView(b,new LinearLayout.LayoutParams(-1,dp(48)));}

    private void load(boolean append){
        if(!auth.isConnected()){showConnect();return;}spinner.setVisibility(View.VISIBLE);status.setText("Consultando Drive…");if(!append){items.clear();rows.removeAllViews();next="";}
        final String p=parent,q=search.getText().toString(),token=append?next:"";final boolean s=shared;
        io.execute(()->{try{DriveRepository.Page page=repo.list(p,s,q,token);host.runOnUiThread(()->{if(!isShowing())return;spinner.setVisibility(View.GONE);items.addAll(page.items);next=page.next;render();});}catch(Exception e){host.runOnUiThread(()->{if(!isShowing())return;spinner.setVisibility(View.GONE);status.setText("No se pudo abrir Drive · "+safe(e.getMessage()));});}});
    }

    private void render(){rows.removeAllViews();status.setText(items.size()+" elementos");for(DriveTrack t:items){LinearLayout line=row();line.setPadding(dp(10),dp(4),dp(6),dp(4));line.setBackgroundColor(CARD);LinearLayout labels=col();TextView n=txt((t.folder?"▣  ":"♫  ")+t.name,13,TEXT,true);n.setSingleLine(true);TextView sub=txt(t.subtitle(),10,MUTED,false);labels.addView(n);labels.addView(sub);line.addView(labels,new LinearLayout.LayoutParams(0,dp(54),1));if(t.folder){Button open=button("ABRIR",MUTED);open.setOnClickListener(v->openFolder(t));line.addView(open,new LinearLayout.LayoutParams(dp(74),dp(46)));line.setOnClickListener(v->openFolder(t));}else{Button a=button("A",CYAN),b=button("B",PINK);a.setOnClickListener(v->host.loadDriveTrack(1,t));b.setOnClickListener(v->host.loadDriveTrack(2,t));line.addView(a,new LinearLayout.LayoutParams(dp(46),dp(46)));line.addView(b,new LinearLayout.LayoutParams(dp(46),dp(46)));}LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(58));lp.setMargins(0,0,0,dp(4));rows.addView(line,lp);}more.setVisibility(next==null||next.isEmpty()?View.GONE:View.VISIBLE);}
    private void openFolder(DriveTrack t){parentStack.push(parent);nameStack.push(location.getText().toString());parent=t.id;shared=false;search.setText("");location.setText(t.name);load(false);}
    private void goBack(){if(parentStack.isEmpty())return;parent=parentStack.pop();location.setText(nameStack.pop());search.setText("");load(false);}

    private LinearLayout col(){LinearLayout l=new LinearLayout(host);l.setOrientation(LinearLayout.VERTICAL);return l;}private LinearLayout row(){LinearLayout l=new LinearLayout(host);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView txt(String s,int sp,int color,boolean bold){TextView v=new TextView(host);v.setText(s);v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private Button button(String s,int color){Button b=new Button(host);b.setText(s);b.setTextColor(TEXT);b.setTextSize(11);b.setAllCaps(false);b.setBackgroundTintList(ColorStateList.valueOf(color));return b;}
    private int dp(int v){return Math.round(v*host.getResources().getDisplayMetrics().density);}private static String safe(String s){return s==null?"Error de conexión":s.length()>90?s.substring(0,90):s;}
}
