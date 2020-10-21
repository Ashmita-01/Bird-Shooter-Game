package com.example.helloar;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.webkit.WebHistoryItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameView  extends SurfaceView implements Runnable{

    private Thread thread;
    private boolean isPlaying , isGameOver = false;
    private int screenX,screenY,score = 0;
    public static float screenRatioX,screenRatioY;
    private Paint paint;
    private Bird[] birds;
    private SharedPreferences prefs;
    private Random random;
    private SoundPool soundpool;
    private List<Bullet> bullets;
    private int sound;
    private flight Flight;
    private GameActivity activity;
    private Background background1,background2;

    public GameView(GameActivity activity,int screenX,int screenY) {
        super(activity);

        this.activity = activity;

        prefs = activity.getSharedPreferences("game",Context.MODE_PRIVATE);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .build();

            soundpool = new SoundPool.Builder().setAudioAttributes(audioAttributes).build();

        } else
            soundpool = new SoundPool(1, AudioManager.STREAM_MUSIC,0);

        sound = soundpool.load(activity,R.raw.shoot,1);

        this.screenX = screenX;
        this.screenY = screenY;
        screenRatioX = 1920f /screenX;
        screenRatioY = 1080f /screenY;

        background1= new Background(screenX,screenY,getResources());
        background2 = new Background(screenX,screenY,getResources());

        Flight =  new flight(this,screenY,getResources());

        bullets = new ArrayList<>();

        background2.x = screenX;

        paint = new Paint();
        paint.setTextSize(128);
        paint.setColor(Color.WHITE);

        birds = new Bird[4];

        for(int i = 0; i < 4; i++){
            Bird bird = new Bird(getResources());
            birds[i] = bird;
        }

        random = new Random();
    }

    @Override
    public void run() {

        while(isPlaying){
            update();
            draw();
            sleep();
        }
    }

    private void update(){

        background1.x -= 10 * screenRatioX;
        background2.x -= 10 * screenRatioY;

        if (background1.x + background1.background.getWidth() < 0) {
            background1.x =screenX;
        }

        if (background2.x + background2.background.getWidth() < 0) {
            background2.x =screenX;
        }
        if (Flight.isGoingUp)
            Flight.y -=30 * screenRatioY;
        else
            Flight.y +=30 * screenRatioY;

        if(Flight.y < 0)
            Flight.y = 0;

        if(Flight.y >= screenY-Flight.height)
             Flight.y = screenY - Flight.height;

        List<Bullet> trash = new ArrayList<>();

        for(Bullet bullet : bullets){
             if(bullet.x > screenX)
                 trash.add(bullet);

             bullet.x += 50*screenRatioX;

             for( Bird bird : birds){
                 if ( Rect.intersects(bird.getCollisionShape(),bullet.getCollisionShape())){
                     score++;
                     bird.x = -500;
                     bullet.x =screenX + 500;
                     bird.wasShot = true;
                 }
             }
        }

        for (Bullet bullet : trash){
            bullets.remove(bullet);
        }

        for(Bird bird : birds){
            bird.x -= bird.speed;

            if(bird.x + bird.width < 0){

                if(!bird.wasShot){
                    isGameOver = true;
                    return;
                }

                int bound = (int) (30 * screenRatioX);
                bird.speed = random.nextInt(bound);

                if(bird.speed < 10 * screenRatioX)
                    bird.speed = (int) (10 * screenRatioX);

                bird.x = screenX;
                bird.y = random.nextInt(screenY - bird.height);

                bird.wasShot = false;

            }

            if(Rect.intersects(bird.getCollisionShape(),Flight.getCollisionShape())){

                isGameOver = true;
                return;
            }
        }
    }

    private void draw(){

        if (getHolder().getSurface().isValid()){

            Canvas canvas = getHolder().lockCanvas();
            canvas.drawBitmap(background1.background,background1.x,background1.y,paint);
            canvas.drawBitmap(background2.background,background2.x,background2.y,paint);

            for(Bird bird :birds)
                canvas.drawBitmap(bird.getBird(),bird.x,bird.y,paint);

            canvas.drawText(score + "", screenX/2f, 164,paint);

            if(isGameOver){
                isPlaying = false;
                canvas.drawBitmap(Flight.getDead(),Flight.x,Flight.y,paint);
                getHolder().unlockCanvasAndPost(canvas);
                saveIfHighScore();
                waitBeforeExiting();
                return;
            }

            canvas.drawBitmap(Flight.getFlight(),Flight.x, Flight.y,paint);

            for(Bullet bullet : bullets)
                canvas.drawBitmap(bullet.bullet,bullet.x,bullet.y,paint);

            getHolder().unlockCanvasAndPost(canvas);
        }
    }

    private void waitBeforeExiting() {

        try {
            Thread.sleep(3000);
            activity.startActivity(new Intent(activity,MainActivity.class));
            activity.finish();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void saveIfHighScore() {

        if(prefs.getInt("highscore",0)< score){
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("highscore",score);
            editor.apply();
        }

    }

    private void sleep(){
        try {
            Thread.sleep(17);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    public void resume(){
        isPlaying = true;
        thread = new Thread(this);
        thread.start();
    }

    public void pause(){
        try {
            isPlaying=false;
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        switch (event.getAction()){
            case MotionEvent.ACTION_DOWN:
                if(event.getX() < screenX/2){
                    Flight.isGoingUp = true;
                }
                break;
            case MotionEvent.ACTION_UP:
                Flight.isGoingUp = false;
                if (event.getX() > screenX/2)
                    Flight.toShoot++;
                break;
        }

        return true;
    }

    public void newBullet() {

        if(!prefs.getBoolean("isMute",false))
            soundpool.play(sound,1,1,0,0,1);

        Bullet bullet = new Bullet(getResources());
        bullet.x = Flight.x + Flight.width;
        bullet.y = Flight.y + (Flight.height/2);
        bullets.add(bullet);
    }
}