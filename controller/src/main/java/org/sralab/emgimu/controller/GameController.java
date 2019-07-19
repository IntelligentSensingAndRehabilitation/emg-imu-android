package org.sralab.emgimu.controller;

import java.util.Random;

public class GameController {

    Random rng;

    float goal_x, goal_y, vel_x, vel_y;
    float rate = 0.1f;
    float max_vel = 0.5f;

    public GameController() {
        rng = new Random();
        vel_x = 0.0f;
        vel_y = 0.0f;
        goal_x = 0.5f;
        goal_y = 0.5f;
    }

    public void update(float decoded_x, float decoded_y, float dt_ms) {

        vel_x += rng.nextGaussian() * rate;
        vel_y += rng.nextGaussian() * rate;

        if (vel_x > max_vel) vel_x = max_vel;
        if (vel_x < -max_vel) vel_x = -max_vel;
        if (vel_y > max_vel) vel_y = max_vel;
        if (vel_y < -max_vel) vel_y = -max_vel;

        goal_x += vel_x * dt_ms / 1000.0f;
        goal_y += vel_y * dt_ms / 1000.0f;

        // Bounce off walls
        if (goal_x > 1.0f) {
            goal_x = 2.0f - goal_x;
            vel_x = -vel_x;
        }
        if (goal_x < 0.0f) {
            goal_x = -goal_x;
            vel_x = -vel_x;
        }

        if (goal_y > 1.0f) {
            goal_y = 2.0f - goal_y;
            vel_y = -vel_y;
        }
        if (goal_y < 0.0f) {
            goal_y = -goal_y;
            vel_y = -vel_y;
        }
    }

    public float getGoalX() { return goal_x; };
    public float getGoalY() { return goal_y; };
}
