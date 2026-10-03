package cl.fernando.nubedj;

import org.json.JSONObject;
import java.util.Locale;

public final class DriveTrack {
    public final String id,name,mime,resourceKey;
    public final long size;
    public final boolean folder,downloadable;
    public DriveTrack(String id,String name,String mime,String resourceKey,long size,boolean folder,boolean downloadable){
        this.id=id;this.name=name;this.mime=mime;this.resourceKey=resourceKey;this.size=size;this.folder=folder;this.downloadable=downloadable;
    }
    public static DriveTrack fromJson(JSONObject json){
        if(json==null)return null;
        JSONObject shortcut=json.optJSONObject("shortcutDetails");
        String mime=json.optString("mimeType"),id=json.optString("id"),key=json.optString("resourceKey");
        if(DriveRules.SHORTCUT.equals(mime)){
            if(shortcut==null)return null;id=shortcut.optString("targetId");mime=shortcut.optString("targetMimeType");key=shortcut.optString("targetResourceKey");
        }
        if(!DriveRules.validId(id))return null;
        boolean folder=DriveRules.FOLDER.equals(mime);
        String name=json.optString("name","Sin nombre");
        if(!folder&&!DriveRules.isAudio(name,mime))return null;
        JSONObject caps=json.optJSONObject("capabilities");
        return new DriveTrack(id,name,mime,key,json.optLong("size",-1),folder,caps==null||caps.optBoolean("canDownload",true));
    }
    public String subtitle(){if(folder)return "Carpeta";String ext=DriveRules.extension(name).toUpperCase(Locale.ROOT);return size>0?ext+String.format(Locale.forLanguageTag("es-CL")," · %.1f MB",size/1048576.0):ext;}
}
