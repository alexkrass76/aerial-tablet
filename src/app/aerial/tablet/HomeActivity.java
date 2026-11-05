package app.aerial.tablet;

import android.app.*;
import android.app.role.RoleManager;
import android.content.*;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.AudioManager;
import android.net.*;
import android.os.*;
import android.provider.*;
import android.text.*;
import android.text.format.DateFormat;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.JSONArray;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** A touch-first HOME activity. Uses public Android APIs and requires no accessibility service. */
public final class HomeActivity extends Activity {
    private static final int WHITE=0xFFF9F9FD, SECONDARY=0xCDEDEDF7;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private ArrayList<AppCatalog.Entry> apps=new ArrayList<>();
    private final ArrayList<String> favorites=new ArrayList<>();
    private android.content.SharedPreferences prefs;
    private KidsMode kids;
    private boolean parentPanel;
    private final Set<String> parentOnlyPackages=new HashSet<>(Arrays.asList("com.android.settings","app.aerial.tablet"));
    private FrameLayout root, overlay, dockContainer;
    private AlertDialog appContextMenu;
    private LinearLayout home;
    private ScrollView homeScroll;
    private AerialWallpaper wallpaper;
    private TextView time, date, status;
    private Typeface inter;
    private boolean landscape, destroyed;
    private int battery=-1, preset=0, navigationInset;
    private boolean charging;
    private int loadToken;
    private float touchX,touchY;
    private long touchStarted;
    private boolean trackSwipe;
    private final BroadcastReceiver updates=new BroadcastReceiver() {
        @Override public void onReceive(Context c,Intent i) {
            if(Intent.ACTION_BATTERY_CHANGED.equals(i.getAction())) {
                int scale=i.getIntExtra(BatteryManager.EXTRA_SCALE,100);
                battery=scale>0?i.getIntExtra(BatteryManager.EXTRA_LEVEL,0)*100/scale:-1;
                charging=i.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0;
            }
            updateClock();updateStatus();
        }
    };
    private final BroadcastReceiver packages=new BroadcastReceiver() {
        @Override public void onReceive(Context c,Intent i) { loadApps(); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=getSharedPreferences("aerial-tablet",MODE_PRIVATE);
        kids=new KidsMode(this);
        preset=Math.max(0,Math.min(3,prefs.getInt("wallpaper",0)));
        try { inter=Typeface.createFromAsset(getAssets(),"inter.ttf"); } catch(Exception e) { inter=Typeface.DEFAULT; }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN|WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setNavigationBarColor(0x33101527);
        getWindow().setNavigationBarContrastEnforced(false);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        applyBrightness();
        try {JSONArray a=new JSONArray(prefs.getString("favorites","[]"));for(int n=0;n<a.length();n++)if(!favorites.contains(a.getString(n))&&favorites.size()<6)favorites.add(a.getString(n));}catch(Exception ignored){}
        buildHome();loadApps();
        IntentFilter f=new IntentFilter();
        for(String action:new String[]{Intent.ACTION_TIME_TICK,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,Intent.ACTION_DATE_CHANGED,Intent.ACTION_BATTERY_CHANGED,ConnectivityManager.CONNECTIVITY_ACTION}) f.addAction(action);
        registerReceiver(updates,f);
        IntentFilter p=new IntentFilter();p.addAction(Intent.ACTION_PACKAGE_ADDED);p.addAction(Intent.ACTION_PACKAGE_REMOVED);p.addAction(Intent.ACTION_PACKAGE_CHANGED);p.addDataScheme("package");registerReceiver(packages,p);
    }
    @Override protected void onResume() {super.onResume();updateClock();updateStatus();}
    @Override protected void onPause(){if(parentPanel)dismissPanel();super.onPause();}
    @Override protected void onDestroy() {destroyed=true;unregisterReceiver(updates);unregisterReceiver(packages);worker.shutdownNow();super.onDestroy();}
    @Override public void onConfigurationChanged(Configuration config) {super.onConfigurationChanged(config);dismissAppMenu();dismissPanel();buildHome();}
    @Override protected void onNewIntent(Intent intent) {super.onNewIntent(intent);setIntent(intent);dismissAppMenu();dismissPanel();if(homeScroll!=null)homeScroll.smoothScrollTo(0,0);}
    @Override public void onBackPressed() {if(overlay!=null)dismissPanel();else if(homeScroll!=null)homeScroll.smoothScrollTo(0,0);}
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){trackSwipe=overlay==null;touchX=event.getX();touchY=event.getY();touchStarted=event.getEventTime();}
        if(action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_CANCEL)trackSwipe=false;
        if(action==MotionEvent.ACTION_UP&&trackSwipe&&overlay==null){
            trackSwipe=false;float rise=touchY-event.getY();
            if(rise>dp(80)&&Math.abs(event.getX()-touchX)<rise*.6f&&event.getEventTime()-touchStarted<750){
                MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();showDrawer();return true;
            }
        }
        return super.dispatchTouchEvent(event);
    }
    private int dp(float n) {return Math.round(n*getResources().getDisplayMetrics().density);}
    private int screenDp() {return Math.round(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density);}
    private LinearLayout column() {LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row() {LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private LinearLayout.LayoutParams lp(int w,int h) {return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private void gap(LinearLayout parent,int h) {parent.addView(new View(this),lp(1,h));}
    private TextView text(String value,float size,int weight,int color) {
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setTypeface(Typeface.create(inter,weight,false));t.setIncludeFontPadding(false);t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    private GradientDrawable plate(int color,float radius,int stroke) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;
    }
    private Drawable glass(View view,float radius,FrostedGlassDrawable.Material material,int wash) {
        return new FrostedGlassDrawable(view,wallpaper,dp(radius),material,wash);
    }
    private void interactive(View v,int color,float radius) {
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x28FFFFFF),glass(v,radius,FrostedGlassDrawable.Material.CONTROL,color),plate(WHITE,radius,0)));
        v.setClickable(true);v.setFocusable(true);v.setElevation(dp(1));
    }
    private TextView button(String label,Runnable action) {
        TextView b=text(label,15,550,WHITE);b.setGravity(Gravity.CENTER);b.setMinHeight(dp(52));b.setPadding(dp(20),dp(12),dp(20),dp(12));interactive(b,0x20FFFFFF,18);b.setOnClickListener(v->action.run());return b;
    }
    private void buildHome() {
        landscape=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        dockContainer=null;
        root=new FrameLayout(this);wallpaper=new AerialWallpaper(this,preset);root.addView(wallpaper,new FrameLayout.LayoutParams(-1,-1));
        homeScroll=new ScrollView(this);homeScroll.setFillViewport(true);homeScroll.setClipToPadding(false);homeScroll.setVerticalScrollBarEnabled(false);homeScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        home=column();int side=landscape?56:40;home.setPadding(dp(side),dp(28),dp(side),dp(38)+navigationInset);
        homeScroll.addView(home,new ScrollView.LayoutParams(-1,-2));root.addView(homeScroll,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int n=Build.VERSION.SDK_INT>=30?insets.getInsets(WindowInsets.Type.navigationBars()).bottom:insets.getStableInsetBottom();
            int keyboard=Build.VERSION.SDK_INT>=30?insets.getInsets(WindowInsets.Type.ime()).bottom:insets.getSystemWindowInsetBottom();
            if(n!=navigationInset){navigationInset=n;home.setPadding(home.getPaddingLeft(),home.getPaddingTop(),home.getPaddingRight(),dp(38)+n);}
            if(dockContainer!=null){FrameLayout.LayoutParams dockBounds=(FrameLayout.LayoutParams)dockContainer.getLayoutParams();int bottom=n+dp(18);if(dockBounds.bottomMargin!=bottom){dockBounds.bottomMargin=bottom;dockContainer.setLayoutParams(dockBounds);}}
            // Fullscreen windows do not use adjustResize: explicitly keep a modal above the IME.
            if(overlay!=null)overlay.setPadding(0,0,0,Math.max(0,keyboard-n));
            return insets;
        });root.requestApplyInsets();
        LinearLayout top=row();
        TextView brand=text("AERIAL",13,650,0xDFFFFFFF);brand.setLetterSpacing(.2f);top.addView(brand,lp(-2,52));
        TextView tablet=text(kids.active()?"  /  ДЕТСКИЙ РЕЖИМ":"  /  TABLET",11,500,0xAAFFFFFF);tablet.setLetterSpacing(.12f);top.addView(tablet,lp(-2,52));
        top.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        status=button("",()->{if(kids.active())showParentGate(true);else showControls();});status.setContentDescription(kids.active()?"Выход из детского режима":"Сеть, заряд и быстрые настройки");top.addView(status,lp(-2,52));home.addView(top,lp(-1,52));
        LinearLayout hero=column();hero.setGravity(Gravity.CENTER_VERTICAL);hero.setPadding(dp(8),0,0,dp(8));
        date=text("",landscape?23:24,500,WHITE);hero.addView(date,lp(-1,38));
        time=text("",landscape?137:150,300,WHITE);time.setLetterSpacing(-.055f);time.setShadowLayer(dp(20),0,dp(4),0x35000000);time.setContentDescription("Текущее время");
        hero.addView(time,lp(-1,landscape?160:182));if(!kids.active()){hero.setOnClickListener(v->open(new Intent(AlarmClock.ACTION_SHOW_ALARMS)));hero.setFocusable(true);hero.setContentDescription("Часы. Открыть будильники");}
        home.addView(hero,lp(-1,landscape?280:370));
        buildDock();
        updateClock();updateStatus();
    }
    private void buildDock() {
        ArrayList<AppCatalog.Entry> selected=new ArrayList<>();if(kids.active()){for(AppCatalog.Entry e:visibleApps()){if(selected.size()==6)break;selected.add(e);}}else for(String id:favorites){AppCatalog.Entry e=find(id);if(e!=null)selected.add(e);}
        int slot=Math.min(88,(screenDp()-104)/(selected.size()+1));
        LinearLayout dock=row();dock.setPadding(dp(12),dp(10),dp(12),dp(10));
        for(AppCatalog.Entry e:selected)dock.addView(appTile(e,true),lp(slot,84));
        if(!selected.isEmpty()){View separator=new View(this);separator.setBackgroundColor(0x35FFFFFF);LinearLayout.LayoutParams line=lp(1,44);line.setMargins(dp(12),0,dp(12),0);dock.addView(separator,line);}
        ImageView library=new ImageView(this);library.setImageResource(R.drawable.ic_apps);library.setScaleType(ImageView.ScaleType.CENTER_INSIDE);library.setPadding(dp(20),dp(20),dp(20),dp(20));
        interactive(library,0x16FFFFFF,22);library.setElevation(0);library.setContentDescription("Приложения");library.setTooltipText("Приложения");library.setOnClickListener(v->showDrawer());dock.addView(library,lp(slot,84));
        dockContainer=new FrameLayout(this);dockContainer.setBackground(glass(dockContainer,32,FrostedGlassDrawable.Material.DOCK,0));dockContainer.setClipToOutline(true);dockContainer.setElevation(dp(18));dockContainer.setContentDescription("Док избранного");
        dockContainer.addView(dock,new FrameLayout.LayoutParams(-2,-2));
        int dockWidth=(selected.size()+1)*slot+24+(selected.isEmpty()?0:25);
        FrameLayout.LayoutParams anchor=new FrameLayout.LayoutParams(dp(dockWidth),dp(104),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);anchor.bottomMargin=navigationInset+dp(18);root.addView(dockContainer,anchor);
    }
    private void loadApps() {
        int token=++loadToken;
        worker.execute(()->{try {ArrayList<AppCatalog.Entry> list=AppCatalog.load(this);Set<String> homePackages=new HashSet<>();for(android.content.pm.ResolveInfo r:getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0))homePackages.add(r.activityInfo.packageName);runOnUiThread(()->{
            if(destroyed||token!=loadToken)return;apps=list;parentOnlyPackages.addAll(homePackages);
            if(!prefs.contains("favorites")) {
                for(String pkg:new String[]{"com.android.chrome","com.google.android.youtube","com.google.android.apps.photos","com.google.android.apps.nbu.files","com.android.vending","com.android.settings","com.google.android.calendar"}) {
                    if(favorites.size()>=6)break;
                    for(AppCatalog.Entry e:apps)if(e.pkg.equals(pkg)){favorites.add(e.id);break;}
                }
                saveFavorites();
            }
            // Remove entries only after a successful catalog query; never overwrite on an error.
            favorites.removeIf(id->find(id)==null);saveFavorites();dismissPanel();buildHome();
        });}catch(Exception e){runOnUiThread(()->{if(!destroyed)toast("Не удалось прочитать список приложений");});}});
    }
    private AppCatalog.Entry find(String id) {for(AppCatalog.Entry e:apps)if(e.id.equals(id))return e;return null;}
    private void saveFavorites(){prefs.edit().putString("favorites",new JSONArray(favorites).toString()).apply();}
    private View appTile(AppCatalog.Entry e,boolean dock) {
        LinearLayout tile=column();tile.setGravity(Gravity.CENTER);tile.setPadding(dp(6),dp(8),dp(6),dp(6));
        int iconSize=dock?68:66;
        ImageView icon=new ImageView(this);Drawable d=e.icon.getConstantState()!=null?e.icon.getConstantState().newDrawable(getResources()).mutate():e.icon;
        icon.setImageDrawable(d);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setBackground(plate(0x08FFFFFF,18,0));icon.setClipToOutline(true);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);tile.addView(icon,lp(iconSize,iconSize));
        if(!dock){gap(tile,10);TextView label=text(e.label,13,500,WHITE);label.setGravity(Gravity.CENTER);label.setMaxLines(2);label.setEllipsize(TextUtils.TruncateAt.END);label.setShadowLayer(dp(3),0,dp(1),0x60000000);tile.addView(label,lp(-1,36));}else tile.setTooltipText(e.label);
        tile.setBackground(new RippleDrawable(ColorStateList.valueOf(0x35FFFFFF),plate(0x00FFFFFF,22,0),plate(WHITE,22,0)));tile.setClickable(true);tile.setFocusable(true);tile.setContentDescription(e.label);tile.setOnClickListener(v->launch(e));tile.setOnLongClickListener(v->{appMenu(e);return true;});
        return tile;
    }
    private void fillGrid(LinearLayout container,List<AppCatalog.Entry> list,boolean drawer) {
        container.removeAllViews();int cols=Math.max(3,Math.min(8,(screenDp()-(landscape?112:80))/132));
        if(drawer)cols=Math.max(3,Math.min(7,(screenDp()-112)/132));
        if(list.isEmpty()){TextView empty=text(apps.isEmpty()?"Приложения загружаются…":"Ничего не найдено",17,500,SECONDARY);empty.setGravity(Gravity.CENTER);container.addView(empty,lp(-1,130));return;}
        for(int start=0;start<list.size();start+=cols){LinearLayout r=row();r.setGravity(Gravity.TOP);
            for(int i=0;i<cols;i++){int index=start+i;View cell=index<list.size()?appTile(list.get(index),false):new View(this);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(132),1);p.setMargins(dp(3),0,dp(3),dp(12));r.addView(cell,p);}container.addView(r,lp(-1,-2));}
    }
    private ArrayList<AppCatalog.Entry> visibleApps(){
        if(!kids.active())return new ArrayList<>(apps);
        Set<String> allowed=kids.approved();ArrayList<AppCatalog.Entry> result=new ArrayList<>();
        for(AppCatalog.Entry e:apps)if(allowed.contains(e.pkg)&&!parentOnlyPackages.contains(e.pkg))result.add(e);return result;
    }
    private void launch(AppCatalog.Entry e) {
        Intent i=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(e.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        open(i);
    }
    private void open(Intent i) {
        if(kids.active()){ComponentName target=i.getComponent();if(target==null||parentOnlyPackages.contains(target.getPackageName())||!kids.approved().contains(target.getPackageName())){toast("Это приложение недоступно в детском режиме");return;}}
        try {startActivity(i);}catch(ActivityNotFoundException|SecurityException e){toast("Приложение недоступно");}
    }
    private void appMenu(AppCatalog.Entry e) {
        if(kids.active())return;
        dismissAppMenu();
        boolean pinned=favorites.contains(e.id);ArrayList<String> options=new ArrayList<>();options.add(pinned?"Убрать из избранного":"Добавить в избранное");
        if(pinned&&favorites.indexOf(e.id)>0)options.add("Переместить левее");options.add("О приложении");
        appContextMenu=new AlertDialog.Builder(this).setTitle(e.label).setItems(options.toArray(new String[0]),(dialog,which)->{
            String action=options.get(which);
            if(action.equals("О приложении")){open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+e.pkg)));return;}
            if(action.equals("Переместить левее")){int n=favorites.indexOf(e.id);Collections.swap(favorites,n,n-1);}
            else if(pinned)favorites.remove(e.id);
            else {if(favorites.size()>=6){toast("В доке шесть мест. Сначала уберите одно приложение.");return;}favorites.add(e.id);}
            saveFavorites();dismissPanel();buildHome();
        }).setNegativeButton("Отмена",null).show();
        styleDialog(appContextMenu);
    }
    private void dismissAppMenu(){if(appContextMenu!=null){appContextMenu.dismiss();appContextMenu=null;}}
    private void updateClock() {
        if(time==null)return;Date now=new Date();time.setText(DateFormat.getTimeFormat(this).format(now));
        date.setText(new SimpleDateFormat("EEEE, d MMMM",Locale.getDefault()).format(now));
    }
    private void updateStatus() {
        if(status==null)return;String network="Нет сети";
        try {ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);NetworkCapabilities nc=cm.getNetworkCapabilities(cm.getActiveNetwork());
            if(nc!=null)network=nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)?"Wi-Fi":nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)?"Ethernet":"Мобильная сеть";
        }catch(Exception ignored){}
        if(kids.active()){status.setText("Родителям"+(battery>=0?"  ·  "+battery+"%":""));return;}
        status.setText(network+"   ·   "+(battery>=0?battery+"%":"—")+(charging?" · зарядка":""));
    }
    private void dismissPanel() {
        parentPanel=false;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        View focused=getCurrentFocus();if(focused!=null)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focused.getWindowToken(),0);
        if(overlay!=null){root.removeView(overlay);overlay=null;}if(homeScroll!=null)homeScroll.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);if(dockContainer!=null)dockContainer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }
    private LinearLayout panel(String title,boolean wide) {
        dismissPanel();homeScroll.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);dockContainer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        overlay=new FrameLayout(this);overlay.setElevation(dp(40));overlay.setBackgroundColor(wide?0x59000000:0x73000000);overlay.setOnClickListener(v->dismissPanel());overlay.setAccessibilityPaneTitle(title);
        LinearLayout panel=column();panel.setPadding(dp(28),dp(24),dp(28),dp(24)+navigationInset);panel.setBackground(glass(panel,30,FrostedGlassDrawable.Material.PANEL,0));panel.setClipToOutline(true);panel.setClickable(true);panel.setElevation(dp(24));
        FrameLayout.LayoutParams bounds=new FrameLayout.LayoutParams(wide?-1:dp(Math.min(screenDp()-32,500)),-1,wide?Gravity.CENTER:Gravity.RIGHT);bounds.setMargins(dp(wide?24:12),dp(20),dp(20),dp(12));overlay.addView(panel,bounds);root.addView(overlay,new FrameLayout.LayoutParams(-1,-1));root.requestApplyInsets();
        LinearLayout header=row();TextView t=text(title,26,600,WHITE);header.addView(t,new LinearLayout.LayoutParams(0,dp(56),1));TextView close=button("Готово",this::dismissPanel);header.addView(close,lp(-2,52));panel.addView(header,lp(-1,56));gap(panel,20);
        panel.setAlpha(0);panel.setTranslationY(dp(18));panel.animate().alpha(1).translationY(0).setDuration(180).start();return panel;
    }
    private void showDrawer() {
        LinearLayout p=panel(kids.active()?"Разрешённые приложения":"Все приложения",true);
        EditText search=new EditText(this);search.setTextColor(WHITE);search.setHintTextColor(0xA0FFFFFF);search.setSingleLine(true);search.setTextSize(18);search.setTypeface(inter);search.setHint("Название приложения");search.setContentDescription("Поиск по названию приложения");search.setPadding(dp(20),0,dp(20),0);search.setBackground(glass(search,18,FrostedGlassDrawable.Material.CONTROL,0x10FFFFFF));p.addView(search,lp(-1,58));gap(p,20);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);LinearLayout grid=column();scroll.addView(grid,new ScrollView.LayoutParams(-1,-2));p.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));fillGrid(grid,visibleApps(),true);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){String q=normalize(s.toString().trim());ArrayList<AppCatalog.Entry> filtered=new ArrayList<>();for(AppCatalog.Entry e:visibleApps())if(normalize(e.label).contains(q)||e.pkg.toLowerCase(Locale.ROOT).contains(q))filtered.add(e);fillGrid(grid,filtered,true);scroll.scrollTo(0,0);}public void afterTextChanged(Editable e){}});
        p.setFocusableInTouchMode(true);p.requestFocus();
    }
    private static String normalize(String s){return s.toLowerCase(Locale.getDefault()).replace('ё','е');}
    private void showControls() {
        if(kids.active()){showParentGate(true);return;}
        LinearLayout p=panel("Пункт управления",false);ScrollView scroll=new ScrollView(this);LinearLayout body=column();scroll.addView(body);p.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        body.addView(button("Детский режим",()->showParentGate(false)),lp(-1,60));gap(body,12);
        body.addView(button("Wi-Fi и подключение",()->open(new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))),lp(-1,60));gap(body,12);
        body.addView(button("Bluetooth",()->open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))),lp(-1,60));gap(body,28);
        AudioManager audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        body.addView(text("Громкость мультимедиа",15,550,WHITE),lp(-1,36));SeekBar volume=new SeekBar(this);volume.setMax(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));volume.setProgress(audio.getStreamVolume(AudioManager.STREAM_MUSIC));volume.setContentDescription("Громкость мультимедиа");volume.setProgressTintList(ColorStateList.valueOf(0xFFBFC5FF));
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int n,boolean user){if(user)audio.setStreamVolume(AudioManager.STREAM_MUSIC,n,0);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});body.addView(volume,lp(-1,56));gap(body,18);
        body.addView(text("Яркость оболочки",15,550,WHITE),lp(-1,36));SeekBar brightness=new SeekBar(this);brightness.setMax(100);brightness.setProgress(prefs.getInt("brightness",70));brightness.setContentDescription("Яркость оболочки");brightness.setProgressTintList(ColorStateList.valueOf(0xFFBFC5FF));
        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int n,boolean user){if(user){prefs.edit().putInt("brightness",n).apply();applyBrightness();}}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});body.addView(brightness,lp(-1,56));
        TextView note=text("Действует на домашнем экране. Яркость остальных приложений — в настройках экрана.",12,450,SECONDARY);note.setMaxLines(3);body.addView(note,lp(-1,60));gap(body,18);
        body.addView(button("Настройки экрана",()->open(new Intent(Settings.ACTION_DISPLAY_SETTINGS))),lp(-1,60));gap(body,12);
        body.addView(button("Оформление",this::showAppearance),lp(-1,60));gap(body,12);
        body.addView(button("Настройки Android",()->open(new Intent(Settings.ACTION_SETTINGS))),lp(-1,60));
    }
    private LinearLayout scrollBody(LinearLayout parent){ScrollView scroll=new ScrollView(this);LinearLayout body=column();scroll.addView(body);parent.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));return body;}
    private void paragraph(LinearLayout body,String value){TextView note=text(value,14,450,SECONDARY);note.setLineSpacing(dp(4),1);body.addView(note,lp(-1,-2));gap(body,20);}
    private int availableApproved(){Set<String> allowed=kids.approved(),seen=new HashSet<>();for(AppCatalog.Entry e:apps)if(allowed.contains(e.pkg)&&!parentOnlyPackages.contains(e.pkg))seen.add(e.pkg);return seen.size();}
    private void showKidsSettings(){
        if(kids.active()){showParentGate(true);return;}
        LinearLayout p=panel("Детский режим",false);parentPanel=true;LinearLayout body=scrollBody(p);
        paragraph(body,"Только выбранные приложения на домашнем экране. Для выхода понадобится ваш PIN.");
        body.addView(button("Разрешённые приложения · "+availableApproved(),this::showApprovedPicker),lp(-1,60));gap(body,12);
        body.addView(button(kids.configured()?"Изменить PIN":"Задать PIN",this::showPinSetup),lp(-1,60));gap(body,24);
        TextView enable=button("Включить детский режим",()->{
            if(availableApproved()==0||!kids.setActive(true)){toast("Сначала выберите приложения и задайте PIN");return;}
            dismissAppMenu();dismissPanel();buildHome();
        });boolean ready=kids.configured()&&availableApproved()>0;enable.setEnabled(ready);enable.setAlpha(ready?1:.45f);body.addView(enable,lp(-1,60));gap(body,16);
        if(!ready)paragraph(body,"Для включения сохраните список приложений и задайте PIN из 4–8 цифр.");
        paragraph(body,"Ограничивает домашний экран Aerial. Системная шторка, недавние приложения и переходы из других приложений Android остаются доступны.");
        if(kids.configured())body.addView(button("Сбросить настройки режима",()->{
            appContextMenu=new AlertDialog.Builder(this).setTitle("Сбросить детский режим?").setMessage("PIN и список разрешённых приложений будут удалены. Обычное избранное сохранится.").setNegativeButton("Отмена",null).setPositiveButton("Сбросить",(d,w)->{
                if(!kids.reset()){toast("Не удалось сохранить настройки");return;}dismissPanel();buildHome();showKidsSettings();
            }).show();styleDialog(appContextMenu);
        }),lp(-1,60));
    }
    private void showApprovedPicker(){
        LinearLayout p=panel("Разрешённые приложения",true);parentPanel=true;
        Set<String> draft=new LinkedHashSet<>(kids.approved());
        if(!kids.hasList())draft.addAll(Arrays.asList("com.google.android.apps.youtube.kids","com.bimiboo.birthday","com.bimiboo.playandlearn"));
        Set<String> candidates=new HashSet<>();for(AppCatalog.Entry e:apps)if(!parentOnlyPackages.contains(e.pkg))candidates.add(e.pkg);draft.retainAll(candidates);
        TextView count=text("Выбрано: "+draft.size(),15,500,SECONDARY);p.addView(count,lp(-1,36));
        LinearLayout body=scrollBody(p);Set<String> seen=new HashSet<>();
        for(AppCatalog.Entry e:apps){if(parentOnlyPackages.contains(e.pkg)||!seen.add(e.pkg))continue;
            LinearLayout item=row();item.setPadding(dp(12),dp(6),dp(12),dp(6));item.setBackground(glass(item,18,FrostedGlassDrawable.Material.CONTROL,0x08FFFFFF));
            ImageView icon=new ImageView(this);icon.setImageDrawable(e.icon.getConstantState()!=null?e.icon.getConstantState().newDrawable(getResources()):e.icon);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);item.addView(icon,lp(42,42));
            CheckBox box=new CheckBox(this);box.setText(e.label);box.setTextColor(WHITE);box.setTextSize(15);box.setTypeface(inter);box.setPadding(dp(16),0,dp(8),0);box.setMinHeight(dp(60));box.setChecked(draft.contains(e.pkg));box.setContentDescription("Разрешить "+e.label);box.setButtonTintList(ColorStateList.valueOf(0xFFD5D8FF));
            box.setOnCheckedChangeListener((b,checked)->{if(checked)draft.add(e.pkg);else draft.remove(e.pkg);count.setText("Выбрано: "+draft.size());});item.addView(box,new LinearLayout.LayoutParams(0,dp(64),1));body.addView(item,lp(-1,76));gap(body,8);
        }
        gap(p,16);p.addView(button("Сохранить список",()->{if(!kids.saveApproved(draft)){toast("Не удалось сохранить список");return;}showKidsSettings();}),lp(-1,58));
    }
    private EditText pinField(String hint){
        EditText field=new EditText(this);field.setTextColor(WHITE);field.setHintTextColor(SECONDARY);field.setHint(hint);field.setContentDescription(hint);field.setTextSize(20);field.setTypeface(inter);field.setSingleLine(true);field.setSaveEnabled(false);field.setSelectAllOnFocus(true);
        field.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);field.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789"));field.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE|android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);field.setPadding(dp(18),0,dp(18),0);field.setBackground(glass(field,18,FrostedGlassDrawable.Material.CONTROL,0x10FFFFFF));return field;
    }
    private void showPinSetup(){
        LinearLayout p=panel(kids.configured()?"Новый PIN":"Задать PIN",false);parentPanel=true;getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);LinearLayout body=scrollBody(p);
        paragraph(body,"Задайте код из 4–8 цифр для выхода из детского режима. Запомните его: восстановление кода внутри оболочки не предусмотрено.");
        EditText first=pinField("Новый PIN"),second=pinField("Повторите PIN");first.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);body.addView(first,lp(-1,58));gap(body,12);body.addView(second,lp(-1,58));gap(body,16);
        TextView error=text("",14,500,0xFFFFD1C8);body.addView(error,lp(-1,56));TextView save=button("Сохранить PIN",()->{});body.addView(save,lp(-1,58));
        save.setOnClickListener(v->{
            if(!save.isEnabled())return;
            char[] a=first.getText().toString().toCharArray(),b=second.getText().toString().toCharArray();
            boolean valid=PinCodec.valid(a)&&Arrays.equals(a,b);Arrays.fill(b,'\0');
            if(!valid){Arrays.fill(a,'\0');error.setText("Введите одинаковый PIN из 4–8 цифр в обоих полях");return;}
            save.setEnabled(false);save.setText("Сохраняем…");first.setEnabled(false);second.setEnabled(false);
            worker.execute(()->{String encoded=null;try{encoded=PinCodec.encode(a);}catch(Exception ignored){}finally{Arrays.fill(a,'\0');}final String result=encoded;
                runOnUiThread(()->{if(destroyed||!p.isAttachedToWindow())return;first.getText().clear();second.getText().clear();
                    if(result!=null&&kids.savePin(result)){showKidsSettings();return;}
                    save.setEnabled(true);save.setText("Сохранить PIN");first.setEnabled(true);second.setEnabled(true);error.setText("Не удалось сохранить PIN. Попробуйте ещё раз.");
                });
            });
        });second.setOnEditorActionListener((view,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){save.performClick();return true;}return false;});
        p.setFocusableInTouchMode(true);p.requestFocus();
    }
    private void showParentGate(boolean exit){
        if(!kids.configured()){if(kids.active()){toast("Не удалось прочитать настройки PIN");return;}showKidsSettings();return;}
        LinearLayout p=panel(exit?"Родителям":"Введите PIN",false);parentPanel=true;getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);LinearLayout body=scrollBody(p);
        paragraph(body,exit?"Введите родительский PIN, чтобы вернуться к обычному домашнему экрану.":"Введите PIN для изменения детского режима.");
        EditText field=pinField("PIN");body.addView(field,lp(-1,58));gap(body,16);TextView message=text("",14,500,0xFFFFD1C8);body.addView(message,lp(-1,66));TextView verify=button(exit?"Выйти из детского режима":"Продолжить",()->{});body.addView(verify,lp(-1,58));boolean[] busy={false};
        Runnable ticker=new Runnable(){public void run(){if(!p.isAttachedToWindow())return;long left=kids.retrySeconds();verify.setEnabled(!busy[0]&&left==0);if(left>0){message.setText("Слишком много попыток. Повторите через "+left+" с");p.postDelayed(this,1000);}else if(message.getText().toString().startsWith("Слишком много"))message.setText("Можно повторить попытку");}};p.post(ticker);
        verify.setOnClickListener(v->{
            if(busy[0])return;if(kids.retrySeconds()>0){ticker.run();return;}
            char[] entered=field.getText().toString().toCharArray();if(!PinCodec.valid(entered)){Arrays.fill(entered,'\0');message.setText("Введите PIN из 4–8 цифр");return;}
            busy[0]=true;verify.setEnabled(false);field.setEnabled(false);message.setText("Проверяем…");String stored=kids.verifier();
            worker.execute(()->{boolean accepted;try{accepted=PinCodec.verify(entered,stored);}finally{Arrays.fill(entered,'\0');}runOnUiThread(()->{
                if(destroyed||!p.isAttachedToWindow())return;busy[0]=false;field.setEnabled(true);field.getText().clear();
                if(accepted){
                    if(!kids.authenticated()||(exit&&!kids.setActive(false))){message.setText("Не удалось сохранить настройки. Повторите попытку.");ticker.run();return;}
                    dismissPanel();if(exit)buildHome();else showKidsSettings();
                }else{if(!kids.failed()){message.setText("Не удалось сохранить проверку PIN");verify.setEnabled(false);return;}message.setText("Неверный PIN");ticker.run();}
            });});
        });field.setOnEditorActionListener((view,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){verify.performClick();return true;}return false;});p.setFocusableInTouchMode(true);p.requestFocus();
    }
    private void styleDialog(AlertDialog dialog){Window w=dialog.getWindow();if(w!=null){View decor=w.getDecorView();w.setBackgroundDrawable(glass(decor,28,FrostedGlassDrawable.Material.PANEL,0));decor.setClipToOutline(true);w.setDimAmount(.35f);}}
    private void applyBrightness() {WindowManager.LayoutParams a=getWindow().getAttributes();a.screenBrightness=prefs.contains("brightness")?.08f+prefs.getInt("brightness",70)/100f*.92f:-1;getWindow().setAttributes(a);}
    private void showAppearance() {
        if(kids.active()){showParentGate(true);return;}
        LinearLayout p=panel("Оформление",false);ScrollView scroll=new ScrollView(this);LinearLayout body=column();scroll.addView(body);p.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        body.addView(text("Обои Aerial",18,600,WHITE),lp(-1,40));gap(body,12);
        for(int i=0;i<4;i++){final int n=i;LinearLayout item=row();item.setPadding(dp(10),dp(10),dp(16),dp(10));interactive(item,preset==i?0x30FFFFFF:0x0CFFFFFF,20);
            AerialWallpaper preview=new AerialWallpaper(this,i);preview.setBackground(plate(0,12,0));preview.setClipToOutline(true);item.addView(preview,lp(110,66));TextView name=text(AerialWallpaper.NAMES[i]+(preset==i?"  ·  выбрано":""),16,500,WHITE);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,dp(66),1);np.leftMargin=dp(16);item.addView(name,np);item.setOnClickListener(v->{preset=n;prefs.edit().putInt("wallpaper",n).apply();dismissPanel();buildHome();showAppearance();});body.addView(item,lp(-1,86));gap(body,12);}
        gap(body,18);body.addView(button("Сделать домашним экраном",this::requestHome),lp(-1,58));gap(body,14);
        body.addView(button("Приложения по умолчанию",()->open(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))),lp(-1,58));gap(body,24);
        TextView about=text("Aerial Tablet 1.3\nИнтерфейс по мотивам Aerial UI\nРаботает без сети · без рекламы",13,450,SECONDARY);about.setMaxLines(4);body.addView(about,lp(-1,76));
    }
    private void requestHome() {
        RoleManager rm=getSystemService(RoleManager.class);
        if(rm!=null&&rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
            if(rm.isRoleHeld(RoleManager.ROLE_HOME)){toast("Aerial уже выбран домашним экраном");return;}
            try{startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME),10);return;}catch(Exception ignored){}
        }
        open(new Intent(Settings.ACTION_HOME_SETTINGS));
    }
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
}
