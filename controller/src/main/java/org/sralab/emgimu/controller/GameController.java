package org.sralab.emgimu.controller;

import android.util.Log;

import java.util.Random;

public class GameController {

    float goal_x, goal_y;
    float theta, radius;
    float vel;
    float dt = 0.01f;

    public GameController() {
        goal_x = 0.5f;
        goal_y = 0.5f;
        theta = 0.0f;
        radius = 0.0f;
        vel = 0.5f / 1.0f;
    }

    public void update(float decoded_x, float decoded_y, float dt_ms) {
        radius = radius + vel * dt_ms / 1000.0f;
        if (radius > 0.5) {
            vel = -vel;
        } else if (radius < 0) {
            radius = 0;
            vel = -vel;
            theta = theta + (float) Math.PI / 4; // Make 8 angles
        }

        goal_x = 0.5f + (float) Math.cos(theta) * radius;
        goal_y = 0.5f + (float) Math.sin(theta) * radius;

        Log.d("GameController", "Goal: " + goal_x + " " + goal_y + " " + theta + " " + radius);
    }

    public float getGoalX() { return goal_x; };
    public float getGoalY() { return goal_y; };
}
