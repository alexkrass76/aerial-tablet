package app.aerial.tablet;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Hills and palettes adapted from Aerial's Gradients.kt, MIT, (c) 2026 pengchujin. */
public final class AerialWallpaper extends View {
    public static final String[] NAMES = {"Рассвет", "Ночь", "Туман", "Сумерки"};
    private static final int[][] COLORS = {
        {0xFF7B8FD6,0xFFE9B7C9,0xFFFFD9B8,0xFFB7A3D8,0xFFC9B2D9,0xFF8F7FC4,0xFFA38FC8,0xFF6A5AA8,0xFF7B66AE,0xFF433878,0xFF3A2F66},
        {0xFF04081C,0xFF14285A,0xFF2F4D86,0xFF1F3566,0xFF253C70,0xFF162851,0xFF182B55,0xFF0D1B3A,0xFF0B1733,0xFF050C1F,0xFF040917},
        {0xFFA9C9D8,0xFFDBE8E3,0xFFF3EFE2,0xFFA8C2BB,0xFFB9D0C6,0xFF7EA79F,0xFF8FB3A7,0xFF4F8178,0xFF5D8B7D,0xFF2A5249,0xFF24463D},
        {0xFF16244F,0xFFC25B6B,0xFFF59B5B,0xFF7D3F63,0xFF8A4762,0xFF552D5A,0xFF5B2F55,0xFF33204A,0xFF2C1C40,0xFF170F2A,0xFF120B20}
    };
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private Bitmap cached;
    private Bitmap frosted;
    private int preset;
    public AerialWallpaper(Context c, int preset) { super(c); this.preset = preset; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    public void setPreset(int n) { preset = Math.max(0, Math.min(3, n)); clear(); invalidate(); }
    private void clear() { if (cached != null) { cached.recycle(); cached = null; } if(frosted!=null){frosted.recycle();frosted=null;} }
    /** Aerial Images.frost: three separable box blurs, radius 7, shared by every glass surface. */
    public Bitmap frosted() {
        if(frosted!=null)return frosted;
        int sw=getWidth()>0?getWidth():getResources().getDisplayMetrics().widthPixels;
        int sh=getHeight()>0?getHeight():getResources().getDisplayMetrics().heightPixels;
        float scale=320f/Math.max(sw,sh);int w=Math.max(1,Math.round(sw*scale)),h=Math.max(1,Math.round(sh*scale));
        Bitmap small=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);drawScene(new Canvas(small),w,h,preset);
        int[] pixels=new int[w*h],tmp=new int[w*h];small.getPixels(pixels,0,w,0,0,w,h);
        for(int i=0;i<3;i++){blurPass(pixels,tmp,w,h,7,true);blurPass(tmp,pixels,w,h,7,false);}
        small.setPixels(pixels,0,w,0,0,w,h);frosted=small.copy(Bitmap.Config.ARGB_8888,false);small.recycle();return frosted;
    }
    private static void blurPass(int[] src,int[] dst,int w,int h,int radius,boolean horizontal) {
        int lines=horizontal?h:w,length=horizontal?w:h,divisor=radius*2+1;
        for(int line=0;line<lines;line++){
            int a=0,r=0,g=0,b=0;
            for(int k=-radius;k<=radius;k++){int j=Math.max(0,Math.min(length-1,k));int c=src[horizontal?line*w+j:j*w+line];a+=c>>>24;r+=(c>>>16)&255;g+=(c>>>8)&255;b+=c&255;}
            for(int i=0;i<length;i++){
                dst[horizontal?line*w+i:i*w+line]=(a/divisor<<24)|(r/divisor<<16)|(g/divisor<<8)|b/divisor;
                int left=Math.max(0,i-radius),right=Math.min(length-1,i+radius+1);
                int old=src[horizontal?line*w+left:left*w+line],next=src[horizontal?line*w+right:right*w+line];
                a+=(next>>>24)-(old>>>24);r+=((next>>>16)&255)-((old>>>16)&255);g+=((next>>>8)&255)-((old>>>8)&255);b+=(next&255)-(old&255);
            }
        }
    }
    @Override protected void onSizeChanged(int w,int h,int ow,int oh) { clear(); }
    @Override protected void onDraw(Canvas canvas) {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        if (cached == null) {
            cached = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
            drawScene(new Canvas(cached),getWidth(),getHeight(),preset);
        }
        canvas.drawBitmap(cached,0,0,paint);
    }
    @Override protected void onDetachedFromWindow() { clear(); super.onDetachedFromWindow(); }
    public static void drawScene(Canvas c,float w,float h,int preset) {
        int[] p=COLORS[preset]; Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        ink.setShader(new LinearGradient(0,0,0,h*.75f,new int[]{p[0],p[1],p[2]},null,Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,ink);
        int glow=preset==0?0xB2FFD6AA:preset==1?0x5978AAFF:preset==2?0xB2FFFAEB:0xA6FFA05A;
        ink.setShader(new RadialGradient(w*.7f,h*.6f,w*.45f,glow,glow&0xFFFFFF,Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,ink);
        for(int i=0;i<4;i++) {
            float base=h*(.45f+i*.11f), amp=h*(.09f-i*.006f);
            ink.setShader(new LinearGradient(0,base-h*.1f,0,h,p[3+i*2],p[4+i*2],Shader.TileMode.CLAMP));
            c.drawPath(ridge(w,h,base,amp,7+i*13),ink);
        }
        // Aerial's foreground scrim keeps white labels legible over its light wallpapers.
        ink.setShader(new LinearGradient(0,0,0,h,new int[]{0x280C1028,0x100C1028,0xB00B0E22},new float[]{0,.42f,1},Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,ink);
    }
    private static Path ridge(float w,float h,float base,float amp,int seed) {
        long s=seed; float[] ph=new float[3],f=new float[3];
        for(int i=0;i<3;i++) {s=s*16807%2147483647;ph[i]=s/2147483647f*6;}
        s=s*16807%2147483647;f[0]=1.3f+s/2147483647f;
        s=s*16807%2147483647;f[1]=3+s/2147483647f*2;
        s=s*16807%2147483647;f[2]=7+s/2147483647f*3;
        Path path=new Path();path.moveTo(0,h);
        for(int i=0;i<=240;i++) {float t=i/240f;float y=base+amp*(float)(Math.sin(t*f[0]+ph[0])*.6+Math.sin(t*f[1]+ph[1])*.28+Math.sin(t*f[2]+ph[2])*.12);path.lineTo(t*w,y);}
        path.lineTo(w,h);path.close();return path;
    }
}
