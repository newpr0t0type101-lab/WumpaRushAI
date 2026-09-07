package com.wumparush.game;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;

public class GameView extends GLSurfaceView {
    public interface Listener {
        void onHud(int score,int coins,int hp,int boss,String bossName,int bossHp,int bossMax,boolean maskReady,boolean dashReady);
        void onRunOver(int score,int coins,int bosses);
        void onMessage(String s);
    }

    private float sx, sy;
    private final GameRenderer renderer;

    public GameView(Context c, Listener l){
        super(c);
        setEGLContextClientVersion(2);
        renderer=new GameRenderer(c,l);
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    public void startRun(){ queueEvent(renderer::startRun); }
    public void useMask(){ queueEvent(renderer::useMask); }
    public void useDash(){ queueEvent(renderer::useDash); }
    public void setQuality(boolean q){ queueEvent(() -> renderer.setQuality(q)); }
    public int getBossIndex(){ return renderer.getActiveBossIndex(); }

    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getAction()==MotionEvent.ACTION_DOWN){ sx=e.getX(); sy=e.getY(); return true; }
        if(e.getAction()==MotionEvent.ACTION_UP){
            float dx=e.getX()-sx, dy=e.getY()-sy;
            if(Math.hypot(dx,dy)<50)return true;
            if(Math.abs(dx)>Math.abs(dy)){
                if(dx>0)queueEvent(renderer::moveRight); else queueEvent(renderer::moveLeft);
            } else {
                if(dy<0)queueEvent(renderer::jump); else queueEvent(renderer::slide);
            }
            return true;
        }
        return true;
    }
}