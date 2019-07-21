package org.sralab.emgimu.controller;

import java.util.Random;

public class GameController {

    public enum TrackingMode {CENTER_OUT, DRIFT};

    private Random rng;

    private float goal_x, goal_y;

    private TrackingMode trackingMode = TrackingMode.CENTER_OUT;

    // state variables for drift process
    private float vel_x, vel_y;
    private float rate = 0.1f;
    private float max_vel = 0.5f;

    // state variables for center out
    private float vel = 0.25f;
    private float radius = 0;
    private float theta = (float) Math.PI;

    GameController() {
        rng = new Random();
        vel_x = 0.0f;
        vel_y = 0.0f;
        goal_x = 0.5f;
        goal_y = 0.5f;
    }

    void update (float decoded_x, float decoded_y, float dt_ms){
        switch (trackingMode) {
            case CENTER_OUT:
                update_center_out(decoded_x, decoded_y, dt_ms);
                break;
            case DRIFT:
                update_drifting(decoded_x, decoded_y, dt_ms);
                break;
        }
    }

    private void update_center_out(float decoded_x, float decoded_y, float dt_ms) {
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

    private void update_drifting(float decoded_x, float decoded_y, float dt_ms) {

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

    float getGoalX() { return goal_x; };
    float getGoalY() { return goal_y; };

    void toggleMode() {
        switch(trackingMode) {
            case DRIFT:
                trackingMode = TrackingMode.CENTER_OUT;
                break;
            case CENTER_OUT:
                trackingMode = TrackingMode.DRIFT;
                break;
        }
    }

}
