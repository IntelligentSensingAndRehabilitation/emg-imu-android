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
import android.widget.Toast;

import java.util.List;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.sralab.emgimu.EmgImuBaseActivity;
import org.sralab.emgimu.service.EmgImuService;
import org.sralab.emgimu.service.EmgLogRecord;
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
    
    /** Counts number of played games */
    private static int gameOverCounter = 1;

    // Handle toggling the control mode
    public enum CONTROL_MODE { LINEAR, THRESHOLD };
    private CONTROL_MODE mMode = CONTROL_MODE.LINEAR;
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
    
    /** Time interval (ms) you have to press the backbutton twice in to exit */
    private static final long DOUBLE_BACK_TIME = 1000;
    
    /** Saves the time of the last backbutton press*/
    private long backPressed;
    
    /** To do UI things from different threads */
    public MyHandler handler;
    
    /** Hold all accomplishments */
    AccomplishmentBox accomplishmentBox;
    
    /** The view that handles all kind of stuff */
    GameView view;
    
    /** The amount of collected coins */
    int coins;
    
    /** This will increase the revive price */
    public int numberOfRevive = 1;
    
    /** The dialog displayed when the game is over*/
    GameOverDialog gameOverDialog;

    private boolean mFirstStart = false;

    private FirebaseAnalytics mFirebaseAnalytics;

    @Override
    protected void onCreateView(Bundle savedInstanceState) {
        Fabric.with(this, new Crashlytics());

        accomplishmentBox = new AccomplishmentBox();
        gameOverDialog = new GameOverDialog(this);
        handler = new MyHandler(this);
        initMusicPlayer();
        loadCoins();

        view = new GameView(this);
        setContentView(view);

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

    @Override
    protected void onServiceBinded(final EmgImuService.EmgImuBinder binder) {
        // Do nothing
        Log.d(TAG, "onServiceBinded");
        mDevices = binder.getManagedDevices();
        mService = binder;
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
    }

    private boolean mReady = false;
    @Override
    public void onDeviceReady(final BluetoothDevice device) {
        mReady = true;
        Log.d(TAG, "onDeviceReady");
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
    public void onDeviceNotSupported(final BluetoothDevice device) {
        Log.d(TAG, "onDeviceNotSupported");
        super.onDeviceNotSupported(device);
    }

    @Override
    public void onLinklossOccur(final BluetoothDevice device) {

        // The link loss may also be called when Bluetooth adapter was disabled
        if (BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            // Do nothing. We could notify the user here.
            Log.d(TAG, "onLinklossOccur");
        }
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

    /**** Methods required to use the EMG logging via RACP ****/
    @Override
    public void onEmgLogRecordReceived(BluetoothDevice device, EmgLogRecord record) {

    }

    @Override
    public void onOperationStarted(BluetoothDevice device) {

    }

    @Override
    public void onOperationCompleted(BluetoothDevice device) {

    }

    @Override
    public void onOperationFailed(BluetoothDevice device) {

    }

    @Override
    public void onOperationAborted(BluetoothDevice device) {

    }

    @Override
    public void onOperationNotSupported(BluetoothDevice device) {

    }

    @Override
    public void onDatasetClear(BluetoothDevice device) {

    }

    @Override
    public void onNumberOfRecordsRequested(BluetoothDevice device, int value) {

    }

    /**** End of methods required to use the EMG logging via RACP ****/

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
        view.pause();
        if(musicPlayer.isPlaying()){
            musicPlayer.pause();
        }
        super.onPause();
    }

    /**
     * Resumes the view (but waits the view waits for a tap)
     * and starts the music if it should be running.
     * Also checks whether the Google Play Services are available.
     */
    @Override
    protected void onResume() {
        view.drawOnce();
        if(musicShouldPlay){
            musicPlayer.start();
        }
        super.onResume();
    }
    
    /**
     * Prevent accidental exits by requiring a double press.
     */
    @Override
    public void onBackPressed() {
        if(System.currentTimeMillis() - backPressed < DOUBLE_BACK_TIME){
            super.onBackPressed();
        }else{
            backPressed = System.currentTimeMillis();
            Toast.makeText(this, getResources().getString(R.string.on_back_press), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Sends the handler the command to show the GameOverDialog.
     * Because it needs an UI thread.
     */
    public void gameOver(){
        handler.sendMessage(Message.obtain(handler, MyHandler.GAME_OVER_DIALOG));
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

        accomplishmentBox.points = 0;
        this.view.getPlayer().upgradeBitmap(accomplishmentBox.points);
    }

    /**
     * Shows the GameOverDialog when a message with code 0 is received.
     */
    static class MyHandler extends Handler{
        public static final int GAME_OVER_DIALOG = 0;
        public static final int SHOW_TOAST = 1;

        private Game game;
        
        public MyHandler(Game game){
            this.game = game;
        }
        
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what){
                case GAME_OVER_DIALOG:
                    showGameOverDialog();
                    break;
                case SHOW_TOAST:
                    Toast.makeText(game, msg.arg1, Toast.LENGTH_SHORT).show();
                    break;
            }
        }

        private void showGameOverDialog() {
            ++Game.gameOverCounter;
            game.gameOverDialog.init();
            game.gameOverDialog.show();
        }
    }
    

}
