package com.wumparush.game;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import android.content.SharedPreferences;

public class MainActivity extends Activity implements GameView.Listener {
    private GameView game;
    private FrameLayout root;
    private LinearLayout menu;
    private TextView hud, message;
    private Button maskButton, dashButton;
    private SharedPreferences prefs;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        prefs = getSharedPreferences("wumpa", MODE_PRIVATE);
        root = new FrameLayout(this);
        game = new GameView(this, this);
        root.addView(game, new FrameLayout.LayoutParams(-1,-1));

        hud = label("", 18, true);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-1, 80);
        hp.gravity = Gravity.TOP; hp.setMargins(18,18,18,0);
        root.addView(hud,hp);

        message = label("", 24, true);
        message.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(-1,120);
        mp.gravity = Gravity.TOP; mp.topMargin = 100;
        root.addView(message, mp);

        LinearLayout skills = new LinearLayout(this);
        skills.setOrientation(LinearLayout.HORIZONTAL);
        maskButton = button("МАСКА");
        dashButton = button("РЫВОК");
        maskButton.setOnClickListener(v -> game.useMask());
        dashButton.setOnClickListener(v -> game.useDash());
        skills.addView(maskButton, new LinearLayout.LayoutParams(0,72,1));
        skills.addView(dashButton, new LinearLayout.LayoutParams(0,72,1));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-1,82);
        sp.gravity = Gravity.BOTTOM; sp.setMargins(10,0,10,10);
        root.addView(skills,sp);

        buildMenu();
        setContentView(root);
        showMenu();
    }

    private void buildMenu(){
        menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setGravity(Gravity.CENTER);
        menu.setPadding(28,28,28,28);
        menu.setBackgroundColor(Color.argb(225,2,6,23));

        TextView title = label("CRASH:\\nWUMPA RUSH", 36, true);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(Color.rgb(250,204,21));
        menu.addView(title, new LinearLayout.LayoutParams(-1,140));

        Button play = button("НАЧАТЬ ЗАБЕГ");
        play.setOnClickListener(v -> { menu.setVisibility(View.GONE); game.startRun(); });
        menu.addView(play,new LinearLayout.LayoutParams(-1,70));

        Button scores = button("РЕКОРДЫ");
        scores.setOnClickListener(v -> {
            int best=prefs.getInt("best",0), bank=prefs.getInt("bank",0);
            Toast.makeText(this,"Лучший счёт: "+best+"   Wumpa-банк: "+bank,Toast.LENGTH_LONG).show();
        });
        menu.addView(scores,new LinearLayout.LayoutParams(-1,65));

        Button quality = button("ГРАФИКА: "+(prefs.getBoolean("quality",true)?"QUALITY":"PERFORMANCE"));
        quality.setOnClickListener(v -> {
            boolean q=!prefs.getBoolean("quality",true);
            prefs.edit().putBoolean("quality",q).apply();
            game.setQuality(q);
            quality.setText("ГРАФИКА: "+(q?"QUALITY":"PERFORMANCE"));
        });
        menu.addView(quality,new LinearLayout.LayoutParams(-1,65));

        TextView help = label("Свайп ← →: дорожки\\nСвайп ↑: прыжок\\nСвайп ↓: подкат\\nМаска: защита • Рывок: ускорение и урон боссу",14,false);
        help.setGravity(Gravity.CENTER);
        menu.addView(help,new LinearLayout.LayoutParams(-1,170));

        root.addView(menu,new FrameLayout.LayoutParams(-1,-1));
    }

    private TextView label(String s,int size,boolean bold){
        TextView t=new TextView(this);
        t.setText(s); t.setTextColor(Color.WHITE); t.setTextSize(size);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setShadowLayer(4,0,2,Color.BLACK);
        return t;
    }
    private Button button(String s){
        Button b=new Button(this); b.setText(s); b.setTextSize(16); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return b;
    }
    private void showMenu(){ menu.setVisibility(View.VISIBLE); hud.setText(""); message.setText(""); }

    @Override public void onHud(int score,int coins,int hp,int boss,String bossName,int bossHp,int bossMax,boolean maskReady,boolean dashReady){
        runOnUiThread(() -> {
            hud.setText("WUMPA "+coins+"     SCORE "+score+"     HP "+hp);
            message.setText(boss>=0?bossName+"  "+bossHp+"/"+bossMax:"");
            maskButton.setEnabled(maskReady); dashButton.setEnabled(dashReady);
        });
    }

    @Override public void onRunOver(int score,int coins,int bosses){
        int best=Math.max(score,prefs.getInt("best",0));
        int bank=prefs.getInt("bank",0)+coins;
        prefs.edit().putInt("best",best).putInt("bank",bank).apply();
        runOnUiThread(() -> {
            Toast.makeText(this,"Забег окончен: "+score+" очков • "+coins+" Wumpa • боссы "+bosses+"/3",Toast.LENGTH_LONG).show();
            showMenu();
        });
    }

    @Override public void onMessage(String s){
        runOnUiThread(() -> {
            message.setText(s);
            message.postDelayed(() -> { if(game.getBossIndex()<0) message.setText(""); },900);
        });
    }

    @Override protected void onPause(){ super.onPause(); game.onPause(); }
    @Override protected void onResume(){ super.onResume(); game.onResume(); }
}