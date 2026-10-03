package cl.fernando.nubedj;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class DriveCache {
    private DriveCache(){}
    public static File download(Context context,AuthManager auth,DriveTrack track)throws IOException{
        File dir=new File(context.getCacheDir(),"drive-audio");if(!dir.exists()&&!dir.mkdirs())throw new IOException("No se pudo crear caché");
        String ext=DriveRules.extension(track.name);File target=new File(dir,track.id+(ext.isEmpty()?"":"."+ext));
        if(target.isFile()&&target.length()>1024)return target;
        File tmp=new File(target.getAbsolutePath()+".part");if(tmp.exists())tmp.delete();
        Uri url=Uri.parse("https://www.googleapis.com/drive/v3/files").buildUpon().appendPath(track.id).appendQueryParameter("alt","media").appendQueryParameter("supportsAllDrives","true").build();
        for(int attempt=0;attempt<2;attempt++){
            String token=auth.accessToken();HttpURLConnection c=(HttpURLConnection)new URL(url.toString()).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(45000);c.setRequestProperty("Authorization","Bearer "+token);
            if(track.resourceKey!=null&&!track.resourceKey.isEmpty())c.setRequestProperty("X-Goog-Drive-Resource-Keys",track.id+"/"+track.resourceKey);
            int status=c.getResponseCode();if(status==401&&attempt==0){c.disconnect();auth.invalidate(token);continue;}if(status<200||status>=300){c.disconnect();throw new IOException("No se pudo descargar desde Drive ("+status+")");}
            try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(tmp)){byte[] buf=new byte[64*1024];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);}finally{c.disconnect();}
            if(!tmp.renameTo(target)){tmp.delete();throw new IOException("No se pudo guardar la pista");}return target;
        }throw new AuthManager.NeedsAuthorization();
    }
}
