package app.aerial.tablet;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.json.JSONArray;

/** Only this app's private settings. All writes are committed before changing the visible mode. */
final class KidsMode {
    private final SharedPreferences prefs;
    KidsMode(Context c){prefs=c.getSharedPreferences("kids-mode",Context.MODE_PRIVATE);}
    boolean active(){return prefs.getBoolean("active",false);}
    boolean configured(){return prefs.contains("pin");}
    String verifier(){return prefs.getString("pin",null);}
    boolean savePin(String encoded){return prefs.edit().putString("pin",encoded).remove("failures").remove("retryAt").commit();}
    Set<String> approved(){
        LinkedHashSet<String> result=new LinkedHashSet<>();
        try{JSONArray a=new JSONArray(prefs.getString("approved","[]"));for(int i=0;i<a.length();i++)result.add(a.getString(i));}catch(Exception ignored){}
        return Collections.unmodifiableSet(result);
    }
    boolean hasList(){return prefs.contains("approved");}
    boolean saveApproved(Set<String> packages){return prefs.edit().putString("approved",new JSONArray(packages).toString()).commit();}
    boolean setActive(boolean active){if(active&&(!configured()||approved().isEmpty()))return false;return prefs.edit().putBoolean("active",active).commit();}
    long retrySeconds(){return Math.max(0,(prefs.getLong("retryAt",0)-System.currentTimeMillis()+999)/1000);}
    boolean failed(){
        int count=Math.min(100,prefs.getInt("failures",0)+1);
        return prefs.edit().putInt("failures",count).putLong("retryAt",System.currentTimeMillis()+PinCodec.delayAfterFailure(count)).commit();
    }
    boolean authenticated(){return prefs.edit().remove("failures").remove("retryAt").commit();}
    boolean reset(){return prefs.edit().clear().commit();}
}
