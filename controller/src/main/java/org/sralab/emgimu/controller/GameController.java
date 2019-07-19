package org.sralab.emgimu.controller;

import java.util.Random;

public class GameController {

    Random rng;

    float goal_x, goal_y, vel_x, vel_y;
    float vel = 0.25f;
    float radius = 0;
    float theta = (float) Math.PI;

    public GameController() {
        rng = new Random();
        goal_x = 0.5f;
        goal_y = 0.5f;
    }

    public void update(float decoded_x, float decoded_y, float dt_ms) {
        radius = radius + vel * dt_ms / 1000.0f; // + (float) rng.nextGaussian() / 10.0f;
        if (radius > 0.5) {
            vel = -vel;
        } else if (radius < 0) {
            radius = 0;
            vel = -vel;
            theta = theta + (float) Math.PI / 4; // Make 8 angles
        }
        goal_x = 0.5f + (float) Math.cos(theta) * radius + (float) rng.nextGaussian() / 100.0f;
        goal_y = 0.5f + (float) Math.sin(theta) * radius + (float) rng.nextGaussian() / 100.0f;

    }

    public float getGoalX() { return goal_x; };
    public float getGoalY() { return goal_y; };
}
