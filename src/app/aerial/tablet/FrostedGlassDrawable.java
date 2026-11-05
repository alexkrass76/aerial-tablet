package app.aerial.tablet;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * Native port of Aerial's LiquidGlass.kt, HomeChrome.kt and SmoothCorners.kt.
 * MIT, copyright (c) 2026 pengchujin. Uses the original tints, edge light and 0.6 smoothing.
 * A small immutable wallpaper texture is shared; text and icons never enter the blur.
 */
public final class FrostedGlassDrawable extends Drawable {
    public enum Material { DOCK, PANEL, CONTROL }
    private final View anchor;
    private final AerialWallpaper wallpaper;
    private final float radius,rim;
    private final int wash;
    private final float edgeGain,sheenGain;
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint sheen=new Paint(Paint.ANTI_ALIAS_FLAG),edge=new Paint(Paint.ANTI_ALIAS_FLAG),highlight=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix=new Matrix();
    private final int[] position=new int[2],origin=new int[2];
    private Bitmap texture;
    private BitmapShader shader;
    private Path shape=new Path(),outline=new Path();
    private int opacity=255;

    public FrostedGlassDrawable(View anchor,AerialWallpaper wallpaper,float radius,Material material,int wash) {
        this.anchor=anchor;this.wallpaper=wallpaper;this.radius=radius;this.wash=wash;
        // Aerial's nested fills have quieter edges than the sheet enclosing them.
        edgeGain=material==Material.CONTROL?.25f:1f;sheenGain=material==Material.CONTROL?.4f:1f;
        rim=1.2f*anchor.getResources().getDisplayMetrics().density;
        float gain=material==Material.DOCK?.8f:.48f,lift=material==Material.DOCK?30f:14f;
        fill.setColorFilter(new ColorMatrixColorFilter(new float[]{gain,0,0,0,lift,0,gain,0,0,lift,0,0,gain,0,lift,0,0,0,1,0}));
        edge.setStyle(Paint.Style.STROKE);edge.setStrokeWidth(rim);highlight.setColor(wash);
    }
    @Override protected void onBoundsChange(Rect bounds) {
        RectF r=new RectF(bounds);shape=smoothRect(r,radius);r.inset(rim/2,rim/2);outline=smoothRect(r,Math.max(0,radius-rim/2));
        float w=Math.max(1,bounds.width()),h=Math.max(1,bounds.height());
        sheen.setShader(new LinearGradient(bounds.left,bounds.top,bounds.left,bounds.top+h,new int[]{0x1AFFFFFF,0x00FFFFFF},new float[]{0,.5f},Shader.TileMode.CLAMP));
        edge.setShader(new LinearGradient(bounds.left,bounds.top,bounds.left+w,bounds.top+h,new int[]{0x8CFFFFFF,0x1AFFFFFF,0x0FFFFFFF,0x4DFFFFFF},new float[]{0,.35f,.7f,1},Shader.TileMode.CLAMP));
    }
    @Override public void draw(Canvas canvas) {
        if(getBounds().isEmpty())return;
        Bitmap latest=wallpaper.frosted();
        if(latest!=texture){texture=latest;shader=new BitmapShader(texture,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);fill.setShader(shader);}
        anchor.getLocationOnScreen(position);wallpaper.getLocationOnScreen(origin);
        int w=wallpaper.getWidth()>0?wallpaper.getWidth():anchor.getResources().getDisplayMetrics().widthPixels;
        int h=wallpaper.getHeight()>0?wallpaper.getHeight():anchor.getResources().getDisplayMetrics().heightPixels;
        matrix.setScale(w/(float)texture.getWidth(),h/(float)texture.getHeight());matrix.postTranslate(origin[0]-position[0],origin[1]-position[1]);shader.setLocalMatrix(matrix);
        fill.setAlpha(opacity);sheen.setAlpha(Math.round(opacity*sheenGain));edge.setAlpha(Math.round(opacity*edgeGain));highlight.setAlpha(Color.alpha(wash)*opacity/255);
        canvas.drawPath(shape,fill);if(Color.alpha(wash)>0)canvas.drawPath(shape,highlight);canvas.drawPath(shape,sheen);canvas.drawPath(outline,edge);
    }
    @Override public void setAlpha(int alpha){opacity=alpha;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){fill.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    @Override public void getOutline(Outline result){result.setRoundRect(getBounds(),radius);result.setAlpha(1f);}

    private static Path smoothRect(RectF rect,float radius) {
        Path path=new Path();Corner c=new Corner(radius,Math.min(rect.width(),rect.height())/2);
        path.moveTo(rect.left+c.p,rect.top);c.add(path,rect.right,rect.top,1,0,0,1);c.add(path,rect.right,rect.bottom,0,1,-1,0);c.add(path,rect.left,rect.bottom,-1,0,0,-1);c.add(path,rect.left,rect.top,0,-1,1,0);path.close();return path;
    }
    /** Exact continuous-corner geometry from Aerial, with room-aware smoothing. */
    private static final class Corner {
        final float p,r,a,b,c,d,arc;
        Corner(float radius,float budget){
            float s=.6f;if(radius>0&&(1+s)*radius>budget)s=Math.max(0,Math.min(s,budget/radius-1));
            r=Math.max(0,Math.min(radius,budget));p=Math.min((1+s)*r,budget);arc=(float)(Math.PI/2*(1-s));
            float arcLength=(float)(Math.sin(arc/2)*r*Math.sqrt(2)),alpha=(float)(Math.PI/2-arc)/2,p3p4=(float)(r*Math.tan(alpha/2)),beta=(float)Math.PI/4*s;
            c=(float)(p3p4*Math.cos(beta));d=(float)(c*Math.tan(beta));b=(p-arcLength-c-d)/3;a=2*b;
        }
        void add(Path path,float vx,float vy,float ux,float uy,float xx,float xy){
            if(r<=0){path.lineTo(vx,vy);return;}path.lineTo(vx-ux*p,vy-uy*p);
            cubic(path,vx,vy,ux,uy,xx,xy,-p+a,0,-p+a+b,0,-p+a+b+c,d);
            float f0=(float)-Math.PI/4-arc/2,f1=(float)-Math.PI/4+arc/2,k=(float)(4f/3*Math.tan(arc/4)*r);
            float e0x=-r+r*(float)Math.cos(f0),e0y=r+r*(float)Math.sin(f0),e1x=-r+r*(float)Math.cos(f1),e1y=r+r*(float)Math.sin(f1);
            cubic(path,vx,vy,ux,uy,xx,xy,e0x-k*(float)Math.sin(f0),e0y+k*(float)Math.cos(f0),e1x+k*(float)Math.sin(f1),e1y-k*(float)Math.cos(f1),e1x,e1y);
            cubic(path,vx,vy,ux,uy,xx,xy,0,p-a-b,0,p-a,0,p);
        }
        private void cubic(Path path,float vx,float vy,float ux,float uy,float xx,float xy,float x1,float y1,float x2,float y2,float x3,float y3){path.cubicTo(vx+ux*x1+xx*y1,vy+uy*x1+xy*y1,vx+ux*x2+xx*y2,vy+uy*x2+xy*y2,vx+ux*x3+xx*y3,vy+uy*x3+xy*y3);}
    }
}
