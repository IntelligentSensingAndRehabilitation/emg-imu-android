package org.sralab.emgimu.controller.telepresence;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Post {

    public Post(float speed, float turn) {
        this.speed = speed;
        this.turn = turn;
    }

    @SerializedName("speed")
    @Expose
    private Float speed;
    @SerializedName("turn")
    @Expose
    private Float turn;

    public Float getSpeed() {
        return speed;
    }

    public void setSpeed(Float speed) {
        this.speed = speed;
    }

    public Float getTurn() {
        return turn;
    }

    public void setTurn(Float turn) {
        this.turn = turn;
    }

    @Override
    public String toString() {
        return "Post{" +
                "speed=" + speed +
                ", turn=" + turn +
                '}';
    }
}