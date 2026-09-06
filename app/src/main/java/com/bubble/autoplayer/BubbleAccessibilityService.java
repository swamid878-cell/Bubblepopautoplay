package com.bubble.autoplayer;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.*;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.Display;
import android.content.Context;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class BubbleAccessibilityService extends AccessibilityService {
    private static BubbleAccessibilityService instance;
    private static final AtomicBoolean enabled = new AtomicBoolean(false);
    private Handler handler;
    private long startedAt = 0;
    private volatile String status = "Idle";
    private Bitmap lastFrame;
    private final Random random = new Random();

    // Tuned from the supplied 720x1574 recording.
    private static final int GAME_TOP = 90;
    private static final int GAME_BOTTOM = 1080;
    private static final int SHOOTER_Y = 1165;
    private static final int SHOOTER_X = 360;
    private static final long MAX_RUN_MS = 6L * 60L * 60L * 1000L;

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        handler = new Handler(Looper.getMainLooper());
        status = "Ready";
        loop();
    }

    @Override public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent e) {}
    @Override public void onInterrupt() { status = "Interrupted"; }

    public static void setEnabledFromUi(boolean on) {
        enabled.set(on);
        if (instance != null) {
            if (on) {
                instance.startedAt = SystemClock.uptimeMillis();
                instance.status = "Starting";
                instance.loop();
            } else {
                instance.status = "Paused";
            }
        }
    }
    public static boolean isEnabledFromUi() { return enabled.get(); }
    public static boolean isRunning() { return instance != null; }
    public static String statusText() { return instance == null ? "Offline" : instance.status; }

    private void loop() {
        if (handler == null) return;
        handler.postDelayed(() -> {
            if (!enabled.get()) { status = "Paused"; return; }
            if (startedAt == 0) startedAt = SystemClock.uptimeMillis();
            if (SystemClock.uptimeMillis() - startedAt > MAX_RUN_MS) {
                enabled.set(false); status = "6-hour session complete"; return;
            }
            takeFrame();
            loop();
        }, 650);
    }

    private void takeFrame() {
        if (Build.VERSION.SDK_INT < 30) { status = "Android 11+ required"; return; }
        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(),
            new TakeScreenshotCallback() {
                @Override public void onSuccess(ScreenshotResult result) {
                    try {
                        Bitmap b = Bitmap.wrapHardwareBuffer(
                            result.getHardwareBuffer(),
                            result.getColorSpace());
                        if (b == null) return;
                        lastFrame = b.copy(Bitmap.Config.ARGB_8888, false);
                        analyzeAndAct(lastFrame);
                        result.getHardwareBuffer().close();
                    } catch (Throwable t) {
                        status = "Screenshot error";
                    }
                }
                @Override public void onFailure(int errorCode) {
                    status = "Waiting for screen";
                }
            });
    }

    private void analyzeAndAct(Bitmap b) {
        if (isLikelyAdOrInstallScreen()) {
            status = "Ad/CTA detected — paused";
            tryCloseAdSafely();
            return;
        }

        if (isLevelComplete(b)) {
            status = "Level complete — next";
            tapIfSafe(365, 865);
            return;
        }

        // Detect colored bubbles in the gameplay region.
        ArrayList<Ball> balls = detectBalls(b);
        Ball shooter = detectShooter(b);
        if (shooter == null) shooter = new Ball(SHOOTER_X, SHOOTER_Y, 0, 0);

        if (balls.size() < 3) {
            status = "Waiting for stable board";
            return;
        }

        Ball target = chooseTarget(balls, shooter);
        if (target == null) {
            status = "No safe target";
            return;
        }

        status = "Aiming " + target.x + "," + target.y;
        shoot(shooter.x, shooter.y, target.x, target.y);
    }

    private ArrayList<Ball> detectBalls(Bitmap b) {
        int w=b.getWidth(), h=b.getHeight();
        ArrayList<Ball> out = new ArrayList<>();
        boolean[][] seen = new boolean[Math.max(1,w)][Math.max(1,h)];
        int step = 3;
        for (int y=GAME_TOP; y<Math.min(GAME_BOTTOM,h-20); y+=step) {
            for (int x=10; x<w-10; x+=step) {
                if (seen[x][y] || !isBubblePixel(b.getPixel(x,y))) continue;
                int minX=x,maxX=x,minY=y,maxY=y,count=0;
                ArrayDeque<Point> q=new ArrayDeque<>();
                q.add(new Point(x,y)); seen[x][y]=true;
                while(!q.isEmpty() && count<1000) {
                    Point p=q.removeFirst(); count++;
                    minX=Math.min(minX,p.x); maxX=Math.max(maxX,p.x);
                    minY=Math.min(minY,p.y); maxY=Math.max(maxY,p.y);
                    int[][] ds={{step,0},{-step,0},{0,step},{0,-step}};
                    for(int[] d:ds){
                        int nx=p.x+d[0], ny=p.y+d[1];
                        if(nx>=0&&nx<w&&ny>=GAME_TOP&&ny<GAME_BOTTOM&&!seen[nx][ny]
                                &&isBubblePixel(b.getPixel(nx,ny))){
                            seen[nx][ny]=true; q.addLast(new Point(nx,ny));
                        }
                    }
                }
                int bw=maxX-minX, bh=maxY-minY;
                if (count>=25 && bw>=12 && bh>=12 && bw<=65 && bh<=65) {
                    int cx=(minX+maxX)/2, cy=(minY+maxY)/2;
                    int rgb=averageCenter(b,cx,cy);
                    out.add(new Ball(cx,cy,rgb,0));
                }
            }
        }
        // De-duplicate close detections.
        ArrayList<Ball> clean=new ArrayList<>();
        for(Ball a:out){
            boolean duplicate=false;
            for(Ball c:clean) if(dist(a.x,a.y,c.x,c.y)<22){ duplicate=true; break; }
            if(!duplicate) clean.add(a);
        }
        return clean;
    }

    private int averageCenter(Bitmap b,int cx,int cy){
        long r=0,g=0,bl=0,n=0;
        for(int yy=-4;yy<=4;yy++) for(int xx=-4;xx<=4;xx++){
            int x=cx+xx,y=cy+yy;
            if(x>=0&&y>=0&&x<b.getWidth()&&y<b.getHeight()){
                int p=b.getPixel(x,y); r+=Color.red(p); g+=Color.green(p); bl+=Color.blue(p); n++;
            }
        }
        return Color.rgb((int)(r/n),(int)(g/n),(int)(bl/n));
    }

    private boolean isBubblePixel(int p){
        int r=Color.red(p),g=Color.green(p),bl=Color.blue(p);
        int max=Math.max(r,Math.max(g,bl)), min=Math.min(r,Math.min(g,bl));
        return max-min>55 && max>105 && !(r>120&&g>75&&bl<60 && r>g*1.25);
    }

    private Ball detectShooter(Bitmap b){
        // The recording places the current bubble around the launcher at y~1165.
        int cx=SHOOTER_X, cy=SHOOTER_Y;
        if(cy>=b.getHeight()) return null;
        int p=averageCenter(b,cx,cy);
        if(isBubblePixel(p)) return new Ball(cx,cy,p,0);
        // Search a small window around the launcher.
        for(int y=1120;y<1230&&y<b.getHeight();y+=8)
            for(int x=280;x<=440&&x<b.getWidth();x+=8){
                if(isBubblePixel(b.getPixel(x,y))) return new Ball(x,y,b.getPixel(x,y),0);
            }
        return null;
    }

    private Ball chooseTarget(ArrayList<Ball> balls, Ball shooter){
        ArrayList<Ball> same=new ArrayList<>();
        for(Ball a:balls) if(colorDistance(a.rgb,shooter.rgb)<95) same.add(a);
        if(same.isEmpty()) same=balls;

        Ball best=null; double bestScore=-1e9;
        for(Ball t:same){
            int neighbors=0;
            for(Ball a:balls) if(a!=t && colorDistance(a.rgb,t.rgb)<90 && dist(a.x,a.y,t.x,t.y)<65) neighbors++;
            // Prefer clusters and lower targets; avoid extreme edges.
            double score=neighbors*100 - Math.abs(t.x-360)*0.12 + t.y*0.03;
            if(t.x<25||t.x>695) score-=80;
            if(score>bestScore){bestScore=score;best=t;}
        }
        return best;
    }

    private int colorDistance(int a,int b){
        return Math.abs(Color.red(a)-Color.red(b))
            +Math.abs(Color.green(a)-Color.green(b))
            +Math.abs(Color.blue(a)-Color.blue(b));
    }

    private double dist(int x1,int y1,int x2,int y2){
        return Math.hypot(x1-x2,y1-y2);
    }

    private void shoot(int sx,int sy,int tx,int ty){
        // Gesture uses a short drag; game interprets the release direction as the shot.
        Path path=new Path();
        path.moveTo(sx,sy);
        float dx=tx-sx, dy=ty-sy;
        float len=(float)Math.hypot(dx,dy);
        if(len<1) return;
        float scale=Math.min(1.0f, 480f/len);
        path.lineTo(sx+dx*scale, sy+dy*scale);
        GestureDescription.StrokeDescription stroke =
            new GestureDescription.StrokeDescription(path, 0, 180);
        dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
    }

    private boolean isLevelComplete(Bitmap b){
        // Screenshot sample: green "Level ... Completed!" banner + dark results card.
        // Heuristic: central region becomes mostly dark/gray while gameplay bubbles disappear.
        int w=b.getWidth(), h=b.getHeight();
        int dark=0, total=0, green=0;
        for(int y=350;y<900&&y<h;y+=8) for(int x=100;x<w-100;x+=8){
            int p=b.getPixel(x,y);
            int r=Color.red(p),g=Color.green(p),bl=Color.blue(p);
            total++;
            if(r<85&&g<85&&bl<85) dark++;
            if(g>95 && g>r*1.15 && g>bl*1.05) green++;
        }
        return total>0 && green>80 && dark>900;
    }

    private boolean isLikelyAdOrInstallScreen(){
        // Accessibility tree is safer than guessing: never activate nodes containing
        // install/download/play-store language. Only pause when such CTA is visible.
        List<AccessibilityNodeInfo> roots = new ArrayList<>();
        AccessibilityNodeInfo r=getRootInActiveWindow();
        if(r==null) return false;
        String text=treeText(r).toLowerCase(Locale.US);
        return text.contains("install") || text.contains("download") ||
               text.contains("play store") || text.contains("get the app") ||
               text.contains("open in play");
    }

    private String treeText(AccessibilityNodeInfo n){
        StringBuilder s=new StringBuilder();
        if(n.getText()!=null) s.append(n.getText()).append(' ');
        if(n.getContentDescription()!=null) s.append(n.getContentDescription()).append(' ');
        for(int i=0;i<n.getChildCount();i++){
            AccessibilityNodeInfo c=n.getChild(i);
            if(c!=null){ s.append(treeText(c)); c.recycle(); }
        }
        return s.toString();
    }

    private void tryCloseAdSafely(){
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null) return;
        AccessibilityNodeInfo node=findCloseNode(root);
        if(node!=null){
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            node.recycle();
            status="Ad closed — verifying";
        }
        root.recycle();
    }

    private AccessibilityNodeInfo findCloseNode(AccessibilityNodeInfo n){
        String s=((n.getText()==null?"":n.getText().toString())+" "+
                  (n.getContentDescription()==null?"":n.getContentDescription().toString()))
                  .toLowerCase(Locale.US);
        if(s.equals("x") || s.equals("close") || s.equals("skip") ||
           s.contains("close ad") || s.contains("skip ad")) {
            if(n.isClickable()) return AccessibilityNodeInfo.obtain(n);
        }
        for(int i=0;i<n.getChildCount();i++){
            AccessibilityNodeInfo c=n.getChild(i);
            if(c!=null){
                AccessibilityNodeInfo f=findCloseNode(c);
                c.recycle();
                if(f!=null) return f;
            }
        }
        return null;
    }

    private void tapIfSafe(int x,int y){
        // Fixed Next coordinate from supplied recording (720x1574 portrait).
        // Scale for other resolutions.
        Display d=getDisplay();
        if(d==null) return;
        Point size=new Point(); d.getRealSize(size);
        float sx=size.x/720f, sy=size.y/1574f;
        int xx=(int)(x*sx), yy=(int)(y*sy);
        Path p=new Path(); p.moveTo(xx,yy); p.lineTo(xx+1,yy+1);
        GestureDescription.StrokeDescription st=new GestureDescription.StrokeDescription(p,0,80);
        dispatchGesture(new GestureDescription.Builder().addStroke(st).build(),null,null);
    }

    private static class Ball {
        int x,y,rgb,type;
        Ball(int x,int y,int rgb,int type){this.x=x;this.y=y;this.rgb=rgb;this.type=type;}
    }
}
