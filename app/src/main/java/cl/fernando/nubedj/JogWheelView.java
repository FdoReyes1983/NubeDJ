package cl.fernando.nubedj;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/** DJ-style jog: center = scratch/scrub, outer rim = pitch bend/nudge. */
public final class JogWheelView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final DjDeck deck;
    private final int accent;
    private float lastAngle;
    private boolean touching;
    private boolean scratchMode;

    public JogWheelView(Context c, DjDeck deck, int accent) {
        super(c);
        this.deck=deck;
        this.accent=accent;
        setClickable(true);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float size=Math.min(getWidth(),getHeight()), cx=getWidth()/2f, cy=getHeight()/2f, r=size*0.47f;

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF05070B);
        c.drawCircle(cx,cy,r,p);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(2));
        p.setColor(0xFF6C7784);
        c.drawCircle(cx,cy,r*0.98f,p);
        p.setStrokeWidth(dp(1));
        for(int i=0;i<28;i++){
            double a=i*Math.PI*2/28.0;
            float x1=cx+(float)Math.cos(a)*r*0.86f;
            float y1=cy+(float)Math.sin(a)*r*0.86f;
            float x2=cx+(float)Math.cos(a)*r*0.96f;
            float y2=cy+(float)Math.sin(a)*r*0.96f;
            p.setColor(i%4==0?0xFFB8C1CA:0xFF4D5661);
            c.drawLine(x1,y1,x2,y2,p);
        }
        for(int i=0;i<9;i++){
            p.setColor(i%2==0?0x443D4650:0x332D353E);
            c.drawCircle(cx,cy,r*(0.28f+i*0.065f),p);
        }

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF1B222B);
        c.drawCircle(cx,cy,r*0.31f,p);
        p.setColor(0xFF0D1218);
        c.drawCircle(cx,cy,r*0.22f,p);
        p.setColor(accent);
        c.drawCircle(cx,cy,r*0.075f,p);

        double turn=(deck.getCurrentPosition()%1800L)/1800.0*Math.PI*2-Math.PI/2;
        float mx=cx+(float)Math.cos(turn)*r*0.72f,my=cy+(float)Math.sin(turn)*r*0.72f;
        p.setColor(0xFFFFFFFF);
        c.drawCircle(mx,my,dp(2.7f),p);

        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(dp(7.5f));
        p.setColor(touching ? accent : 0xFF8B98A6);
        c.drawText(touching ? (scratchMode?"SCRATCH":"NUDGE") : "JOG",cx,cy+r*0.08f,p);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float cx=getWidth()/2f, cy=getHeight()/2f;
        float dx=e.getX()-cx,dy=e.getY()-cy;
        float radius=(float)Math.sqrt(dx*dx+dy*dy);
        float maxR=Math.min(getWidth(),getHeight())*0.47f;
        float angle=(float)Math.atan2(dy,dx);
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                lastAngle=angle;
                touching=true;
                scratchMode=radius < maxR*0.76f;
                if(scratchMode)deck.beginScratch();else deck.beginNudge();
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if(!touching)return true;
                float delta=angle-lastAngle;
                while(delta>Math.PI)delta-=Math.PI*2f;
                while(delta<-Math.PI)delta+=Math.PI*2f;
                if(scratchMode){
                    // One full turn ~= 5.2 seconds. Gives finer control than v0.2.1.
                    long deltaMs=Math.round(delta/(Math.PI*2f)*5200f);
                    if(deltaMs!=0)deck.scratchBy(deltaMs);
                }else{
                    // Rim behaves as temporary pitch bend, like nudging a platter.
                    float bend=(float)(delta/(Math.PI/5.0));
                    deck.nudge(Math.max(-0.08f,Math.min(0.08f,bend*0.08f)));
                }
                lastAngle=angle;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if(touching){if(scratchMode)deck.endScratch();else deck.endNudge();}
                touching=false;
                getParent().requestDisallowInterceptTouchEvent(false);
                invalidate();
                performClick();
                return true;
            default:return super.onTouchEvent(e);
        }
    }

    @Override public boolean performClick(){super.performClick();return true;}
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
}
