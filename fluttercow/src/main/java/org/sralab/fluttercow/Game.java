/**
 * The Game
 * 
 * @author Lars Harmsen
 * Copyright (c) <2014> <Lars Harmsen - Quchen>
 */

package org.sralab.fluttercow;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.gson.Gson;

import org.sralab.emgimu.EmgImuBaseActivity;
import org.sralab.emgimu.logging.FirebaseGameLogger;
import org.sralab.emgimu.service.EmgImuService;
import org.sralab.emgimu.service.EmgLogRecord;
import org.sralab.emgimu.streaming.messages.EmgPwrMessage;
import org.sralab.fluttercow.sprites.PlayableCharacter;

import io.fabric.sdk.android.Fabric;

public class Game extends EmgImuBaseActivity {
    private static final String TAG = "Game";

    /** Name of the SharedPreference that saves the medals */
    public static final String coin_save = "coin_save";
    
    /** Key that saves the medal */
    public static final String coin_key = "coin_key";

    public static final float volume = 0.3f;
    /** Will play things like mooing */
    public static SoundPool soundPool = new SoundPool(5, AudioManager.STREAM_MUSIC, 0);

    // Handle toggling the control mode
    public enum CONTROL_MODE { LINEAR, THRESHOLD };
    private CONTROL_MODE mMode = CONTROL_MODE.THRESHOLD;
    public void toggleMode() {
        mMode = (mMode == CONTROL_MODE.LINEAR) ? CONTROL_MODE.THRESHOLD : CONTROL_MODE.LINEAR;
    }
    public CONTROL_MODE getMode() {
        return mMode;
    }

    /**
     * Will play songs like:
     * nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan
     * nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan
     * nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan
     * nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan nyan
     * Does someone know the second verse ???
     */
    public static MediaPlayer musicPlayer = null;
    
    /**
     * Whether the music should play or not
     */
    public boolean musicShouldPlay = false;

    /** To do UI things from different threads */
    public MyHandler handler;
    
    /** Hold all accomplishments */
    AccomplishmentBox accomplishmentBox;
    
    /** The view that handles all kind of stuff */
    GameView view;
    
    /** The amount of collected coins */
    int coins;

    double difficulty = 2.0;
    
    /** This will increase the revive price */
    public int numberOfRevive = 1;
    
    private boolean mFirstStart = false;

    private FirebaseAnalytics mFirebaseAnalytics;

    private long startTime = new Date().getTime();

    @Override
    protected void onCreateView(Bundle savedInstanceState) {

        CrashlyticsCore crashlyticsCore = new CrashlyticsCore.Builder()
                .disabled(BuildConfig.DEBUG)
                .build();
        Fabric.with(this, new Crashlytics.Builder().core(crashlyticsCore).build());

        accomplishmentBox = new AccomplishmentBox();
        handler = new MyHandler(this);
        initMusicPlayer();
        loadCoins();

        view = new GameView(this);
        view.setDifficulty(difficulty);
        setContentView(view);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Obtain the FirebaseAnalytics instance.
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, "FLUTTERCOW_CREATED");
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);
    }

    @Override
    protected void setUpView() {
        // Override this from parent to stop it trying to use the toolbar
    }

    /****** Handle the bluetooth service events ********/

    private List <BluetoothDevice> mDevices;
    private EmgImuService.EmgImuBinder mService;
    private FirebaseGameLogger mGameLogger;
    private ArrayList<Integer> roundLen = new ArrayList<>();

    @Override
    protected void onServiceBinded(final EmgImuService.EmgImuBinder binder) {
        // Do nothing
        Log.d(TAG, "onServiceBinded");
        mDevices = binder.getManagedDevices();
        mService = binder;
        mGameLogger = new FirebaseGameLogger(mService, "Flutter Cow", startTime);
    }

    @Override
    protected void onServiceUnbinded() {
        // Do nothing
    }

    @Override
    public void onDeviceConnecting(final BluetoothDevice device) {
        Log.d(TAG, "onDeviceConnecting");
    }

    @Override
    public void onDeviceConnected(final BluetoothDevice device) {
        Log.d(TAG, "onDeviceConnecting");

        // Is previously connected device might be ready and this event won't fire
        if (mService != null && mService.isReady(device)) {
            onDeviceReady(device);
        } else if (mService == null) {
            Log.w(TAG, "Probable race condition");
        }
    }

    private boolean mReady = false;
    @Override
    public void onDeviceReady(final BluetoothDevice device) {
        mReady = true;
        Log.d(TAG, "onDeviceReady");
        mService.streamPwr(device);
    }

    @Override
    public void onBondingFailed(@NonNull BluetoothDevice device) {

    }

    @Override
    public void onDeviceDisconnecting(final BluetoothDevice device) {
        Log.d(TAG, "onDeviceDisconnecting");
        view.pause();
    }

    @Override
    public void onDeviceDisconnected(final BluetoothDevice device) {
        Log.d(TAG, "onDeviceDisconnected");
    }

    @Override
    public void onLinkLossOccurred(@NonNull BluetoothDevice device) {

    }

    @Override
    public void onDeviceNotSupported(final BluetoothDevice device) {
        Log.d(TAG, "onDeviceNotSupported");
        super.onDeviceNotSupported(device);
    }

    private double last_rescaled = 0.5;
    //! Rescale the EMG power into a usable range
    private double smoothEmgPwr(final BluetoothDevice device) {
        final double TAU = 0.9;
        double rescaled = mService.getEmgPwrRescaled(device);
        rescaled = last_rescaled * TAU + rescaled * (1.0-TAU);
        last_rescaled = rescaled;

        return rescaled;
    }

    @Override
    public void onEmgBuffReceived(BluetoothDevice device, int count, double[][] data) {
    }

    @Override
    public void onEmgPwrReceived(final BluetoothDevice device, int value)
    {
        if (mDevices == null || device == null) // should not happen
            return;

        if (!mReady)
            return;

        final int position = mDevices.indexOf(device);

        // For this simple game only responding to the first wearable sensor in our list
        if (position != 0) {
            Log.d(TAG, "Power received but not for the first element");
            return;
        }

        double level = mService.getEmgPwrRescaled(device);

        if (!mFirstStart) {
            // Initialize smoothing filter
            last_rescaled = level;

            view.drawOnce();
            view.getPlayer().setHeight(0.2 + level * 0.8);
            view.getPlayer().setX(view.getWidth() / 6);
            view.resume();

            mFirstStart = true;
            return;
        }

        view.emgPwrBarGraph.setPower(0.2 + level * 0.8);

        // Apply same rescaling and also apply smoothing
        double rescaled = 0.2 + smoothEmgPwr(device) * 0.8;

        switch (mMode) {
            case LINEAR:
                // Dirty making everything understand game mechanics here. This game
                // is not that cleanly implemented in MVC architecture, though.
                if (!view.getPlayer().isDead())
                    view.getPlayer().setHeight(rescaled);
                break;
        }
    }


    @Override
    public void onEmgClick(final BluetoothDevice device) {
        if (mDevices == null || device == null) // should not happen
            return;

        if (!mReady)
            return;

        if (!mFirstStart)
            return;

        switch (mMode) {
            case THRESHOLD:
                view.tap();
        }
    }

    @Override
    public void onImuAccelReceived(BluetoothDevice device, float[][] accel) {

    }

    @Override
    public void onImuGyroReceived(BluetoothDevice device, float[][] gyro) {

    }

    @Override
    public void onImuAttitudeReceived(BluetoothDevice device, float[] quaternion) {

    }

    @Override
    public void onEmgLogFetchCompleted(BluetoothDevice device) {

    }

    @Override
    public void onEmgLogFetchFailed(BluetoothDevice device, String reason) {

    }

    @Override
    protected int getAboutTextId() {
        return R.string.cow_about_text;
    }

    /************ various methods required for the game play ***************/

    /**
     * Initializes the player with the nyan cat song
     * and sets the position to 0.
     */
    public void initMusicPlayer(){
        if(musicPlayer == null){
            // to avoid unnecessary reinitialisation
            musicPlayer = MediaPlayer.create(this, R.raw.nyan_cat_theme);
            musicPlayer.setLooping(true);
            musicPlayer.setVolume(0.3f ,0.3f);
        }
        musicPlayer.seekTo(0);    // Reset song to position 0
    }
    
    private void loadCoins(){
        SharedPreferences saves = this.getSharedPreferences(coin_save, 0);
        this.coins = saves.getInt(coin_key, 0);
    }

    /**
     * Pauses the view and the music
     */
    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        view.pause();
        if(musicPlayer.isPlaying()){
            musicPlayer.pause();
        }

        updateLog();

        super.onPause();
    }

    /**
     * Resumes the view (but waits the view waits for a tap)
     * and starts the music if it should be running.
     * Also checks whether the Google Play Services are available.
     */
    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        view.drawOnce();
        if(musicShouldPlay){
            musicPlayer.start();
        }
        super.onResume();
    }

    private class Details {
        ArrayList<Integer> roundLength;
        double difficulty;
    };

    @Override
    protected void onDestroy() {
        updateLog();
        super.onDestroy();
    }

    private void updateLog() {

        // If exiting before service is bound then do not try
        // and save
        if (mGameLogger == null)
            return;

        Gson gson = new Gson();

        Details d = new Details();
        d.roundLength = roundLen;
        d.difficulty = difficulty;
        String json = gson.toJson(d);

        double p = 0;
        for(Integer len : roundLen)
            p += len;
        p /= roundLen.size();

        Log.d(TAG, "Updating with: " + json);
        mGameLogger.finalize(p, json);
    }

    public void increaseCoin(){
        this.coins++;
        if(coins >= 50 && !accomplishmentBox.achievement_50_coins){
            accomplishmentBox.achievement_50_coins = true;
            handler.sendMessage(Message.obtain(handler,1,R.string.toast_achievement_50_coins, MyHandler.SHOW_TOAST));
        }
    }

    /**
     * What should happen, when an obstacle is passed?
     */
    public void increasePoints(){
        accomplishmentBox.points++;
        
        this.view.getPlayer().upgradeBitmap(accomplishmentBox.points);
        
        if(accomplishmentBox.points >= AccomplishmentBox.BRONZE_POINTS){
            if(!accomplishmentBox.achievement_bronze){
                accomplishmentBox.achievement_bronze = true;
                handler.sendMessage(Message.obtain(handler, MyHandler.SHOW_TOAST, R.string.toast_achievement_bronze, MyHandler.SHOW_TOAST));
            }
            
            if(accomplishmentBox.points >= AccomplishmentBox.SILVER_POINTS){
                if(!accomplishmentBox.achievement_silver){
                    accomplishmentBox.achievement_silver = true;
                    handler.sendMessage(Message.obtain(handler, MyHandler.SHOW_TOAST, R.string.toast_achievement_silver, MyHandler.SHOW_TOAST));
                }
                
                if(accomplishmentBox.points >= AccomplishmentBox.GOLD_POINTS){
                    if(!accomplishmentBox.achievement_gold){
                        accomplishmentBox.achievement_gold = true;
                        handler.sendMessage(Message.obtain(handler, MyHandler.SHOW_TOAST, R.string.toast_achievement_gold, MyHandler.SHOW_TOAST));
                    }
                }
            }
        }
    }


    private void resetView() {
        view = new GameView(this);
        view.setDifficulty(difficulty);
        setContentView(view);
        view.resume();
    }

    public void newGame() {

        handler.post(new Runnable() {
            @Override
            public void run() {
                resetView();
            }
        });

        roundLen.add(accomplishmentBox.points);
        accomplishmentBox.points = 0;
        this.view.getPlayer().upgradeBitmap(accomplishmentBox.points);
    }

    /**
     * Shows the GameOverDialog when a message with code 0 is received.
     */
    static class MyHandler extends Handler{
        public static final int SHOW_TOAST = 0;

        private Game game;
        
        public MyHandler(Game game){
            this.game = game;
        }
        
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what){
                case SHOW_TOAST:
                    Toast.makeText(game, msg.arg1, Toast.LENGTH_SHORT).show();
                    break;
            }
        }

    }
    

}
