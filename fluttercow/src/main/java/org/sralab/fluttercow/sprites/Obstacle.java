/**
 * An obstacle: spider + logHead
 * 
 * @author Lars Harmsen
 * Copyright (c) <2014> <Lars Harmsen - Quchen>
 */

package org.sralab.fluttercow.sprites;

import org.sralab.fluttercow.Game;
import org.sralab.fluttercow.GameView;

import org.sralab.fluttercow.R;

import android.graphics.Canvas;
import android.util.Log;

public class Obstacle extends Sprite{
    private Spider spider;
    private WoodLog log;
    private double difficulty;

    private static int collideSound = -1;
    private static int passSound = -1;

    /** Necessary so the onPass method is just called once */
    public boolean isAlreadyPassed = false;

    public Obstacle(GameView view, Game game, double difficulty) {
        super(view, game);
        spider = new Spider(view, game);
        log = new WoodLog(view, game);

        this.difficulty = difficulty;

        if(collideSound == -1){
            collideSound = Game.soundPool.load(game, R.raw.crash, 1);
        }
        if(passSound == -1){
            passSound = Game.soundPool.load(game, R.raw.pass, 1);
        }
        
        initPos();
    }
    
    /**
     * Creates a spider and a wooden log at the right of the screen.
     * With a certain gap between them.
     * The vertical position is in a certain area random.
     */
    private void initPos(){
        int height = game.getResources().getDisplayMetrics().heightPixels;
        int gap = (int) ((float) height / difficulty);


        int ground = (int) (height * Frontground.GROUND_HEIGHT);
        int random = (int) (Math.random() * (height - gap - ground));
        int spider_ypos = ground + random - spider.height - gap / 2;
        int log_ypos = ground + random +  gap / 2;

        Log.d("Obstacle", "Difficult: " + difficulty + " gap " + gap + " ground " + ground + " spider " + spider_ypos + " log " + log_ypos + " spider height " + spider.height);

        spider.init(game.getResources().getDisplayMetrics().widthPixels, spider_ypos);
        log.init(game.getResources().getDisplayMetrics().widthPixels, log_ypos);
    }

    /**
     * Draws spider and log.
     */
    @Override
    public void draw(Canvas canvas) {
        spider.draw(canvas);
        log.draw(canvas);
    }

    /**
     * Checks whether both, spider and log, are out of range.
     */
    @Override
    public boolean isOutOfRange() {
        return spider.isOutOfRange() && log.isOutOfRange();
    }

    /**
     * Checks whether the spider or the log is colliding with the sprite.
     */
    @Override
    public boolean isColliding(Sprite sprite) {
        return spider.isColliding(sprite) || log.isColliding(sprite);
    }

    /**
     * Moves both, spider and log.
     */
    @Override
    public void move() {
        spider.move();
        log.move();
    }

    /**
     * Sets the speed of the spider and the log.
     */
    @Override
    public void setSpeedX(float speedX) {
        spider.setSpeedX(speedX);
        log.setSpeedX(speedX);
    }
    
    /**
     * Checks whether the spider and the log are passed.
     */
    @Override
    public boolean isPassed(){
        return spider.isPassed() && log.isPassed();
    }
    
    /**
     * Will call obstaclePassed of the game, if this is the first pass of this obstacle.
     */
    public void onPass(){
        if(!isAlreadyPassed){
            isAlreadyPassed = true;
            view.getGame().increasePoints();
            Game.soundPool.play(passSound, Game.volume, Game.volume, 0, 0, 1);
        }
    }

    @Override
    public void onCollision() {
        super.onCollision();
        Game.soundPool.play(collideSound, Game.volume, Game.volume, 0, 0, 1);
    }

}
