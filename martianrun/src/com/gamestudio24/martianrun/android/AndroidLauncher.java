/*
 * Copyright (c) 2014. William Mora
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.gamestudio24.martianrun.android;

import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RelativeLayout;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.gamestudio24.martianrun.MartianRun;
import com.gamestudio24.martianrun.utils.GameEventListener;
import com.gamestudio24.martianrun.utils.GameManager;

public class AndroidLauncher extends AndroidApplication implements
        GameEventListener {

    private static String SAVED_LEADERBOARD_REQUESTED = "SAVED_LEADERBOARD_REQUESTED";
    private static String SAVED_ACHIEVEMENTS_REQUESTED = "SAVED_ACHIEVEMENTS_REQUESTED";

    private boolean mLeaderboardRequested;
    private boolean mAchievementsRequested;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Create the layout
        RelativeLayout layout = new RelativeLayout(this);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().clearFlags(
                WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);

        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();

        // Game view
        View gameView = initializeForView(new MartianRun(this), config);
        layout.addView(gameView);
        
        setContentView(layout);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SAVED_LEADERBOARD_REQUESTED, mLeaderboardRequested);
        outState.putBoolean(SAVED_ACHIEVEMENTS_REQUESTED, mAchievementsRequested);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mLeaderboardRequested = savedInstanceState.getBoolean(SAVED_LEADERBOARD_REQUESTED, false);
        mAchievementsRequested = savedInstanceState.getBoolean(SAVED_ACHIEVEMENTS_REQUESTED, false);
    }


    private RelativeLayout.LayoutParams getAdParams() {
        RelativeLayout.LayoutParams adParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);

        adParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);

        return adParams;
    }

    @Override
    public void displayAd() {

    }

    @Override
    public void hideAd() {

    }

    @Override
    public void submitScore(int score) {
        GameManager.getInstance().saveScore(score);
    }

    @Override
    public void displayLeaderboard() {
        mLeaderboardRequested = true;
    }

    @Override
    public void displayAchievements() {
        mAchievementsRequested = true;
    }

    @Override
    public void share() {

    }

    @Override
    public void unlockAchievement(String id) {

    }

    @Override
    public void incrementAchievement(String id, int steps) {

    }

    @Override
    public String getGettingStartedAchievementId() {
        return null;
    }

    @Override
    public String getLikeARoverAchievementId() {
        return null;
    }

    @Override
    public String getSpiritAchievementId() {
        return null;
    }

    @Override
    public String getCuriosityAchievementId() {
        return null;
    }

    @Override
    public String get5kClubAchievementId() {
        return null;
    }

    @Override
    public String get10kClubAchievementId() {
        return null;
    }

    @Override
    public String get25kClubAchievementId() {
        return null;
    }

    @Override
    public String get50kClubAchievementId() {
        return null;
    }

    @Override
    public String get10JumpStreetAchievementId() {
        return null;
    }

    @Override
    public String get100JumpStreetAchievementId() {
        return null;
    }

    @Override
    public String get500JumpStreetAchievementId() {
        return null;
    }

}
