package cl.fernando.nubedj;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class DriveRules {
    public static final String FOLDER = "application/vnd.google-apps.folder";
    public static final String SHORTCUT = "application/vnd.google-apps.shortcut";
    private static final Set<String> AUDIO = new HashSet<>(Arrays.asList("mp3","m4a","aac","flac","wav","ogg","opus"));
    private DriveRules() {}
    public static boolean validId(String value) { return value != null && !value.isEmpty() && value.matches("[A-Za-z0-9_-]+"); }
    public static String escapeQuery(String text) { return text.replace("\\", "\\\\").replace("'", "\\'"); }
    public static String query(String parent, boolean shared, String search) {
        String base = "trashed = false and " + (shared ? "sharedWithMe = true" : "'" + (validId(parent) ? parent : "root") + "' in parents");
        if (search != null && !search.trim().isEmpty()) base += " and name contains '" + escapeQuery(search.trim()) + "'";
        return base;
    }
    public static String extension(String name) { int i=name==null?-1:name.lastIndexOf('.'); return i<0?"":name.substring(i+1).toLowerCase(Locale.ROOT); }
    public static boolean isAudio(String name,String mime){return (mime!=null&&mime.startsWith("audio/"))||AUDIO.contains(extension(name));}
    public static String title(String name){if(name==null)return "Sin nombre";int i=name.lastIndexOf('.');return i>0&&AUDIO.contains(extension(name))?name.substring(0,i):name;}
}
