/*
 * Copyright (c) 2015, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.sralab.emgimu.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.app.NotificationCompat;

import android.support.v4.util.SimpleArrayMap;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;
import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.GooglePlayDriver;
import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.Lifetime;
import com.firebase.jobdispatcher.RetryStrategy;
import com.firebase.jobdispatcher.Trigger;
import com.google.firebase.Timestamp;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.iid.FirebaseInstanceId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric.sdk.android.Fabric;
import no.nordicsemi.android.ble.error.GattError;
import no.nordicsemi.android.log.ILogSession;
import no.nordicsemi.android.log.LogContract;
import no.nordicsemi.android.log.Logger;
import no.nordicsemi.android.ble.BleManager;
import no.nordicsemi.android.nrftoolbox.profile.multiconnect.BleMulticonnectProfileService;

import org.json.JSONArray;
import org.json.JSONException;
import org.sralab.emgimu.logging.EmgLogFetchJobService;
import org.sralab.emgimu.streaming.NetworkStreaming;

public class EmgImuService extends BleMulticonnectProfileService implements EmgImuManagerCallbacks {
	@SuppressWarnings("unused")
	private static final String TAG = "EmgImuService";

	private final static String ACTION_DISCONNECT = "no.nordicsemi.android.nrftoolbox.proximity.ACTION_DISCONNECT";
	private final static String ACTION_FIND = "no.nordicsemi.android.nrftoolbox.proximity.ACTION_FIND";
	private final static String ACTION_SILENT = "no.nordicsemi.android.nrftoolbox.proximity.ACTION_SILENT";

    public final static String EXTRA_DEVICE = BleMulticonnectProfileService.EXTRA_DEVICE;

    // Broadcast messsages for battery updates
    public static final String BROADCAST_BATTERY_LEVEL = "org.sralab.emgimu.BROADCAST_BATTERY_LEVEL";
    public static final String EXTRA_BATTERY_LEVEL = "org.sralab.emgimu.EXTRA_BATTERY";

    // Broadcast messages for EMG activity
    public static final String BROADCAST_EMG_RAW = "org.sralab.emgimu.BROADCAST_EMG_RAW";
    public static final String BROADCAST_EMG_PWR = "org.sralab.emgimu.BROADCAST_EMG_PWR";
    public static final String BROADCAST_EMG_BUFF = "org.sralab.emgimu.BROADCAST_EMG_BUFF";
    public static final String BROADCAST_EMG_CLICK = "org.sralab.emgimu.BROADCAST_EMG_CLICK";

    public static final String EXTRA_EMG_RAW = "org.sralab.emgimu.EXTRA_EMG_RAW";
    public static final String EXTRA_EMG_PWR = "org.sralab.emgimu.EXTRA_EMG_PWR";
    public static final String EXTRA_EMG_BUFF = "org.sralab.emgimu.EXTRA_EMG_BUFF";
    public static final String EXTRA_EMG_CHANNELS = "org.sralab.emgimu.EXTRA_EMG_CHANNELS";
    public static final String EXTRA_EMG_COUNT = "org.sralab.emgimu.EXTRA_EMG_COUNT";

    public static final String INTENT_FETCH_LOG = "org.sralab.INTENT_FETCH_LOG";
    public static final String INTENT_DEVICE_MAC = "org.sralab.INTENT_DEVICE_MAC";

    // Broadcast messages for IMU activity
    public static final String BROADCAST_IMU_ACCEL = "org.sralab.emgimu.BROADCAST_IMU_ACCEL";
    public static final String BROADCAST_IMU_GYRO = "org.sralab.emgimu.BROADCAST_IMU_GYRO";
    public static final String BROADCAST_IMU_MAG = "org.sralab.emgimu.BROADCAST_IMU_MAG";
    public static final String BROADCAST_IMU_ATTITUDE = "org.sralab.emgimu.BROADCAST_IMU_ATTITUDE";

    public static final String EXTRA_IMU_ACCEL = "org.sralab.emgimu.EXTRA_IMU_ACCEL";
    public static final String EXTRA_IMU_GYRO = "org.sralab.emgimu.EXTRA_IMU_GYRO";
    public static final String EXTRA_IMU_MAG = "org.sralab.emgimu.EXTRA_IMU_MAG";
    public static final String EXTRA_IMU_ATTITUDE = "org.sralab.emgimu.EXTRA_IMU_ATTITUDE";

    public static final String SERVICE_PREFERENCES = "org.sralab.emgimu.PREFERENCES";
    public static final String DEVICE_PREFERENCE = "org.sralab.emgimu.DEVICE_LIST";
    public static final String MIN_PWR_PREFERENCE = "org.sralab.emgimu.MIN_PWR_PREFERENCE";
    public static final String MAX_PWR_PREFERENCE = "org.sralab.emgimu.MAX_PWR_PREFERENCE";
    public static final String THRESHOLD_LOW_PREFERENCE = "org.sralab.emgimu.THRESHOLD_LOW_PREFERENCE";
    public static final String THRESHOLD_HIGH_PREFERENCE = "org.sralab.emgimu.THRESHOLD_HIGH_PREFERENCE";

	private final static String EMGIMU_GROUP_ID = "emgimu_connected_sensors";
	private final static int NOTIFICATION_ID = 1000;
	private final static int OPEN_ACTIVITY_REQ = 0;
	private final static int DISCONNECT_REQ = 1;

	// TODO: optimize for device battery life, handle condition
    // when sensor is not detected (does not influence battery)
    private final static int LOG_FETCH_PERIOD_MIN_S = 5*60;
    private final static int LOG_FETCH_PERIOD_MAX_S = 15*60;

	private final EmgImuBinder mBinder = new EmgImuBinder();

	private FirebaseAuth mAuth;
    private FirebaseAnalytics mFirebaseAnalytics;
    private FirebaseUser mCurrentUser;
    private String mToken;

    private ILogSession mLogSession;
    private ServiceLogger mServiceLogger = new ServiceLogger(TAG, this, mLogSession);

    private NetworkStreaming networkStreaming;

	/**
	 * This local binder is an interface for the bonded activity to operate with the proximity sensor
	 */
	public class EmgImuBinder extends LocalBinder {

	    @Override
        /**
         * Override the parent method to ensure that the device list is updated, even
         * if we are not connected to the device. The onDeviceDisconnected callback only
         * occurs if we are initially connected.
         */
        public void disconnect(final BluetoothDevice device) {
            super.disconnect(device);
            updateSavedDevices();
        }

        /***
         * Check is all devices are connected
         * @return true if all the devices are saved are connected
         */
        public boolean isConnected() {
            List<BluetoothDevice> devices = getSavedDevices();
            for (BluetoothDevice device : devices) {
                if (!mBinder.isConnected(device))
                    return false;
                //final EmgImuManager manager = getBleManager(device);
            }
            return true;
        }

        /**
         * Returns the last received EMG raw value.
         * @param device the device of which battery level should be returned
         * @return emg value or -1 if no value was received or characteristic was not found
         */
        public int getEmgRawValue(final BluetoothDevice device) {
            final EmgImuManager manager = (EmgImuManager) getBleManager(device);
            return manager.getEmgRaw();
        }

        /**
         * Returns the last received EMG PWR value.
         * @param device the device of which battery level should be returned
         * @return emg value or -1 if no value was received or characteristic was not found
         */
        public int getEmgPwrValue(final BluetoothDevice device) {
            final EmgImuManager manager = (EmgImuManager) getBleManager(device);
            return manager.getEmgPwr();
        }

        /**
         * Returns the last received EMG PWR rescaled.
         * @param device the device of which battery level should be returned
         * @return emg value or -1 if no value was received or characteristic was not found
         */
        public double getEmgPwrRescaled(final BluetoothDevice device) {
            final EmgImuManager manager = (EmgImuManager) getBleManager(device);
            return manager.getEmgPwrScaled();
        }

        /**
         * Returns the last received EMG buffered value.
         * @param device the device of which battery level should be returned
         * @return emg value or -1 if no value was received or characteristic was not found
         */
        public double [][] getEmgBuffValue(final BluetoothDevice device) {
            final EmgImuManager manager = (EmgImuManager) getBleManager(device);
            return manager.getEmgBuff();
        }

        /**
         * Configure a device to stream the EMG processed power (default)
         */
        public void streamPwr(final BluetoothDevice device) {
            final EmgImuManager manager = (EmgImuManager) getBleManager(device);
            manager.enablePowerStreamingMode();
        }

        /**
         * Configure a device to stream the buffered data stream
         */
        public void streamBuffered(final BluetoothDevice device) {
            final EmgImuManager manager = (EmgImuManager) getBleManager(device);
            manager.enableBufferedStreamingMode();
        }

        public EmgImuManager.STREAMING_MODE getStreamingMode(final BluetoothDevice device) {
            final EmgImuManager manager = (EmgImuManager) getBleManager(device);
            return manager.getStreamingMode();
        }

       public void enableAttitude(final BluetoothDevice device) {
            final EmgImuManager manager = (EmgImuManager) getBleManager(device);
            manager.enableAttitude();
        }

        public void disableAttitude(final BluetoothDevice device) {
            final EmgImuManager manager = (EmgImuManager) getBleManager(device);
            manager.disableAttitude();
        }

        public void enableImu(final BluetoothDevice device) {
            final EmgImuManager manager = (EmgImuManager) getBleManager(device);
            manager.enableImu();
        }

        public void disableImu(final BluetoothDevice device) {
            final EmgImuManager manager = (EmgImuManager) getBleManager(device);
            manager.disableImu();
        }
        //! Set threshold
        public void setClickThreshold(final BluetoothDevice device, float min, float max) {
            final EmgImuManager manager = (EmgImuManager) getBleManager(device);
            manager.setClickThreshold(min, max);
        }

        //! Set threshold
        public void setPwrRange(final BluetoothDevice device, float min, float max) {
            final EmgImuManager manager = (EmgImuManager) getBleManager(device);
            manager.setPwrRange(min, max);
        }

        //! Get threshold
        public float getClickThreshold(final BluetoothDevice device) {
            final EmgImuManager manager = (EmgImuManager) getBleManager(device);
            return manager.getHighThreshold();
        }

        //! Get threshold
        public float getMinPwr(final BluetoothDevice device) {
            final EmgImuManager manager = (EmgImuManager) getBleManager(device);
            return manager.getMinPwr();
        }

        //! Get threshold
        public float getMaxPwr(final BluetoothDevice device) {
            final EmgImuManager manager = (EmgImuManager) getBleManager(device);
            return manager.getMaxPwr();
        }

        //! Get battery
        public double getBattery(final BluetoothDevice device) {
            final EmgImuManager manager = (EmgImuManager) getBleManager(device);
            return manager.getBatteryVoltage();
        }

        public int getChannelCount(final BluetoothDevice device) {
            final EmgImuManager manager = (EmgImuManager) getBleManager(device);
            return manager.getChannelCount();
        }

        public void startCalibration(final BluetoothDevice device, EmgImuManager.CalibrationListener listener) {
            // TODO: needs to have some type of callback listener to visualize
            final EmgImuManager manager = (EmgImuManager) getBleManager(device);
            manager.startCalibration(listener);
        }

        public void finishCalibration(final BluetoothDevice device, EmgImuManager.CalibrationListener listener) {
            // TODO: needs to have some type of callback listener to visualize
            final EmgImuManager manager = (EmgImuManager) getBleManager(device);
            manager.finishCalibration(listener);
        }

        public int getLoggerProfileTitle() {
            return R.string.emgimu_feature_title;
            //return 0; // Use the line above to enable logging, but this slows down application
        }

        public void updateSavedDevices() {
            Log.d(TAG, "updateSavedDevices called on binder");
            serviceUpdateSavedDevices();
            configureLoggingSavedDevices();
        }

        public String getUser() {
            if (mCurrentUser != null)
                return mCurrentUser.getUid();
            return "";
        }

        /*** hook these methods so we can forward the message to the Service nRF log and logcat ***/
        @Override
        public void log(final BluetoothDevice device, final int level, final String message) {
            super.log(device, level, message);
            mServiceLogger.log(level, device.getAddress() + " : " + message);
        }

        @Override
        public void log(final BluetoothDevice device, final int level, @StringRes final int messageRes, final Object... params) {
            super.log(device, level, messageRes, params);
            mServiceLogger.log(level, messageRes, params);
        }

        @Override
        public void log(final int level, final String message) {
            super.log(level, message);
            mServiceLogger.log(level, message);
        }

        @Override
        public void log(final int level, @StringRes final int messageRes, final Object... params) {
            super.log(level, messageRes, params);
            mServiceLogger.log(level, messageRes, params);
        }

        public List<String> getLoggingReferences() {
            List<String> references = new ArrayList<>();
            for (final BluetoothDevice device : getManagedDevices()) {
                final EmgImuManager manager = (EmgImuManager) getBleManager(device);
                references.add(manager.getLoggingRef());
            }

            return references;
        }

	}

	@Override
	protected LocalBinder getBinder() {
		return mBinder;
	}

	@Override
	protected BleManager<EmgImuManagerCallbacks> initializeManager() {
		return new EmgImuManager(this);
	}

	/**
	 * This broadcast receiver listens for {@link #ACTION_DISCONNECT} that may be fired by pressing Disconnect action button on the notification.
	 */
	private final BroadcastReceiver mDisconnectActionBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			final BluetoothDevice device = intent.getParcelableExtra(EXTRA_DEVICE);
			mBinder.log(device, LogContract.Log.Level.INFO, "[Notification] DISCONNECT action pressed");
			mBinder.disconnect(device);
		}
	};


	@Override
    /**
     * Always called when service started, either by binding or startService. call from
     * parent onCreate prior to starting bluetooth
     */
	protected void onServiceCreated() {

        CrashlyticsCore crashlyticsCore = new CrashlyticsCore.Builder()
                .disabled(BuildConfig.DEBUG)
                .build();
        Fabric.with(this, new Crashlytics.Builder().core(crashlyticsCore).build());

        final int titleId = mBinder.getLoggerProfileTitle();
        if (titleId > 0) {
            mLogSession = Logger.newSession(getApplicationContext(), "Service", "Service", "Service");
            mServiceLogger = new ServiceLogger(TAG, this, mLogSession);
        }

        mServiceLogger.d("onServiceCreated");

	    registerReceiver(mDisconnectActionBroadcastReceiver, new IntentFilter(ACTION_DISCONNECT));

        mAuth = FirebaseAuth.getInstance();
        mToken = null;

        // Check if user is signed in (non-null) and update UI accordingly.
        mCurrentUser = mAuth.getCurrentUser();
        if (mCurrentUser == null) {
            Log.d(TAG, "Attempting to log in to firebase");
            mAuth.signInAnonymously()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            mCurrentUser = mAuth.getCurrentUser();

                            Log.d(TAG, "signInAnonymously:success. UID:" + mCurrentUser.getUid());
                            mServiceLogger.d("signInAnonymously:success. UID:" + mCurrentUser.getUid());

                            // It can happen that either one is set first
                            if (mToken != null && mCurrentUser != null)
                                storeToken();
                        } else {
                            // If sign in fails, display a message to the user.
                            mServiceLogger.w("signInAnonymously:failure" + task.getException());
                            Log.d(TAG, "signInAnonymously:failure" + task.getException());
                        }
                    });
        } else {
            mServiceLogger.d("User ID: " + mCurrentUser.getUid());
        }


        FirebaseInstanceId.getInstance().getInstanceId().addOnSuccessListener(instanceIdResult -> {
            mToken = instanceIdResult.getToken();
            Log.d(TAG, "Received token: " + mToken);

            // It can happen that either one is set first
            if (mToken != null && mCurrentUser != null)
                storeToken();

        });

        // Obtain the FirebaseAnalytics instance.
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, "EMG_IMU_SERVICE_CREATED");
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);

        //networkStreaming = new NetworkStreaming();
        //networkStreaming.start();
	}

    private void storeToken()
    {
        Log.d(TAG, "Updating token for user in firestore");
        FirebaseFirestore mDb = FirebaseFirestore.getInstance();
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder().build();
        mDb.setFirestoreSettings(settings);
        DocumentReference doc = mDb.collection("fcmTokens").document(mCurrentUser.getUid());

        Map<String, Object> data = new HashMap<>();
        data.put("token", mToken);
        data.put("updated", Timestamp.now());
        doc.set(data);
    }

    private final SimpleArrayMap<String, Pair<Runnable, Integer>> logFetchStartId = new SimpleArrayMap<>();

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {

        if (intent == null) {
            mServiceLogger.d("onStartCommand called without intent. flags: " + flags + " startId " + startId);
        } else if (intent.getExtras() != null) {
            mServiceLogger.d("onStartCommand: " + intent.toString() + " extras: " + intent.getExtras().toString() + " flags: " + flags + " startId" + startId);
        } else {
            mServiceLogger.d("onStartCommand: "  + intent.toString() + " flags: " + flags + " startId" + startId);
        }

        // See if there is an intent indicating the service was started unbound to
        // acquire a log. Only start this when not already connected to the device.
        if (intent != null && intent.getBooleanExtra(EmgImuService.INTENT_FETCH_LOG, false)) {

            createSummaryNotification();

            String device_mac = intent.getStringExtra(INTENT_DEVICE_MAC);

            mServiceLogger.d("onStartCommand due to requesting logs from " + device_mac);


            // Detect if bluetooth is enabled and if not don't attempt to get log
            final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (!bluetoothAdapter.isEnabled()) {
                mServiceLogger.e("Unable to download log as bluetooth is disabled");
                stopSelf(startId);
                return START_NOT_STICKY;
            }

            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(device_mac);

            List<BluetoothDevice> devices = getManagedDevices();
            for (BluetoothDevice d : devices) {
                if (d.getAddress().equals(device.getAddress())) {
                    mServiceLogger.d("Already managing " + device_mac + " so will skip fetching log");
                    return START_NOT_STICKY;
                }
            }

            synchronized (logFetchStartId) {
                if (logFetchStartId.containsKey(device_mac)) {
                    // This really shouldn't happen as prior fetches should complete or clear up
                    mServiceLogger.e("Log fetching running for " + device_mac);
                    return START_NOT_STICKY;
                }


                Runnable connectionTimeout = () -> {
                    mServiceLogger.i("Unable to connect to " + device_mac + " in one second");
                    smartStop(device);
                };
                // Note if we were showing the notification based on connecting, then
                // this would need to be shorter. However, we show the notification
                // regardless so can keep this longer to make sure we don't fail on
                // slower devices.
                getHandler().postDelayed(connectionTimeout, 5000);

                logFetchStartId.put(device_mac, new Pair<>(connectionTimeout, startId));
            }

            mBinder.connect(device,  getLogger(device));
        }

        return START_STICKY;
    }

    /**
     * Handles stopping service based on the thread ID. This is a bit complicated because
     * it will stopSelf(threadId) will stop if it is the most recent (greatest) thread ID
     * but we want to only stop when it is the last one.
     */
    private void smartStop(BluetoothDevice device) {
        synchronized(logFetchStartId) {
            Integer startThreadId = logFetchStartId.get(device.getAddress()).second;

            // To avoid this being double called remove the timer
            mServiceLogger.i("Removing connection timeout runnable from " + device.getAddress());
            getHandler().removeCallbacks(logFetchStartId.get(device.getAddress()).first);

            // If there is only one thread remaining, then appropriate to stop it
            if (logFetchStartId.size() == 1) {

                logFetchStartId.remove(device.getAddress());

                if (mBinded) {
                    mBinder.log(device, LogContract.Log.Level.DEBUG, "Last log retrieval thread completed. Not stopped service as service also bound");
                } else {
                    mBinder.log(device, LogContract.Log.Level.DEBUG, "One thread found so stopping service: " + logFetchStartId);
                    stopSelf(startThreadId);
                }
                return;
            }

            // Work out the maximum thread Id
            Integer maxThreadId = 0;
            for (int i = 0; i < logFetchStartId.size(); i++) {
                if (logFetchStartId.valueAt(i).second > maxThreadId) {
                    maxThreadId = logFetchStartId.valueAt(i).second;
                }
            }

            if (startThreadId >= maxThreadId) {
                mBinder.log(device, LogContract.Log.Level.DEBUG, "Multiple threads and this is the greatest. Just removing element " + startThreadId +
                        " " + logFetchStartId);

                // If there are multiple threads and this one is the highest number we should
                // only remove its ID
                logFetchStartId.remove(device.getAddress());

                // However later stop needs to use this maxThreadId which we removed. Arbitrarily
                // assign to the first device..
                logFetchStartId.setValueAt(0, new Pair<>(logFetchStartId.valueAt(0).first, maxThreadId));
            } else {
                mBinder.log(device, LogContract.Log.Level.DEBUG, "This is not the lowest thread. Service should not stop after " + startThreadId + " "
                        + logFetchStartId);

                // This call to stopSelf should make no functional difference
                stopSelf(startThreadId);
                logFetchStartId.remove(device.getAddress());
            }
        }
    }

    @Override
    public void onError(BluetoothDevice device, String message, int errorCode) {
        super.onError(device, message, errorCode);

        if (errorCode == GattError.GATT_INVALID_PDU) {
            Log.e(TAG, "Received invalid PDU. Will continue", new Exception("backtrack"));
            return;
        }
        mBinder.log(device, LogContract.Log.Level.WARNING, "onError: " + message +
                " errorCode: " + errorCode + "(" + GattError.parseConnectionError(errorCode) + ")");
        Log.e(TAG, "onError", new Exception("backtrack"));

        // See if a log fetch has been requested
        Pair<Runnable, Integer> p = logFetchStartId.get(device.getAddress());
        if (p != null) {
            mServiceLogger.d("Unable to download log from " + device.getAddress() + " list of threads " + logFetchStartId);
            smartStop(device);
        }
    }

    @Override
    public IBinder onBind(final Intent intent) {

        if (intent != null)
            mServiceLogger.d("onBind: " + intent.toString());
        else
            mServiceLogger.d("onBind with no intent");

        // We have been starting by a service. Attempt to
        loadAndConnectSavedDevices();

        return super.onBind(intent);
    }

    @Override
	public void onServiceStopped() {
        mBinder.log(LogContract.Log.Level.INFO, "onServicesStopped");

		cancelNotifications();

		unregisterReceiver(mDisconnectActionBroadcastReceiver);

		super.onServiceStopped();

        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, "EMG_IMU_SERVICE_STOPPED");
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);

        if (networkStreaming != null)
            networkStreaming.stop();
    }

    @Override
    public final void onRebind(final Intent intent) {
        mServiceLogger.d("onRebind: " + intent);

        super.onRebind(intent);

    }

    @Override
	protected void onRebind() {

		// When the activity rebinds to the service, remove the notification
		//cancelNotifications();
	}

	@Override
	public void onUnbind() {
        mBinder.log(LogContract.Log.Level.INFO, "onUnbind");

        getHandler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mBinded == false) {
                    mServiceLogger.i("Timeout occurred and service still not bound. Shutting down.");
                    stopSelf();

                }
            }
        }, 5000);
    }

	@Override
	public void onDeviceConnected(final BluetoothDevice device) {
        mBinder.log(device, LogContract.Log.Level.INFO, "onDeviceConnected");

		super.onDeviceConnected(device);

        // See if a log fetch has been requested
        Pair<Runnable, Integer> p = logFetchStartId.get(device.getAddress());
        if (p != null) {
            mServiceLogger.i("Removing connection timeout runnable from " + device.getAddress());
            getHandler().removeCallbacks(p.first);
        }

        if (!mBinded) {
			createBackgroundNotification();
		}

        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, "EMG_IMU_SERVICE_CONNECTED");
        bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, device.getName());
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);
    }

	@Override
	public void onDeviceDisconnected(final BluetoothDevice device) {
		super.onDeviceDisconnected(device);

		if (!mBinded) {
			cancelNotification(device);
			createBackgroundNotification();
		}
	}

    @Override
    public void onLinkLossOccurred(@NonNull BluetoothDevice device) {
        super.onLinkLossOccurred(device);
        Log.d(TAG, "onLinkLossOccurred");
    }

    @Override
    public void onDeviceReady(BluetoothDevice device) {
        super.onDeviceReady(device);

        // See if a log fetch has been requested
        if(logFetchStartId.get(device.getAddress()) != null) {
            mBinder.log(device, LogContract.Log.Level.INFO, "onDeviceReady. requesting log download");
            final EmgImuManager manager = (EmgImuManager) getBleManager(device);
            manager.fetchLogRecords(device1 -> onEmgLogFetchCompleted(device1), (device12, reason) -> onEmgLogFetchFailed(device12, reason));
        } else {
            mBinder.log(device, LogContract.Log.Level.WARNING, "onDeviceReady. no log request active.");
        }
    }

    @Override
    public void onBondingFailed(@NonNull BluetoothDevice device) {

    }

    private void serviceUpdateSavedDevices
            () {
		// Need to access context this way so all apps using service (and with the sharedUserId)
		// have the same preferences and connect to the same devices
        Context mContext = null;
        try {
            mContext = this.createPackageContext("org.sralab.emgimu", Context.CONTEXT_RESTRICTED);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        SharedPreferences sharedPref = mContext.getSharedPreferences(SERVICE_PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        JSONArray names = new JSONArray();
		for (BluetoothDevice device : getManagedDevices()) {
            names.put(device.getAddress());
        }

        // Set of connected devices has changed
        String currentNames = sharedPref.getString(DEVICE_PREFERENCE, "[]");
		if (!currentNames.equals(names)) {
            editor.putString(DEVICE_PREFERENCE, names.toString());
            editor.commit();
        }
	}

	private ILogSession getLogger(final BluetoothDevice device) {
        final int titleId = mBinder.getLoggerProfileTitle();
        ILogSession logSession = null;
        if (titleId > 0) {
            logSession = Logger.newSession(getApplicationContext(), "Device", device.getAddress(), device.getName());
        }
        return logSession;
	}

	private void configureLoggingSavedDevices() {

        // Create a new dispatcher using the Google Play driver.
        FirebaseJobDispatcher dispatcher = new FirebaseJobDispatcher(new GooglePlayDriver(this));

        Log.d(TAG, "Canceling log fetching jobs");
        dispatcher.cancelAll();

        // Need to access context this way so all apps using service (and with the sharedUserId)
        // have the same preferences and connect to the same devices
        Context mContext = null;
        try {
            mContext = this.createPackageContext("org.sralab.emgimu", Context.CONTEXT_RESTRICTED);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        SharedPreferences sharedPref = mContext.getSharedPreferences(SERVICE_PREFERENCES, Context.MODE_PRIVATE);
        String deviceList_s = sharedPref.getString(DEVICE_PREFERENCE, "[]");

        try {
            JSONArray names = new JSONArray(deviceList_s);
            for (int i = 0; i < names.length(); i++) {
                Bundle jobInfo = new Bundle();
                jobInfo.putString("device_mac", names.getString(i));

                mServiceLogger.d("Scheduling log fetching jobs for " + names.getString(i));

                Job myJob = dispatcher.newJobBuilder()
                        .setService(EmgLogFetchJobService.class)             // the JobService that will be called
                        .setTag(EmgLogFetchJobService.JOB_TAG + "_" + names.getString(i))               // uniquely identifies the job
                        .setTrigger(Trigger.executionWindow(LOG_FETCH_PERIOD_MIN_S, LOG_FETCH_PERIOD_MAX_S))
                        .setLifetime(Lifetime.FOREVER)                       // run after boot
                        .setRecurring(true)                                  // tasks reoccurs
                        .setRetryStrategy(RetryStrategy.DEFAULT_LINEAR) // default strategy
                        .setExtras(jobInfo)                                  // store the mac address
                        .setReplaceCurrent(true)
                        .build();
                int result = dispatcher.schedule(myJob);
                if (result != FirebaseJobDispatcher.SCHEDULE_RESULT_SUCCESS) {
                    mServiceLogger.e("Scheduling log fetch failed");
                    showToast("Unable to schedule log fetching.");
                    if (BuildConfig.DEBUG)
                        throw new RuntimeException("Unable to schedule job fetching");
                } else {
                    mServiceLogger.d("Job scheduled successfully");
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    List<BluetoothDevice> getSavedDevices() {
        // Need to access context this way so all apps using service (and with the sharedUserId)
        // have the same preferences and connect to the same devices
        Context mContext = null;
        try {
            mContext = this.createPackageContext("org.sralab.emgimu", Context.CONTEXT_RESTRICTED);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        SharedPreferences sharedPref = mContext.getSharedPreferences(SERVICE_PREFERENCES, Context.MODE_PRIVATE);
        String deviceList_s = sharedPref.getString(DEVICE_PREFERENCE, "[]");
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        ArrayList <BluetoothDevice> devices = new ArrayList<BluetoothDevice>();
        try {
            JSONArray names = new JSONArray(deviceList_s);
            for (int i = 0; i < names.length(); i++) {
                String device_mac = names.getString(i);
                devices.add(bluetoothAdapter.getRemoteDevice(device_mac));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        mServiceLogger.d("Saved devices: " + devices);
        return Collections.unmodifiableList(devices);
    }

	private void loadAndConnectSavedDevices() {
        mServiceLogger.d("LoadAndConnectSaveDevices");

        List <BluetoothDevice> devices = getSavedDevices();

        for (final BluetoothDevice device : devices)
            mBinder.connect(device, getLogger(device));
    }

	private void createBackgroundNotification() {
        /*
        // This presents per-device notifications that allow disconnecting. Not necessary for now.
		final List<BluetoothDevice> connectedDevices = getConnectedDevices();
		for (final BluetoothDevice device : connectedDevices) {
			createNotificationForConnectedDevice(device);
		}
		*/
		createSummaryNotification();
	}

	private void createSummaryNotification() {
		final NotificationCompat.Builder builder = getNotificationBuilder();
		builder.setColor(ContextCompat.getColor(this, R.color.actionBarColorDark));
		builder.setShowWhen(false).setDefaults(0).setOngoing(true); // an ongoing notification will not be shown on Android Wear
		builder.setGroup(EMGIMU_GROUP_ID).setGroupSummary(true);
		builder.setContentTitle(getString(R.string.service_name));

		final List<BluetoothDevice> managedDevices = getManagedDevices();
		final List<BluetoothDevice> connectedDevices = getConnectedDevices();
		if (connectedDevices.isEmpty()) {
			// No connected devices
			final int numberOfManagedDevices = managedDevices.size();
			if (numberOfManagedDevices == 1) {
				final String name = getDeviceName(managedDevices.get(0));
				// We don't use plurals here, as we only have the default language and 'one' is not in every language (versions differ in %d or %s)
				// and throw an exception in e.g. in Chinese
				builder.setContentText(getString(R.string.emgimu_notification_text_nothing_connected_one_disconnected, name));
			} else {
				builder.setContentText(getString(R.string.emgimu_notification_text_nothing_connected_number_disconnected, numberOfManagedDevices));
			}
		} else {
			// There are some proximity tags connected
			final StringBuilder text = new StringBuilder();

			final int numberOfConnectedDevices = connectedDevices.size();
			if (numberOfConnectedDevices == 1) {
				final String name = getDeviceName(connectedDevices.get(0));
				text.append(getString(R.string.emgimu_notification_summary_text_name, name));
			} else {
				text.append(getString(R.string.emgimu_notification_summary_text_number, numberOfConnectedDevices));
			}

			// If there are some disconnected devices, also print them
			final int numberOfDisconnectedDevices = managedDevices.size() - numberOfConnectedDevices;
			if (numberOfDisconnectedDevices == 1) {
				text.append(", ");
				// Find the single disconnected device to get its name
				for (final BluetoothDevice device : managedDevices) {
					if (!isConnected(device)) {
						final String name = getDeviceName(device);
						text.append(getString(R.string.emgimu_notification_text_nothing_connected_one_disconnected, name));
						break;
					}
				}
			} else if (numberOfDisconnectedDevices > 1) {
				text.append(", ");
				// If there are more, just write number of them
				text.append(getString(R.string.emgimu_notification_text_nothing_connected_number_disconnected, numberOfDisconnectedDevices));
			}
			builder.setContentText(text);
		}

		final Notification notification = builder.build();
		final NotificationManagerCompat nm = NotificationManagerCompat.from(this);
		nm.notify(NOTIFICATION_ID, notification);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, notification);
        }
	}

	/**
	 * Creates the notification for given connected device.
	 * Adds 1 action buttons: DISCONNECT which perform given action on the device.
	 */
	private void createNotificationForConnectedDevice(final BluetoothDevice device) {
		final NotificationCompat.Builder builder = getNotificationBuilder();
		builder.setColor(ContextCompat.getColor(this, R.color.actionBarColorDark));
		builder.setGroup(EMGIMU_GROUP_ID).setDefaults(0).setOngoing(true); // an ongoing notification will not be shown on Android Wear
		builder.setContentTitle(getString(R.string.emgimu_notification_text, getDeviceName(device)));

		// Add DISCONNECT action
		final Intent disconnect = new Intent(ACTION_DISCONNECT);
		disconnect.putExtra(EXTRA_DEVICE, device);
		final PendingIntent disconnectAction = PendingIntent.getBroadcast(this, DISCONNECT_REQ + device.hashCode(), disconnect, PendingIntent.FLAG_UPDATE_CURRENT);
		builder.addAction(new NotificationCompat.Action(R.drawable.ic_action_bluetooth, getString(R.string.emgimu_action_disconnect), disconnectAction));
		builder.setSortKey(getDeviceName(device) + device.getAddress()); // This will keep the same order of notification even after an action was clicked on one of them

		final Notification notification = builder.build();
		final NotificationManagerCompat nm = NotificationManagerCompat.from(this);
		nm.notify(device.getAddress(), NOTIFICATION_ID, notification);
	}


	private NotificationCompat.Builder getNotificationBuilder() {

        String channelId = "";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channelId = getString(R.string.default_notification_channel_id);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(channelId, "EMG IMU Service", NotificationManager.IMPORTANCE_MIN));
        }

        final Intent parentIntent = new Intent(this, EmgImuService.class);
		parentIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		final Intent targetIntent = new Intent(this, EmgImuService.class);

		// Both activities above have launchMode="singleTask" in the AndroidManifest.xml file, so if the task is already running, it will be resumed
		final PendingIntent pendingIntent = PendingIntent.getActivities(this, OPEN_ACTIVITY_REQ, new Intent[] { parentIntent, targetIntent }, PendingIntent.FLAG_UPDATE_CURRENT);

		final NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId);
		builder.setContentIntent(pendingIntent).setAutoCancel(false);
		builder.setSmallIcon(R.drawable.ic_stat_notify_proximity);
		return builder;
	}

	/**
	 * Cancels the existing notification. If there is no active notification this method does nothing
	 */
	private void cancelNotifications() {
		final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		nm.cancel(NOTIFICATION_ID);

		/*
		final List<BluetoothDevice> managedDevices = getManagedDevices();
		for (final BluetoothDevice device : managedDevices) {
			nm.cancel(device.getAddress(), NOTIFICATION_ID);
		}
		*/
	}

	/**
	 * Cancels the existing notification for given device. If there is no active notification this method does nothing
	 */
	private void cancelNotification(final BluetoothDevice device) {
	    /* // Does nothing right now as we are not creating per-device notifications
		final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		nm.cancel(device.getAddress(), NOTIFICATION_ID);
		*/
	}

    @Override
    public void onBatteryReceived(BluetoothDevice device, float battery) {
        final Intent broadcast = new Intent(BROADCAST_BATTERY_LEVEL);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_BATTERY_LEVEL, battery);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onEmgRawReceived(final BluetoothDevice device, int value)
    {
        final Intent broadcast = new Intent(BROADCAST_EMG_RAW);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_EMG_RAW, value);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    public void onEmgPwrReceived(final BluetoothDevice device, int value)
    {
        final Intent broadcast = new Intent(BROADCAST_EMG_PWR);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_EMG_PWR, value);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);

        if (networkStreaming != null && networkStreaming.isConnected()) {
            double [] data = {(double) value};
            networkStreaming.streamEmgPwr(device, 0, data);
        }
    }

    @Override
    public void onEmgBuffReceived(BluetoothDevice device, int count, double[][] data) {
        final int CHANNELS = data.length;
        final int SAMPLES = data[0].length;
	    double [] linearizedData = new double[CHANNELS * SAMPLES];

	    for (int i = 0; i < CHANNELS; i++)
	        for (int j = 0; j < SAMPLES; j++)
	            linearizedData[i + j * CHANNELS] = data[i][j];

        final Intent broadcast = new Intent(BROADCAST_EMG_BUFF);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_EMG_CHANNELS, CHANNELS);
        broadcast.putExtra(EXTRA_EMG_COUNT, count);
        broadcast.putExtra(EXTRA_EMG_BUFF, linearizedData);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);

        if (networkStreaming != null && networkStreaming.isConnected()) {
            networkStreaming.streamEmgBuffer(device, 0, SAMPLES, CHANNELS, data);
        }
    }

    public void onEmgClick(final BluetoothDevice device) {
        final Intent broadcast = new Intent(BROADCAST_EMG_CLICK);
        broadcast.putExtra(EXTRA_DEVICE, device);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onImuAccelReceived(BluetoothDevice device, float[][] accel) {
        float [] linearizedData = new float[3 * 3];

        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                linearizedData[i + j * 3] = accel[i][j];

        final Intent broadcast = new Intent(BROADCAST_IMU_ACCEL);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_IMU_ACCEL, linearizedData);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);

        /*
        if (networkStreaming != null && networkStreaming.isConnected()) {
            double [] data = {(double) value};
            networkStreaming.streamImuAttitude(device, 0, data);
        }
        */
    }

    @Override
    public void onImuGyroReceived(BluetoothDevice device, float[][] gyro) {
        float [] linearizedData = new float[3 * 3];

        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                linearizedData[i + j * 3] = gyro[i][j];

        final Intent broadcast = new Intent(BROADCAST_IMU_GYRO);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_IMU_GYRO, linearizedData);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);

        /*
        if (networkStreaming != null && networkStreaming.isConnected()) {
            double [] data = {(double) value};
            networkStreaming.streamImuAttitude(device, 0, data);
        }
        */
    }

    @Override
    public void onImuMagReceived(BluetoothDevice device, float[][] mag) {
        float [] linearizedData = new float[3 * 3];

        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                linearizedData[i + j * 3] = mag[i][j];

        final Intent broadcast = new Intent(BROADCAST_IMU_MAG);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_IMU_MAG, linearizedData);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onImuAttitudeReceived(BluetoothDevice device, float[] quaternion) {
        final Intent broadcast = new Intent(BROADCAST_IMU_ATTITUDE);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_IMU_ATTITUDE, quaternion);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);

        /*
        if (networkStreaming != null && networkStreaming.isConnected()) {
            double [] data = {(double) value};
            networkStreaming.streamImuAttitude(device, 0, data);
        }
        */
    }

    /******** These callbacks are for managing the EMG logging via RACP ********/

    public void onEmgLogFetchCompleted(BluetoothDevice device) {
        mBinder.log(device, LogContract.Log.Level.DEBUG, "onEmgLogFetchCompleted: " + logFetchStartId);

        if (logFetchStartId.get(device.getAddress()) != null) {
            mBinder.log(device, LogContract.Log.Level.INFO, "Log retrieval complete");
            smartStop(device);
        } else {
            mBinder.log(device, LogContract.Log.Level.WARNING, "onEmgLogFetchCompleted without log fetch intent");
        }
    }

    public void onEmgLogFetchFailed(final BluetoothDevice device, String reason) {
        mBinder.log(device, LogContract.Log.Level.DEBUG, "onEmgLogFetchFailed: " + logFetchStartId);

        if (logFetchStartId.get(device.getAddress()) != null) {
            mBinder.log(device, LogContract.Log.Level.INFO, "Log fetch failed");
            smartStop(device);
        } else {
            mBinder.log(device, LogContract.Log.Level.WARNING, "onEmgLogFetchFailed without log fetch intent");
        }
    }

    /********* End EMG Logging RACP callbacks ********/

    private String getDeviceName(final BluetoothDevice device) {
		String name = device.getName();
		if (TextUtils.isEmpty(name))
			name = getString(R.string.emgimu_default_device_name);
		return name;
	}

}
