package cl.fernando.nubedj;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class DriveRepository {
    public static final class Page { public final List<DriveTrack> items; public final String next; Page(List<DriveTrack> i,String n){items=i;next=n;} }
    private final AuthManager auth;
    public DriveRepository(AuthManager auth){this.auth=auth;}

    public Page list(String parent,boolean shared,String search,String pageToken)throws IOException{
        Uri.Builder u=Uri.parse("https://www.googleapis.com/drive/v3/files").buildUpon()
                .appendQueryParameter("q",DriveRules.query(parent,shared,search))
                .appendQueryParameter("pageSize","100").appendQueryParameter("orderBy","folder,name_natural")
                .appendQueryParameter("spaces","drive").appendQueryParameter("supportsAllDrives","true")
                .appendQueryParameter("includeItemsFromAllDrives","true")
                .appendQueryParameter("fields","nextPageToken,files(id,name,mimeType,size,resourceKey,capabilities(canDownload),shortcutDetails(targetId,targetMimeType,targetResourceKey))");
        if(pageToken!=null&&!pageToken.isEmpty())u.appendQueryParameter("pageToken",pageToken);
        JSONObject json=get(u.build().toString(),"");List<DriveTrack> out=new ArrayList<>();JSONArray files=json.optJSONArray("files");
        if(files!=null)for(int i=0;i<files.length();i++){DriveTrack t=DriveTrack.fromJson(files.optJSONObject(i));if(t!=null)out.add(t);}return new Page(out,json.optString("nextPageToken"));
    }

    public String accountEmail()throws IOException{JSONObject j=get("https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)","");JSONObject u=j.optJSONObject("user");return u==null?"":u.optString("emailAddress");}

    private JSONObject get(String url,String resourceHeader)throws IOException{
        boolean refreshed=false;
        for(int attempt=0;attempt<3;attempt++){
            String token=auth.accessToken();HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
            c.setConnectTimeout(15000);c.setReadTimeout(25000);c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("Accept","application/json");
            if(!resourceHeader.isEmpty())c.setRequestProperty("X-Goog-Drive-Resource-Keys",resourceHeader);
            try{int status=c.getResponseCode();if(status==401&&!refreshed){auth.invalidate(token);refreshed=true;continue;}if(status<200||status>=300)throw new IOException("Drive respondió "+status);
                try(InputStream in=c.getInputStream();ByteArrayOutputStream b=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)b.write(buf,0,n);return new JSONObject(b.toString(StandardCharsets.UTF_8.name()));}
                catch(org.json.JSONException e){throw new IOException("Respuesta inválida de Drive",e);}finally{c.disconnect();}
            }catch(IOException e){c.disconnect();throw e;}
        }throw new AuthManager.NeedsAuthorization();
    }
}
