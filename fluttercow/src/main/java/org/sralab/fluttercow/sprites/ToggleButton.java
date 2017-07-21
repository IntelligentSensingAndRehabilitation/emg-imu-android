/**
 * The pauseButton
 * 
 * @author Lars Harmsen
 * Copyright (c) <2014> <Lars Harmsen - Quchen>
 */

package org.sralab.fluttercow.sprites;

import org.sralab.fluttercow.Game;
import org.sralab.fluttercow.GameView;
import org.sralab.fluttercow.R;
import org.sralab.fluttercow.Util;

public class ToggleButton extends Sprite{

    public enum TOGGLE_MODE { LINEAR, THRESHOLD };

    private Game.CONTROL_MODE mMode;

    public ToggleButton(GameView view, Game game) {
        super(view, game);
        setMode(Game.CONTROL_MODE.LINEAR);
        move(); // set initial position
    }

    public void setMode(Game.CONTROL_MODE mode) {
        mMode = mode;
        switch(mMode) {
            case LINEAR:
                this.bitmap = Util.getScaledBitmapAlpha8(game, R.drawable.toggle_button_linear);
                break;
            case THRESHOLD:
                this.bitmap = Util.getScaledBitmapAlpha8(game, R.drawable.toggle_button_threshold);
                break;
        }
        this.width = this.bitmap.getWidth();
        this.height = this.bitmap.getHeight();
    }
    
    /**
     * Sets the button in the right upper corner.
     */
    @Override
    public void move(){
        // The pause button is to the right of this and the same width
        this.x = this.view.getWidth() - (this.width * 5) / 2;
        this.y = 0;
    }
}