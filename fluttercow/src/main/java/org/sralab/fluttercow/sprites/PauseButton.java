/**
 * The pauseButton
 * 
 * @author Lars Harmsen
 * Copyright (c) <2014> <Lars Harmsen - Quchen>
 */

package org.sralab.fluttercow.sprites;

import org.sralab.fluttercow.Game;
import org.sralab.fluttercow.GameView;
import org.sralab.fluttercow.Util;

import org.sralab.fluttercow.R;

public class PauseButton extends Sprite{
    public PauseButton(GameView view, Game game) {
        super(view, game);
        this.bitmap = Util.getScaledBitmapAlpha8(game, R.drawable.pause_button);
        this.width = this.bitmap.getWidth();
        this.height = this.bitmap.getHeight();
    }
    
    /**
     * Sets the button in the right upper corner.
     */
    @Override
    public void move(){
        this.x = this.view.getWidth() - this.width;
        this.y = 0;
    }
}