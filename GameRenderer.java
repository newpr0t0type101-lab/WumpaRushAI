package com.wumparush.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.opengl.GLES20;
import android.opengl.Matrix;
import java.nio.*;
import java.util.*;

public class GameRenderer implements android.opengl.GLSurfaceView.Renderer {
    private final GameView.Listener listener;
    private int program,aPos,uMvp,uColor;
    private FloatBuffer cube; private int cubeVerts;
    private final float[] proj=new float[16],view=new float[16],vp=new float[16],model=new float[16],mvp=new float[16];
    private final Random rnd=new Random();
    private long lastNs=0;
    private boolean running=false,quality=true;
    private int lane=1,hp=3,coins=0,score=0,bossesDone=0,activeBoss=-1,bossHp=0,bossMax=1,location=0;
    private float playerY=0,vy=0,slide=0,inv=0,mask=0,dash=0,maskCd=0,dashCd=0,distance=0,speed=9,spawn=.5f,coinSpawn=.3f,bossTimer=0,bossAttack=0;
    private final ArrayList<Obj> objs=new ArrayList<>();
    private static final String[] BOSS_NAMES={"RIPPER ROO","N. GIN","TINY TIGER"};
    private static final int[] BOSS_HP={18,24,30};
    private static class Obj{int lane,type;float z;boolean coin,hit;Obj(int l,int t,float z,boolean c){lane=l;type=t;this.z=z;coin=c;}}

    public GameRenderer(Context c,GameView.Listener l){
        listener=l;
        SharedPreferences p=c.getSharedPreferences("wumpa",Context.MODE_PRIVATE);
        quality=p.getBoolean("quality",true);
    }

    @Override public void onSurfaceCreated(javax.microedition.khronos.egl.EGLConfig c){
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        program=makeProgram(VS,FS);
        aPos=GLES20.glGetAttribLocation(program,"aPosition");
        uMvp=GLES20.glGetUniformLocation(program,"uMVP");
        uColor=GLES20.glGetUniformLocation(program,"uColor");
        buildCube();
    }
    @Override public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl,int w,int h){
        GLES20.glViewport(0,0,w,h);
        Matrix.perspectiveM(proj,0,58,(float)w/h,.1f,100);
        Matrix.setLookAtM(view,0,0,4.8f,7.0f,0,1.0f,-7,0,1,0);
        Matrix.multiplyMM(vp,0,proj,0,view,0);
    }
    @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl){
        long n=System.nanoTime();
        float dt=lastNs==0?.016f:Math.min(.033f,(n-lastNs)/1_000_000_000f);
        lastNs=n; update(dt); render();
    }

    public void setQuality(boolean q){quality=q;}
    public int getActiveBossIndex(){return activeBoss;}
    public void startRun(){running=true;lane=1;hp=3;coins=0;score=0;bossesDone=0;playerY=0;vy=0;slide=0;inv=0;mask=0;dash=0;maskCd=0;dashCd=0;distance=0;speed=9;spawn=.35f;coinSpawn=.2f;activeBoss=-1;objs.clear();location=0;listener.onMessage("GO!");}
    public void moveLeft(){if(running)lane=Math.max(0,lane-1);}
    public void moveRight(){if(running)lane=Math.min(2,lane+1);}
    public void jump(){if(running&&playerY<=.001f){vy=8.5f;listener.onMessage("JUMP");}}
    public void slide(){if(running&&playerY<=.001f){slide=.7f;listener.onMessage("SLIDE");}}
    public void useMask(){if(running&&maskCd<=0){mask=5;maskCd=13;listener.onMessage("ВУДУ-МАСКА!");}}
    public void useDash(){if(running&&dashCd<=0){dash=2.4f;dashCd=10;listener.onMessage("WUMPA-РЫВОК!");}}

    private void update(float dt){
        if(!running){pushHud();return;}
        distance+=speed*dt*(dash>0?1.45f:1); score+=(int)(32*dt*(dash>0?1.4f:1)); speed=Math.min(17,9+distance/130f); location=distance<420?0:1;
        vy-=20*dt;playerY+=vy*dt;if(playerY<0){playerY=0;vy=0;}
        slide=Math.max(0,slide-dt);inv=Math.max(0,inv-dt);mask=Math.max(0,mask-dt);dash=Math.max(0,dash-dt);maskCd=Math.max(0,maskCd-dt);dashCd=Math.max(0,dashCd-dt);

        if(activeBoss<0){
            if(bossesDone==0&&distance>140)startBoss(0);
            else if(bossesDone==1&&distance>470)startBoss(1);
            else if(bossesDone==2&&distance>820)startBoss(2);
        }

        if(activeBoss<0){
            spawn-=dt;if(spawn<=0){spawnObstacle();spawn=.45f+rnd.nextFloat()*.55f;}
            coinSpawn-=dt;if(coinSpawn<=0){spawnCoins();coinSpawn=.8f+rnd.nextFloat()*.9f;}
        }else{
            bossTimer+=dt;bossAttack-=dt;
            if(bossAttack<=0){for(int i=0;i<1+activeBoss;i++)objs.add(new Obj(rnd.nextInt(3),4,-7-i*1.5f,false));bossAttack=Math.max(.45f,1.1f-activeBoss*.2f);}
            if(dash>0&&bossTimer>.9f){bossHp--;score+=20;bossTimer=.78f;}
            if(bossHp<=0){listener.onMessage(BOSS_NAMES[activeBoss]+" ПОБЕЖДЁН!");score+=1000*(activeBoss+1);bossesDone++;activeBoss=-1;objs.clear();}
        }

        for(int i=objs.size()-1;i>=0;i--){
            Obj o=objs.get(i);o.z+=speed*dt*(dash>0?1.45f:1);
            if(!o.hit&&o.z>4.6f&&o.z<6.2f&&o.lane==lane){
                if(o.coin){o.hit=true;coins++;score+=15;}
                else{
                    boolean safe=(o.type==0&&playerY>1)||(o.type==1&&slide>0)||(o.type==2&&playerY>1.1f)||(o.type==3&&playerY>1);
                    if(!safe){o.hit=true;hit();}
                }
            }
            if(o.z>8||o.hit)objs.remove(i);
        }
        pushHud();
    }

    private void pushHud(){listener.onHud(score,coins,hp,activeBoss,activeBoss>=0?BOSS_NAMES[activeBoss]:"",bossHp,bossMax,maskCd<=0,dashCd<=0);}
    private void hit(){if(inv>0||mask>0||dash>0)return;hp--;inv=1.3f;listener.onMessage("OUCH!");if(hp<=0){running=false;listener.onRunOver(score,coins,bossesDone);}}
    private void startBoss(int i){activeBoss=i;bossMax=BOSS_HP[i];bossHp=bossMax;bossTimer=0;bossAttack=.5f;objs.clear();listener.onMessage("BOSS: "+BOSS_NAMES[i]);}
    private void spawnObstacle(){objs.add(new Obj(rnd.nextInt(3),rnd.nextInt(4),-28,false));if(rnd.nextFloat()<.2f)objs.add(new Obj(rnd.nextInt(3),rnd.nextInt(4),-34,false));}
    private void spawnCoins(){int l=rnd.nextInt(3);for(int i=0;i<5;i++)objs.add(new Obj(l,0,-22-i*2.2f,true));}

    private void render(){
        if(location==0)GLES20.glClearColor(.10f,.48f,.30f,1);else GLES20.glClearColor(.18f,.08f,.16f,1);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);GLES20.glUseProgram(program);
        drawBox(0,-.35f,-11,6.4f,.35f,38,location==0?.42f:.18f,location==0?.24f:.20f,location==0?.10f:.23f);
        for(int i=-1;i<=1;i+=2)for(int z=-28;z<8;z+=4)drawBox(i*1.05f,.01f,z,.05f,.02f,1.2f,.9f,.82f,.35f);
        if(quality)for(int z=-30;z<10;z+=5){drawBox(-4.2f,1,z,1.4f,2,1.2f,.08f,.38f,.10f);drawBox(4.2f,1,z-2.5f,1.4f,2,1.2f,.30f,.10f,.08f);}
        if(activeBoss>=0)drawBox((float)Math.sin(System.nanoTime()/5e8)*1.7f,1.6f,-8,2.4f,3.2f,2,.75f,.18f,.10f);

        for(Obj o:objs){
            float x=(o.lane-1)*2.05f;
            if(o.coin)drawBox(x,1.05f,o.z,.45f,.45f,.45f,1,.45f,.08f);
            else if(o.type==0)drawBox(x,.65f,o.z,1.3f,1.3f,1.3f,.55f,.28f,.08f);
            else if(o.type==1)drawBox(x,1.55f,o.z,1.5f,.35f,.5f,.92f,.70f,.08f);
            else if(o.type==2)drawBox(x,.40f,o.z,1.35f,.75f,1.5f,.25f,.25f,.28f);
            else drawBox(x,.28f,o.z,1.5f,.55f,1.5f,.35f,.08f,.08f);
        }

        float px=(lane-1)*2.05f,py=.9f+playerY;
        if(slide>0)drawBox(px,.48f,5.2f,1.1f,.55f,1.2f,.95f,.35f,.08f);
        else {drawBox(px,py,5.2f,.9f,1.8f,.9f,.95f,.35f,.08f);drawBox(px,py+1.25f,5.2f,.78f,.72f,.78f,1,.56f,.12f);}
        if(mask>0)drawBox(px,py+.7f,5.2f,1.5f,2.6f,1.5f,.95f,.78f,.10f);
    }

    private void drawBox(float x,float y,float z,float sx,float sy,float sz,float r,float g,float b){
        Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,y,z);Matrix.scaleM(model,0,sx,sy,sz);Matrix.multiplyMM(mvp,0,vp,0,model,0);
        GLES20.glUniformMatrix4fv(uMvp,1,false,mvp,0);GLES20.glUniform4f(uColor,r,g,b,1);
        cube.position(0);GLES20.glEnableVertexAttribArray(aPos);GLES20.glVertexAttribPointer(aPos,3,GLES20.GL_FLOAT,false,0,cube);GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,cubeVerts);GLES20.glDisableVertexAttribArray(aPos);
    }

    private void buildCube(){
        float[] v={-0.5f,-0.5f,0.5f,0.5f,-0.5f,0.5f,0.5f,0.5f,0.5f,-0.5f,-0.5f,0.5f,0.5f,0.5f,0.5f,-0.5f,0.5f,0.5f,0.5f,-0.5f,-0.5f,-0.5f,-0.5f,-0.5f,-0.5f,0.5f,-0.5f,0.5f,-0.5f,-0.5f,-0.5f,0.5f,-0.5f,0.5f,0.5f,-0.5f,-0.5f,-0.5f,-0.5f,-0.5f,0.5f,-0.5f,0.5f,0.5f,-0.5f,-0.5f,-0.5f,-0.5f,0.5f,0.5f,-0.5f,0.5f,-0.5f,0.5f,-0.5f,0.5f,0.5f,-0.5f,-0.5f,0.5f,0.5f,-0.5f,-0.5f,0.5f,0.5f,0.5f,-0.5f,0.5f,-0.5f,0.5f,0.5f,0.5f,-0.5f,0.5f,0.5f,0.5f,0.5f,-0.5f,0.5f,0.5f,0.5f,0.5f,-0.5f,-0.5f,0.5f,-0.5f,0.5f,-0.5f,-0.5f,-0.5f,-0.5f,-0.5f,0.5f,-0.5f,-0.5f,-0.5f,-0.5f,0.5f,-0.5f,-0.5f,0.5f};
        cubeVerts=v.length/3;ByteBuffer bb=ByteBuffer.allocateDirect(v.length*4).order(ByteOrder.nativeOrder());cube=bb.asFloatBuffer();cube.put(v).position(0);
    }
    private int makeProgram(String vs,String fs){int v=compile(GLES20.GL_VERTEX_SHADER,vs),f=compile(GLES20.GL_FRAGMENT_SHADER,fs);int p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);return p;}
    private int compile(int type,String s){int sh=GLES20.glCreateShader(type);GLES20.glShaderSource(sh,s);GLES20.glCompileShader(sh);return sh;}
    private static final String VS="uniform mat4 uMVP; attribute vec3 aPosition; void main(){gl_Position=uMVP*vec4(aPosition,1.0);}";
    private static final String FS="precision mediump float; uniform vec4 uColor; void main(){gl_FragColor=uColor;}";
}