package cl.fernando.nubedj;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

public final class JogWheelView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final DjDeck deck;
    private final int accent;
    public JogWheelView(Context c, DjDeck deck, int accent) { super(c); this.deck=deck; this.accent=accent; }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float size=Math.min(getWidth(),getHeight()), cx=getWidth()/2f, cy=getHeight()/2f, r=size*0.46f;
        p.setStyle(Paint.Style.FILL);p.setColor(0xFF05080D);c.drawCircle(cx,cy,r,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));
        for(int i=0;i<7;i++){p.setColor(i%2==0?0x445F6C7A:0x33424C58);c.drawCircle(cx,cy,r*(0.30f+i*0.09f),p);}
        p.setStyle(Paint.Style.FILL);p.setColor(0xFF1A2432);c.drawCircle(cx,cy,r*0.30f,p);
        p.setColor(accent);c.drawCircle(cx,cy,r*0.10f,p);
        double turn=(deck.getCurrentPosition()%1800L)/1800.0*Math.PI*2-Math.PI/2;
        float mx=cx+(float)Math.cos(turn)*r*0.80f,my=cy+(float)Math.sin(turn)*r*0.80f;
        p.setColor(0xFFFFFFFF);c.drawCircle(mx,my,dp(3),p);
    }
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
}
