package app.aerial.tablet;

import android.content.*;
import android.content.pm.*;
import android.graphics.drawable.Drawable;
import java.text.Collator;
import java.util.*;

/** Catalog is queried off the main thread. IDs are components, so multi-activity apps remain distinct. */
final class AppCatalog {
    static final class Entry {
        final String id, label, pkg;
        final ComponentName component;
        final Drawable icon;
        Entry(ResolveInfo r, PackageManager pm) {
            component=new ComponentName(r.activityInfo.packageName,r.activityInfo.name);
            id=component.flattenToString();pkg=component.getPackageName();
            label=r.loadLabel(pm).toString();icon=r.loadIcon(pm);
        }
    }
    static ArrayList<Entry> load(Context c) {
        PackageManager pm=c.getPackageManager();
        Intent query=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        ArrayList<Entry> result=new ArrayList<>(); Set<String> seen=new HashSet<>();
        for(ResolveInfo r:pm.queryIntentActivities(query,0)) {
            if(c.getPackageName().equals(r.activityInfo.packageName))continue;
            Entry e=new Entry(r,pm);if(seen.add(e.id))result.add(e);
        }
        Collator collator=Collator.getInstance(Locale.getDefault());
        result.sort((a,b)->collator.compare(a.label,b.label)); return result;
    }
}
