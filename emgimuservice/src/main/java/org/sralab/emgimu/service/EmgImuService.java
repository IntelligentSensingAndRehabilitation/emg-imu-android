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
import android.support.annotation.StringRes;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.app.NotificationCompat;

import android.text.TextUtils;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.GooglePlayDriver;
import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.Lifetime;
import com.firebase.jobdispatcher.RetryStrategy;
import com.firebase.jobdispatcher.Trigger;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

public class EmgImuService extends BleMulticonnectProfileService implements EmgImuManagerCallbacks {
	@SuppressWarnings("unused")
	private static final String TAG = "EmgImuService";

	private final static String ACTION_DISCONNECT = "no.nordicsemi.android.nrftoolbox.proximity.ACTION_DISCONNECT";
	private final static String ACTION_FIND = "no.nordicsemi.android.nrftoolbox.proximity.ACTION_FIND";
	private final static String ACTION_SILENT = "no.nordicsemi.android.nrftoolbox.proximity.ACTION_SILENT";

    public final static String EXTRA_DEVICE = "org.sralab.emgimu.EXTRA_DEVICE";

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

    public static final String SERVICE_PREFERENCES = "org.sralab.emgimu.PREFERENCES";
    public static final String DEVICE_PREFERENCE = "org.sralab.emgimu.DEVICE_LIST";
    public static final String MIN_THRESHOLD_PREFERENCE = "org.sralab.emgimu.MIN_THRESHOLD_PREFERENCE";
    public static final String MAX_THRESHOLD_PREFERENCE = "org.sralab.emgimu.MAX_THRESHOLD_PREFERENCE";

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

    private ILogSession mLogSession;
    private ServiceLogger mServiceLogger = new ServiceLogger(TAG, this, mLogSession);

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
            for (BluetoothDevice device : devices)
                if (!mBinder.isConnected(device))
                    return false;

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

        //! Set threshold
        public void setThreshold(final BluetoothDevice device, double minThreshold, double maxThreshold) {
            final EmgImuManager manager = (EmgImuManager) getBleManager(device);
            manager.setThreshold(minThreshold, maxThreshold);
        }

        public double getMinThreshold(final BluetoothDevice device) {
            final EmgImuManager manager = (EmgImuManager) getBleManager(device);
            return manager.getMinThreshold();
        }

        public double getMaxThreshold(final BluetoothDevice device) {
            final EmgImuManager manager = (EmgImuManager) getBleManager(device);
            return manager.getMaxThreshold();
        }

        //! Get battery
        public double getBattery(final BluetoothDevice device) {
            final EmgImuManager manager = (EmgImuManager) getBleManager(device);
            return manager.getBatteryVoltage();
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
     * parent onCreate prior to startign bluetooth
     */
	protected void onServiceCreated() {

        Fabric.with(this, new Crashlytics());

        final int titleId = mBinder.getLoggerProfileTitle();
        if (titleId > 0) {
            mLogSession = Logger.newSession(getApplicationContext(), "Service", "Service", "Service");
            mServiceLogger = new ServiceLogger(TAG, this, mLogSession);
        }

        mServiceLogger.d("onServiceCreated");

	    registerReceiver(mDisconnectActionBroadcastReceiver, new IntentFilter(ACTION_DISCONNECT));

        mAuth = FirebaseAuth.getInstance();

        // Check if user is signed in (non-null) and update UI accordingly.
        FirebaseUser currentUser = mAuth.getCurrentUser();
        mServiceLogger.d("User ID: " + currentUser);
        if (currentUser == null) {
            Log.d(TAG, "Attempting to log in to firebase");
            mAuth.signInAnonymously()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            FirebaseUser user = mAuth.getCurrentUser();

                            Log.d(TAG, "signInAnonymously:success. UID:" + user.getUid());
                            mServiceLogger.d("signInAnonymously:success. UID:" + user.getUid());
                            //updateUI(user);
                        } else {
                            // If sign in fails, display a message to the user.
                            mServiceLogger.w("signInAnonymously:failure" + task.getException());
                            Log.d(TAG, "signInAnonymously:failure" + task.getException());
                            //Toast.makeText(AnonymousAuthActivity.this, "Authentication failed.",
                            //        Toast.LENGTH_SHORT).show();
                            //updateUI(null);
                        }
                    });
        } else {
            Log.d(TAG, "Prior logged in user: " + currentUser.getUid());
        }

        // Obtain the FirebaseAnalytics instance.
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, "EMG_IMU_SERVICE_CREATED");
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);
	}

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        mBinder.log(LogContract.Log.Level.INFO, "onStartCommand");
	    // parent method is stub, so no need to replicate

        mServiceLogger.d("onStartCommand: " + intent + " " + flags + " " + startId);

        return START_STICKY;
    }

    @Override
    public void onError(BluetoothDevice device, String message, int errorCode) {
        super.onError(device, message, errorCode);

        mBinder.log(device, LogContract.Log.Level.WARNING, "onError: " + message +
                " errorCode: " + errorCode + "(" + GattError.parseConnectionError(errorCode) + ")");
    }


    private boolean mBound = false;
    private final Object mLock = new Object();

    @Override
    public IBinder onBind(final Intent intent) {
        mServiceLogger.d("onBind: " + intent);

        synchronized (mLock) {
            mBound = true;
        }

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
    }

    @Override
    public final void onRebind(final Intent intent) {
        mServiceLogger.d("onRebind: " + intent);

        synchronized (mLock) {
            mBound = true;
        }

        super.onRebind(intent);

    }

    @Override
	protected void onRebind() {

        synchronized (mLock) {
            mBound = true;
        }

		// When the activity rebinds to the service, remove the notification
		cancelNotifications();
	}

	@Override
	public void onUnbind() {
        mBinder.log(LogContract.Log.Level.INFO, "onUnbind");

        synchronized (mLock) {
            mBound = false;
        }

        getHandler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mBound == false) {
                    mServiceLogger.i("Timeout occurred and service still not bound. Shutting down.");
                    stopSelf();
                }
            }
        }, 5000);

        createBackgroundNotification();
	}

	@Override
	public void onDeviceConnected(final BluetoothDevice device) {
        mBinder.log(device, LogContract.Log.Level.INFO, "onDeviceConnected");

		super.onDeviceConnected(device);

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
                        .setTrigger(Trigger.executionWindow(LOG_FETCH_PERIOD_MIN_S,LOG_FETCH_PERIOD_MAX_S))
                        .setLifetime(Lifetime.FOREVER)                       // run after boot
                        .setRecurring(true)                                  // tasks reoccurs
                        .setRetryStrategy(RetryStrategy.DEFAULT_LINEAR) // default strategy
                        .setExtras(jobInfo)                                  // store the mac address
                        .setReplaceCurrent(true)
                        .build();
                dispatcher.mustSchedule(myJob);
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
		final List<BluetoothDevice> connectedDevices = getConnectedDevices();
		for (final BluetoothDevice device : connectedDevices) {
			createNotificationForConnectedDevice(device);
		}
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
            channelId = "org.sralab.emgimu.service";
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

		final List<BluetoothDevice> managedDevices = getManagedDevices();
		for (final BluetoothDevice device : managedDevices) {
			nm.cancel(device.getAddress(), NOTIFICATION_ID);
		}
	}

	/**
	 * Cancels the existing notification for given device. If there is no active notification this method does nothing
	 */
	private void cancelNotification(final BluetoothDevice device) {
		final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		nm.cancel(device.getAddress(), NOTIFICATION_ID);
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
    }

    public void onEmgClick(final BluetoothDevice device) {
        final Intent broadcast = new Intent(BROADCAST_EMG_CLICK);
        broadcast.putExtra(EXTRA_DEVICE, device);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    /******** These callbacks are for managing the EMG logging via RACP ********/
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

    /********* End EMG Logging RACP callbacks ********/

    private String getDeviceName(final BluetoothDevice device) {
		String name = device.getName();
		if (TextUtils.isEmpty(name))
			name = getString(R.string.emgimu_default_device_name);
		return name;
	}
}
