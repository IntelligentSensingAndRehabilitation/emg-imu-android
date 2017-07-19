/**
 * A Coin
 * 
 * @author Lars Harmsen
 * Copyright (c) <2014> <Lars Harmsen - Quchen>
 */

package org.sralab.fluttercow.sprites;

import org.sralab.fluttercow.Game;
import org.sralab.fluttercow.GameView;
import org.sralab.fluttercow.Util;

import android.graphics.Bitmap;

import org.sralab.fluttercow.R;

public class Coin extends PowerUp {
    /**
     * Static bitmap to reduce memory usage.
     */
    public static Bitmap globalBitmap;
    private static int sound = -1;

    public Coin(GameView view, Game game) {
        super(view, game);
        if(globalBitmap == null){
            globalBitmap = Util.getScaledBitmapAlpha8(game, R.drawable.coin);
        }
        this.bitmap = globalBitmap;
        this.width = this.bitmap.getWidth()/(colNr = 12);
        this.height = this.bitmap.getHeight();
        this.frameTime = 1;
        if(sound == -1){
            sound = Game.soundPool.load(game, R.raw.coin, 1);
        }
    }

    /**
     * When eaten the player will turn into nyan cat.
     */
    @Override
    public void onCollision() {
        super.onCollision();
        playSound();
        game.increaseCoin();
    }
    
    private void playSound(){
        Game.soundPool.play(sound, Game.volume, Game.volume, 0, 0, 1);
    }
    
    @Override
    public void move() {
        changeToNextFrame();
        super.move();
    }
}
