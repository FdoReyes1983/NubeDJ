package cl.fernando.nubedj;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Robust onset/autocorrelation BPM estimator with half/double-time normalization. */
public final class BpmEstimator {
    private BpmEstimator() {}

    public static Double estimate(float[] amplitude,double samplesPerSecond){
        if(amplitude==null||samplesPerSecond<=0||amplitude.length<samplesPerSecond*12)return null;
        float[] onset=onsetEnvelope(amplitude);
        List<Double> candidates=new ArrayList<>();
        Double full=estimateWindow(onset,0,onset.length,samplesPerSecond);if(full!=null)candidates.add(full);else{Double raw=estimateWindow(amplitude,0,amplitude.length,samplesPerSecond);if(raw!=null)candidates.add(raw);}
        int window=(int)Math.round(samplesPerSecond*24.0),step=(int)Math.round(samplesPerSecond*12.0);
        if(window>0){for(int s=0;s+window<=onset.length;s+=Math.max(1,step)){Double b=estimateWindow(onset,s,s+window,samplesPerSecond);if(b!=null)candidates.add(b);}}
        Double pulse=peakTempo(amplitude,samplesPerSecond);if(pulse==null)pulse=peakTempo(onset,samplesPerSecond);
        if(candidates.isEmpty())return pulse==null?null:Math.round(pulse*10.0)/10.0;
        List<Double> folded=new ArrayList<>();for(double b:candidates){if(b>=65&&b<=190)folded.add(b);}if(folded.isEmpty())return pulse==null?null:Math.round(pulse*10.0)/10.0;
        Collections.sort(folded);double median=folded.get(folded.size()/2);
        double sum=0;int n=0;for(double b:folded){double ratio=Math.max(b,median)/Math.min(b,median);if(ratio<1.055){sum+=b;n++;}}
        double autoBpm=n==0?median:sum/n;

        // A second, independent estimate from median onset-to-onset intervals resolves many 1/2x and 2x ambiguities.
        double bpm=autoBpm;
        if(pulse!=null){
            double close=Math.max(autoBpm,pulse)/Math.min(autoBpm,pulse);
            if(close<1.08)bpm=(autoBpm+pulse)/2.0;
            else if(Math.abs(close-2.0)<0.14)bpm=pulse;
        }
        return Math.round(bpm*10.0)/10.0;
    }

    private static Double peakTempo(float[] x,double rate){
        double mean=0;for(float v:x)mean+=v;mean/=Math.max(1,x.length);double var=0;for(float v:x){double d=v-mean;var+=d*d;}double sd=Math.sqrt(var/Math.max(1,x.length));double threshold=mean+0.55*sd;
        int minGap=Math.max(1,(int)Math.round(rate*0.24));List<Integer> peaks=new ArrayList<>();int last=-minGap;
        for(int i=2;i<x.length-2;i++){
            if(x[i]<threshold||x[i]<x[i-1]||x[i]<x[i+1])continue;
            if(i-last<minGap){if(!peaks.isEmpty()&&x[i]>x[last]){peaks.set(peaks.size()-1,i);last=i;}continue;}
            peaks.add(i);last=i;
        }
        if(peaks.size()<8)return null;List<Double> intervals=new ArrayList<>();for(int i=1;i<peaks.size();i++){double sec=(peaks.get(i)-peaks.get(i-1))/rate;if(sec>=0.30&&sec<=0.95)intervals.add(sec);}if(intervals.size()<6)return null;Collections.sort(intervals);double sec=intervals.get(intervals.size()/2);double bpm=60.0/sec;return bpm>=65&&bpm<=190?bpm:null;
    }

    private static float[] onsetEnvelope(float[] amplitude){
        float[] log=new float[amplitude.length];for(int i=0;i<amplitude.length;i++)log[i]=(float)Math.log1p(Math.max(0f,amplitude[i])*12f);
        float[] onset=new float[amplitude.length];float fast=log[0],slow=log[0];
        for(int i=1;i<log.length;i++){fast=0.72f*fast+0.28f*log[i];slow=0.97f*slow+0.03f*log[i];onset[i]=Math.max(0f,fast-slow);}
        float max=0;for(float v:onset)max=Math.max(max,v);if(max>0)for(int i=0;i<onset.length;i++)onset[i]/=max;
        float[] sm=onset.clone();for(int i=2;i<onset.length-2;i++)sm[i]=(onset[i-2]+2*onset[i-1]+4*onset[i]+2*onset[i+1]+onset[i+2])/10f;
        return sm;
    }

    private static Double estimateWindow(float[] x,int from,int to,double rate){
        if(to-from<rate*8)return null;final int minBpm=65,maxBpm=190;int minLag=Math.max(1,(int)Math.floor(rate*60.0/maxBpm));int maxLag=Math.max(minLag+1,(int)Math.ceil(rate*60.0/minBpm));
        int best=-1;double bestScore=0;
        for(int lag=minLag;lag<=maxLag;lag++){
            double bpm=rate*60.0/lag;double score=corr(x,from,to,lag);
            if(lag*2<to-from)score+=0.55*corr(x,from,to,lag*2);
            if(lag/2>=minLag)score+=0.16*corr(x,from,to,Math.max(1,lag/2));
            // Typical dance/pop range gets only a very small preference, not a hard bias.
            if(bpm>=82&&bpm<=155)score*=1.025;
            if(score>bestScore){bestScore=score;best=lag;}
        }
        if(best<=0||bestScore<0.04)return null;
        double delta=0;if(best>minLag&&best<maxLag){double y1=scoreAt(x,from,to,best-1),y2=scoreAt(x,from,to,best),y3=scoreAt(x,from,to,best+1);double den=y1-2*y2+y3;if(Math.abs(den)>1e-9)delta=0.5*(y1-y3)/den;delta=Math.max(-0.5,Math.min(0.5,delta));}
        return rate*60.0/(best+delta);
    }

    private static double scoreAt(float[] x,int from,int to,int lag){double s=corr(x,from,to,lag);if(lag*2<to-from)s+=0.55*corr(x,from,to,lag*2);return s;}
    private static double corr(float[] x,int from,int to,int lag){if(lag<=0||from+lag>=to)return 0;double sum=0,a2=0,b2=0;for(int i=from+lag;i<to;i++){double a=x[i],b=x[i-lag];sum+=a*b;a2+=a*a;b2+=b*b;}return a2==0||b2==0?0:sum/Math.sqrt(a2*b2);}
}
