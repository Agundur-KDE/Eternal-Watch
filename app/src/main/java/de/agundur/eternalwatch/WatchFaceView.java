// SPDX-License-Identifier: GPL-3.0-only
package de.agundur.eternalwatch;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import de.agundur.eternalwatch.core.GameSnapshot;

final class WatchFaceView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private String clock="--:--";
    private int seconds=-1;
    WatchFaceView(Context c) { super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES); }
    void update(GameSnapshot data,long now,boolean showSeconds) {
        String next=data==null?"--:--":data.clock(now,showSeconds);
        int nextSeconds=data==null?-1:data.currentSeconds(now);
        if(!next.equals(clock) || nextSeconds!=seconds) {
            clock=next;seconds=nextSeconds;setContentDescription(getContext().getString(R.string.clock_accessibility,clock));invalidate();
        }
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density=getResources().getDisplayMetrics().density;
        float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())/2f-18*density;
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*density);paint.setColor(Color.rgb(62,78,67));
        canvas.drawCircle(cx,cy,r,paint);
        if(seconds>=0) {
            paint.setColor(Color.rgb(215,188,129));paint.setStrokeWidth(4*density);paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawArc(cx-r,cy-r,cx+r,cy+r,-90,seconds*360f/21600,false,paint);
        }
        paint.setStrokeCap(Paint.Cap.BUTT);paint.setStrokeWidth(density);
        for(int i=0;i<36;i++) {
            double angle=Math.toRadians(i*10-90);float len=(i%6==0?13:5)*density;
            paint.setColor(i%6==0?Color.rgb(215,188,129):Color.rgb(65,83,71));
            canvas.drawLine(cx+(float)Math.cos(angle)*(r-8*density),cy+(float)Math.sin(angle)*(r-8*density),cx+(float)Math.cos(angle)*(r-8*density-len),cy+(float)Math.sin(angle)*(r-8*density-len),paint);
        }
        paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));paint.setTextSize(50*density);paint.setColor(Color.rgb(246,242,231));
        if(paint.measureText(clock)>r*1.5f) paint.setTextSize(paint.getTextSize()*r*1.5f/paint.measureText(clock));
        canvas.drawText(clock,cx,cy-paint.getFontMetrics().ascent*.25f,paint);
        paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));paint.setTextSize(10*density);paint.setColor(Color.rgb(164,184,169));
        canvas.drawText(getContext().getString(R.string.clock_label),cx,cy+34*density,paint);
    }
}
