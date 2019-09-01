package org.sralab.emgimu.controller;

import com.google.gson.Gson;

import org.sralab.emgimu.logging.FirebaseGameLogger;

import java.util.ArrayList;
import java.util.Date;
import java.util.Random;

public class GameController {

    public enum TrackingMode {CENTER_OUT, DRIFT, TARGETS};

    private Random rng;

    private float goal_x, goal_y;
    private float current_x, current_y;

    private TrackingMode trackingMode = TrackingMode.CENTER_OUT;

    // state variables for drift process
    private float vel_x, vel_y;
    private float rate = 0.1f;
    private float max_vel = 0.5f;

    // state variables for center out
    private float vel = 0.25f;
    private float radius = 0;
    private float theta = (float) Math.PI;

    // state variables for targets task
    private float target_track_vel = 1.0f;
    private float target_accept_radius = 0.1f;
    private long target_timeout = 10000;

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
            case TARGETS:
                update_targets(decoded_x, decoded_y, dt_ms);
                break;
        }
    }

    private long last_time = 0;
    private void update_targets(float decoded_x, float decoded_y, float dt_ms) {
        current_x += target_track_vel * (decoded_x - 0.5f) * dt_ms / 1000.0f;
        current_y += target_track_vel * (decoded_y - 0.5f) * dt_ms / 1000.0f;

        if (current_x < 0.0f) current_x = 0.0f;
        if (current_x > 1.0f) current_x = 1.0f;
        if (current_y < 0.0f) current_y = 0.0f;
        if (current_y > 1.0f) current_y = 1.0f;

        float distance = (float) Math.sqrt( Math.pow( current_x - goal_x, 2.0) + Math.pow( current_y - goal_y, 2.0) );

        long current_time = new Date().getTime();

        if (last_time == 0) {
            goal_x = rng.nextFloat();
            goal_y = rng.nextFloat();
            last_time = current_time;
        }
        else if ((current_time - last_time) > target_timeout) {
            LogRound(goal_x, goal_y, current_time - last_time, false);
            goal_x = rng.nextFloat();
            goal_y = rng.nextFloat();
            last_time = current_time;
        }
        else if (distance < target_accept_radius) {
            LogRound(goal_x, goal_y, current_time - last_time, true);
            goal_x = rng.nextFloat();
            goal_y = rng.nextFloat();
            last_time = current_time;
        }
    }

    private void update_center_out(float decoded_x, float decoded_y, float dt_ms) {
        current_x = decoded_x;
        current_y = decoded_y;

        radius = radius + vel * dt_ms / 1000.0f; // + (float) rng.nextGaussian() / 10.0f;
        if (radius > 0.5) {
            vel = -vel;
        } else if (radius < 0) {
            radius = 0;
            vel = -vel;
            theta = theta + (float) Math.PI / 4; // Make 8 angles
        }
        goal_x = 0.5f + (float) Math.cos(theta) * radius;
        goal_y = 0.5f + (float) Math.sin(theta) * radius;
    }

    private void update_drifting(float decoded_x, float decoded_y, float dt_ms) {
        current_x = decoded_x;
        current_y = decoded_y;

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

    float getGoalX() { return goal_x; }
    float getGoalY() { return goal_y; }
    float getCurrentX() { return current_x; }
    float getCurrentY() { return current_y; }
    TrackingMode getMode() { return trackingMode; }

    void toggleMode() {
        switch(trackingMode) {
            case DRIFT: // Currently not entering this mode
            case TARGETS:
                trackingMode = TrackingMode.CENTER_OUT;
                break;
            case CENTER_OUT:
                last_time = 0;
                trackingMode = TrackingMode.TARGETS;
                break;
        }
    }

    ///////////// Logging code

    private FirebaseGameLogger mGameLogger;

    //! Data format logged to Firebase
    private class Details {
        ArrayList<Float> x;
        ArrayList<Float> y;
        ArrayList<Float> time;
        ArrayList<Boolean> success;
    }
    ArrayList<Float> reach_x = new ArrayList<>();
    ArrayList<Float> reach_y = new ArrayList<>();
    ArrayList<Float> reach_time = new ArrayList<>();
    ArrayList<Boolean> reach_success = new ArrayList<>();

    void setGameLogger(FirebaseGameLogger logger) {
        mGameLogger = logger;
    }

    //! Called from Unity when a round ends to store the power
    public void LogRound(float x, float y, float time, boolean success) {

        if (mGameLogger == null)
            return;

        reach_x.add(x);
        reach_y.add(y);
        reach_time.add(time);
        reach_success.add(success);

        Gson gson = new Gson();

        Details d = new Details();
        d.x = reach_x;
        d.y = reach_y;
        d.time = reach_time;
        d.success = reach_success;
        String json = gson.toJson(d);

        mGameLogger.finalize(reach_x.size(), json);
    }

}
