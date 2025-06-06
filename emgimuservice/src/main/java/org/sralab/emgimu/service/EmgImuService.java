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
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.collection.SimpleArrayMap;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/*
import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.GooglePlayDriver;
import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.Lifetime;
import com.firebase.jobdispatcher.RetryStrategy;
import com.firebase.jobdispatcher.Trigger;
 */
import com.google.firebase.Timestamp;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.installations.FirebaseInstallations;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.sralab.emgimu.controller.IEmgDecoderProvider;
import org.sralab.emgimu.logging.FirebaseGameLogger;
import org.sralab.emgimu.logging.GamePlayRecord;
import org.sralab.emgimu.unity_bindings.Bridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import no.nordicsemi.android.ble.BleManager;
import no.nordicsemi.android.ble.annotation.DisconnectionReason;
import no.nordicsemi.android.ble.error.GattError;
import no.nordicsemi.android.ble.observer.ConnectionObserver;

public class EmgImuService extends Service implements ConnectionObserver {
	@SuppressWarnings("unused")
	private static final String TAG = "EmgImuService";

    // Broadcast messages for EMG activity
    public static final String INTENT_FETCH_LOG = "org.sralab.INTENT_FETCH_LOG";
    public static final String INTENT_DEVICE_MAC = "org.sralab.INTENT_DEVICE_MAC";

    public static final String SERVICE_PREFERENCES = "org.sralab.emgimu.PREFERENCES";
    public static final String DEVICE_PREFERENCE = "org.sralab.emgimu.DEVICE_LIST";

	private final static String EMGIMU_GROUP_ID = "emgimu_connected_sensors";
	private final static int NOTIFICATION_ID = 1000;
	private final static int OPEN_ACTIVITY_REQ = 0;

	// TODO: optimize for device battery life, handle condition
    // when sensor is not detected (does not influence battery)
    private final static int LOG_FETCH_PERIOD_MIN_S = 5*60;
    private final static int LOG_FETCH_PERIOD_MAX_S = 15*60;

	private final EmgImuBinder mBinder = new EmgImuBinder();

	private FirebaseAuth mAuth;
    private FirebaseAnalytics mFirebaseAnalytics;
    private FirebaseUser mCurrentUser;
    private String mToken;

    private Handler handler;

    Handler getHandler() { return handler; }

    private List<IEmgImuDevicesUpdatedCallback> deviceUpdateCbs = new ArrayList<>();

    /**
	 * This local binder is an interface for the bonded activity to operate with the proximity sensor
	 */
	public class EmgImuBinder extends IEmgImuServiceBinder.Stub {

        /**
         * Returns an unmodifiable list of devices managed by the service.
         * The returned devices do not need to be connected at tha moment. Each of them was however created
         * using {@link #connect(BluetoothDevice)} method so they might have been connected before and disconnected.
         *
         * @return unmodifiable list of devices managed by the service
         */
        public final List<BluetoothDevice> getManagedDevices() {
            return EmgImuService.this.getManagedDevices();
        }

        public void connectDevice(@NotNull final BluetoothDevice device) throws RemoteException {
            EmgImuService.this.connect(device);
        }

        /**
         * Override the parent method to ensure that the device list is updated, even
         * if we are not connected to the device. The onDeviceDisconnected callback only
         * occurs if we are initially connected.
         */
        public void disconnectDevice(@NotNull final BluetoothDevice device) throws RemoteException {
            EmgImuService.this.disconnect(device);
            updateSavedDevices();
        }

        public boolean isConnected(@NotNull final BluetoothDevice device) throws RemoteException {
            return getBleManager(device).isConnected();
        }

        public int getConnectionState(@NotNull final BluetoothDevice device) throws RemoteException {
            return getBleManager(device).getConnectionState();
        }

        // region Register/Unregister Observers Section
        public void registerDevicesObserver(IEmgImuDevicesUpdatedCallback callback) throws RemoteException {
            deviceUpdateCbs.add(callback);
        }

        @Override
        public void unregisterDevicesObserver(IEmgImuDevicesUpdatedCallback callback) throws RemoteException {
            deviceUpdateCbs.remove(callback);
        }

        /**
         * This method is responsible for registering Emg power observer with the respective
         * manager, so the power data can be streamed. In doing so, the method will pass the
         * callback object to the manager.
         * @param regDevice either a null object or a string containing a Mac address of the
         *                  sensor with which the user wants to connect with. If the argument is
         *                  null, then the method will register each sensor with the manager and
         *                  power will be streamed from each said sensor.
         *                  However, if the argument contains a Mac address, then the method will
         *                  register the sensor with that Mac address only.
         * @param callback the callback object that contains the data structure to hold the
         *                 emg power and the method to handle that data. Reference the following:
         *                 - IEmgImuPwrDataCallback.aidl
         *                 - EmgPwrData.aidl
         */
        public void registerEmgPwrObserver(BluetoothDevice regDevice, IEmgImuPwrDataCallback callback) {
            int i = 0;
            for (final BluetoothDevice device : getManagedDevices()) {
                /*
                When the user connects to the sensor from the Config, the regDevice argument
                will be null; therefore, create a manager for each sensor. Otherwise, if
                the user is connecting to the sensor from the game, then Bridge will pass
                a string argument corresponding to the Mac address of the sensor; therefore,
                in that case, connect only to the sensor whose Mac address matches that string.
                 */
                if (regDevice == null) {
                    final EmgImuManager manager = (EmgImuManager) getBleManager(device);
                    manager.registerEmgPwrCallback(callback);
                    Log.d(TAG, "Service.registerEmgPwrObserver (regDevice == null), manager for device[" + i + "] = " + device.toString());
                    i++;
                } else if (device.getAddress().equals(regDevice.getAddress())) {
                    final EmgImuManager manager = (EmgImuManager) getBleManager(device);
                    manager.registerEmgPwrCallback(callback);
                    Log.d(TAG, "Bridge, inside Service.registerEmgPwrObserver, (device.getName().equals(regDevice)) manager for device[" + i + "] = " + device.toString());
                    i++;
                }
            }
        }

        public void unregisterEmgPwrObserver(BluetoothDevice unregDevice, IEmgImuPwrDataCallback callback) {
            Log.d(TAG, "No callbacks remain. Stopping stream.");
            for (final BluetoothDevice device : getManagedDevices()) {
                if(unregDevice == null) {
                    final EmgImuManager manager = (EmgImuManager) getBleManager(device);
                    manager.unregisterEmgPwrCallback(callback);
                } else if(device.toString().equals(unregDevice.getAddress())) {
                    final EmgImuManager manager = (EmgImuManager) getBleManager(device);
                    manager.unregisterEmgPwrCallback(callback);
                }
            }
        }

        public void registerEmgStreamObserver(BluetoothDevice regDevice, IEmgImuStreamDataCallback callback) throws RemoteException {
            for (final BluetoothDevice device : getManagedDevices()) {
                if (regDevice == null) {
                    final EmgImuManager manager = (EmgImuManager) getBleManager(device);
                    manager.registerEmgStreamCallback(callback);
                } else if (device.getAddress().equals(regDevice.getAddress())) {
                    final EmgImuManager manager = (EmgImuManager) getBleManager(device);
                    manager.registerEmgStreamCallback(callback);
                }
            }
        }

        public void unregisterEmgStreamObserver(BluetoothDevice unregDevice, IEmgImuStreamDataCallback callback) {
            Log.d(TAG, "No callbacks remain. Stopping stream.");
            for (final BluetoothDevice device : getManagedDevices()) {
                if(unregDevice == null) {
                    final EmgImuManager manager = (EmgImuManager) getBleManager(device);
                    manager.unregisterEmgStreamCallback(callback);
                } else if(device.toString().equals(unregDevice.getAddress())) {
                    final EmgImuManager manager = (EmgImuManager) getBleManager(device);
                    manager.unregisterEmgStreamCallback(callback);
                }
            }
        }

        @Override
        public void registerImuAccelObserver(BluetoothDevice regDevice, IEmgImuSenseCallback callback) {
            for (final BluetoothDevice device : getManagedDevices()) {
                if (regDevice == null) {
                    final EmgImuManager manager = (EmgImuManager) getBleManager(device);
                    manager.registerImuAccelCallback(callback);
                } else if (device.toString().equals(regDevice.getAddress())) {
                    final EmgImuManager manager = (EmgImuManager) getBleManager(device);
                    manager.registerImuAccelCallback(callback);
                }
            }
        }

        @Override
        public void unregisterImuAccelObserver(BluetoothDevice unregDevice, IEmgImuSenseCallback callback) {
            for (final BluetoothDevice device : getManagedDevices()) {
                if(unregDevice == null) {
                    final EmgImuManager manager = (EmgImuManager) getBleManager(device);
                    manager.unregisterImuAccelCallback(callback);
                } else if(device.toString().equals(unregDevice.getAddress())) {
                    final EmgImuManager manager = (EmgImuManager) getBleManager(device);
                    manager.unregisterImuAccelCallback(callback);
                }
            }
        }

        @Override
        public void registerImuGyroObserver(BluetoothDevice regDevice, IEmgImuSenseCallback callback) {
            for (final BluetoothDevice device : getManagedDevices()) {
                if (regDevice == null) {
                    final EmgImuManager manager = (EmgImuManager) getBleManager(device);
                    manager.registerImuGyroCallback(callback);
                } else if (device.toString().equals(regDevice.getAddress())) {
                    final EmgImuManager manager = (EmgImuManager) getBleManager(device);
                    manager.registerImuGyroCallback(callback);
                }
            }
        }

        @Override
        public void unregisterImuGyroObserver(BluetoothDevice unregDevice, IEmgImuSenseCallback callback) {
            for (final BluetoothDevice device : getManagedDevices()) {
                if(unregDevice == null) {
                    final EmgImuManager manager = (EmgImuManager) getBleManager(device);
                    manager.unregisterImuGyroCallback(callback);
                } else if(device.toString().equals(unregDevice.getAddress())) {
                    final EmgImuManager manager = (EmgImuManager) getBleManager(device);
                    manager.unregisterImuGyroCallback(callback);
                }
            }
        }

        @Override
        public void registerImuMagObserver(BluetoothDevice regDevice, IEmgImuSenseCallback callback) {
            for (final BluetoothDevice device : getManagedDevices()) {
                if (regDevice == null) {
                    final EmgImuManager manager = (EmgImuManager) getBleManager(device);
                    manager.registerImuMagCallback(callback);
                } else if (device.toString().equals(regDevice.getAddress())) {
                    final EmgImuManager manager = (EmgImuManager) getBleManager(device);
                    manager.registerImuMagCallback(callback);
                }
            }
        }

        @Override
        public void unregisterImuMagObserver(BluetoothDevice unregDevice, IEmgImuSenseCallback callback) {
            for (final BluetoothDevice device : getManagedDevices()) {
                if(unregDevice == null) {
                    final EmgImuManager manager = (EmgImuManager) getBleManager(device);
                    manager.unregisterImuMagCallback(callback);
                } else if(device.toString().equals(unregDevice.getAddress())) {
                    final EmgImuManager manager = (EmgImuManager) getBleManager(device);
                    manager.unregisterImuMagCallback(callback);
                }
            }
        }

        @Override
        public void registerImuQuatObserver(BluetoothDevice regDevice, IEmgImuQuatCallback callback) {
            for (final BluetoothDevice device : getManagedDevices()) {
                if (regDevice == null) {
                    final EmgImuManager manager = (EmgImuManager) getBleManager(device);
                    manager.registerImuQuatCallback(callback);
                } else if (device.toString().equals(regDevice.getAddress())) {
                    final EmgImuManager manager = (EmgImuManager) getBleManager(device);
                    manager.registerImuQuatCallback(callback);
                }
            }
        }

        @Override
        public void unregisterImuQuatObserver(BluetoothDevice unregDevice, IEmgImuQuatCallback callback) {
            for (final BluetoothDevice device : getManagedDevices()) {
                if(unregDevice == null) {
                    final EmgImuManager manager = (EmgImuManager) getBleManager(device);
                    manager.unregisterImuQuatCallback(callback);
                } else if(device.toString().equals(unregDevice.getAddress())) {
                    final EmgImuManager manager = (EmgImuManager) getBleManager(device);
                    manager.unregisterImuQuatCallback(callback);
                }
            }
        }

        @Override
        public void registerBatObserver(BluetoothDevice regDevice, IEmgImuBatCallback callback) throws RemoteException {
            for (final BluetoothDevice device : getManagedDevices()) {
                if (regDevice == null) {
                    final EmgImuManager manager = (EmgImuManager) getBleManager(device);
                    manager.registerBatCallback(callback);
                } else if (device.toString().equals(regDevice.getAddress())) {
                    final EmgImuManager manager = (EmgImuManager) getBleManager(device);
                    manager.registerBatCallback(callback);
                }
            }
        }

        @Override
        public void unregisterBatObserver(BluetoothDevice unregDevice, IEmgImuBatCallback callback) throws RemoteException {
            for (final BluetoothDevice device : getManagedDevices()) {
                if(unregDevice == null) {
                    final EmgImuManager manager = (EmgImuManager) getBleManager(device);
                    manager.unregisterBatCallback(callback);
                } else if(device.toString().equals(unregDevice.getAddress())) {
                    final EmgImuManager manager = (EmgImuManager) getBleManager(device);
                    manager.unregisterBatCallback(callback);
                }
            }
        }
        // endregion

        @Override
        public void storeGameplayRecord(String name, long startTime, String details) throws RemoteException {
            GamePlayRecord record = new GamePlayRecord();
            record.setName(name);
            record.setDetails(details);
            record.setLogReference(getLoggingReferences());
            record.setStartTime(startTime);
            record.setStopTime(new Date().getTime());
            Log.d(TAG, "Storing game play record");
            FirebaseGameLogger logger = new FirebaseGameLogger(mBinder);
            logger.writeRecord(record);
        }

        public LiveData<Integer> getConnectionLiveState(@NotNull final BluetoothDevice device) {
            return getBleManager(device).getConnectionLiveState();
        }

        public LiveData<List<BluetoothDevice>> getLiveDevices() {
            return liveDevices;
        }

        public LiveData<Double> getBattery(final BluetoothDevice device) {
            final EmgImuManager manager = (EmgImuManager) getBleManager(device);
            return manager.getBatteryVoltage();
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

        public void updateSavedDevices() {
            Log.d(TAG, "updateSavedDevices called on binder");
            serviceUpdateSavedDevices();
            //configureLoggingSavedDevices();
        }

        public String getUser() {
            if (mCurrentUser != null)
                return mCurrentUser.getUid();
            return "";
        }

        public String getAuthToken() {
            return mToken;
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

	protected IBinder getBinder() {
		return mBinder;
	}

	protected EmgImuManager initializeManager() {
        EmgImuManager manager = new EmgImuManager(this);
		return manager;
	}

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");

        handler = new Handler(Looper.getMainLooper());

        // Initialize the map of BLE managers
        bleManagers = new HashMap<>();
        managedDevices = new ArrayList<>();

        // Register broadcast receivers
        registerReceiver(bluetoothStateBroadcastReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

        // Call onBluetoothEnabled if Bluetooth enabled
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter.isEnabled()) {
            onBluetoothEnabled();
        }

        FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
        crashlytics.setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG);

        Log.d(TAG, "onServiceCreated");

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
                            // It can happen that either one is set first
                            if (mToken != null && mCurrentUser != null)
                                storeToken();
                        } else {
                            // If sign in fails, display a message to the user.
                            Log.d(TAG, "signInAnonymously:failure" + task.getException());
                        }
                    });
        } else {
            Log.d(TAG, "User ID: " + mCurrentUser.getUid());
        }
		
        try {
            FirebaseInstallations.getInstance().getId().addOnSuccessListener(mToken -> {
                Log.d(TAG, "Received token: " + mToken);

                // It can happen that either one is set first
                if (mToken != null && mCurrentUser != null)
                    storeToken();

            });
        } catch (IllegalArgumentException argumentException) {
            Log.d(TAG, "No valid connection to Firebase!");
        } catch(Exception exception)
        {
            Log.d(TAG, "Unrecognized error when attempting to work with Firebase: " + exception.getMessage());
        }

        // Obtain the FirebaseAnalytics instance.
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, "EMG_IMU_SERVICE_CREATED");
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);
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

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {

        ensureBLESupported();
        if (!isBLEEnabled()) {
            showBLEDialog();
        }

        if (intent == null) {
            Log.d(TAG, "onStartCommand called without intent. flags: " + flags + " startId " + startId);
        } else if (intent.getExtras() != null) {
            Log.d(TAG, "onStartCommand: " + intent.toString() + " extras: " + intent.getExtras().toString() + " flags: " + flags + " startId" + startId);
        } else {
            Log.d(TAG, "onStartCommand: "  + intent.toString() + " flags: " + flags + " startId" + startId);
        }

        // See if there is an intent indicating the service was started unbound to
        // acquire a log. Only start this when not already connected to the device.
        if (intent != null && intent.getBooleanExtra(EmgImuService.INTENT_FETCH_LOG, false)) {

            createSummaryNotification();

            String device_mac = intent.getStringExtra(INTENT_DEVICE_MAC);

            Log.d(TAG, "onStartCommand due to requesting logs from " + device_mac);

            // Detect if bluetooth is enabled and if not don't attempt to get log
            final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (!bluetoothAdapter.isEnabled()) {
                Log.e(TAG, "Unable to download log as bluetooth is disabled");
                stopSelf(startId);
                return START_NOT_STICKY;
            }

            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(device_mac);

            List<BluetoothDevice> devices = getManagedDevices();
            for (BluetoothDevice d : devices) {
                if (d.getAddress().equals(device.getAddress())) {
                    Log.d(TAG, "Already managing " + device_mac + " so will skip fetching log");
                    return START_NOT_STICKY;
                }
            }

            try {
                mBinder.connectDevice(device); //,  getLogger(device));
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        return START_STICKY;
    }

    private void ensureBLESupported() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, org.sralab.emgimu.common.R.string.no_ble, Toast.LENGTH_LONG).show();
        }
    }

    protected boolean isBLEEnabled() {
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothAdapter adapter = bluetoothManager.getAdapter();
        return adapter != null && adapter.isEnabled();
    }

    protected void showBLEDialog() {
        final Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        enableIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(enableIntent);
    }

    @Override
    public IBinder onBind(final Intent intent) {

        if (intent != null)
            Log.d(TAG, "onBind: " + intent.toString());
        else
            Log.d(TAG, "onBind with no intent");

        // We have been starting by a service. Restore list of devices and attempt to connect.
        managedDevices = new ArrayList<>(getSavedDevices());
        onDeviceListUpdated();

        Log.d(TAG, "About to connect to devices: " + managedDevices);
        for (final BluetoothDevice d : managedDevices) {
            EmgImuManager manager = initializeManager();
            manager.setConnectionObserver(EmgImuService.this);
            manager.connect(d).retry(2, 500).useAutoConnect(true)
                    .fail((device, status) -> Log.e(TAG, "Unable to connect to device: " + device + " status: " + status) )
                    .enqueue();
            bleManagers.put(d, manager);
        }

        // TODO: mBinded = true;
        return getBinder();
    }

	public void onDestroy() {
        Log.d(TAG, "onServicesStopped");

		cancelNotifications();

        // Unregister broadcast receivers
        unregisterReceiver(bluetoothStateBroadcastReceiver);

        // The managers map may not be empty if the service was killed by the system
        for (final BleManager manager : bleManagers.values()) {
            // Service is being destroyed, no need to disconnect manually.
            manager.close();
        }

        bleManagers.clear();
        managedDevices.clear();
        bleManagers = null;
        managedDevices = null;
        handler = null;

        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, "EMG_IMU_SERVICE_STOPPED");
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);
    }


    @Override
    public final void onRebind(final Intent intent) {
        super.onRebind(intent);
		// When the activity rebinds to the service, remove the notification
		//cancelNotifications();
	}

	@Override
    public final boolean onUnbind(final Intent intent) {
        super.onUnbind(intent);
        Log.i(TAG, "onUnbind");

        stopSelf();

        // We want the onRebind method be called if anything else binds to it again
        return true;
    }

    @Override
    public void onDeviceConnecting(@NonNull BluetoothDevice device) {
        Log.d(TAG, "onDeviceConnecting: " + device);
    }

    @Override
	public void onDeviceConnected(final BluetoothDevice device) {
        Log.d(TAG, "onDeviceConnected: " + device);
        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, "EMG_IMU_SERVICE_CONNECTED");
        bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, device.getName());
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);
    }

    @Override
    public void onDeviceFailedToConnect(@NonNull BluetoothDevice device, int reason) {
        Log.d(TAG, "onDeviceFailedToConnect: " + device);
    }

    @Override
    public void onDeviceDisconnected(@NonNull final BluetoothDevice device, @DisconnectionReason final int reason) {
        Log.d(TAG, "onDeviceDisconnected: " + device);
	}

    @Override
    public void onDeviceReady(BluetoothDevice device) {
        Log.d(TAG, "onDeviceReady: " + device);
    }

    @Override
    public void onDeviceDisconnecting(@NonNull BluetoothDevice device) {
        Log.d(TAG, "onDeviceDisconnecting: " + device);
    }

    /** Connections and device management **/
    private List<BluetoothDevice> managedDevices = new ArrayList<>();
    private MutableLiveData<List<BluetoothDevice>> liveDevices = new MutableLiveData<>(managedDevices);
    private Map<BluetoothDevice, EmgImuManager> bleManagers = new HashMap<>();

    public void connect(@NotNull final BluetoothDevice device) {

        // If a device is in managed devices, no need to add to list
        if (managedDevices.contains(device))
            return;
        managedDevices.add(device);

        Log.d(TAG, "Service connect called");
        EmgImuManager manager = bleManagers.get(device);
        if (manager != null) {
            manager.connect(device).enqueue();
        } else {
            bleManagers.put(device, manager = initializeManager());
            manager.setConnectionObserver(EmgImuService.this);
            manager.connect(device)
                   .fail((device1, status) -> Log.e(TAG, "Unable to connect to device") )
                   .enqueue();
        }

        serviceUpdateSavedDevices();
        onDeviceListUpdated();
    }

    //! Remove device from list to use (and disconnect)
    public void disconnect(@NotNull final BluetoothDevice device) {
        final BleManager manager = bleManagers.get(device);
        if (manager != null && manager.isConnected()) {
            manager.disconnect().enqueue();
        }
        managedDevices.remove(device);
        serviceUpdateSavedDevices();
        onDeviceListUpdated();
    }

    void onDeviceListUpdated() {
        liveDevices.postValue(managedDevices);
        try {
            for (IEmgImuDevicesUpdatedCallback cb : deviceUpdateCbs)
                cb.onDeviceListUpdated();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    protected List<BluetoothDevice> getManagedDevices() {
        return Collections.unmodifiableList(managedDevices);
    }

    protected EmgImuManager getBleManager(final BluetoothDevice device) {
        return bleManagers.get(device);
    }

    private void serviceUpdateSavedDevices() {
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

        Log.d(TAG, "Saved devices: " + devices);
        return Collections.unmodifiableList(devices);
    }

    /** Core service components **/
    private final BroadcastReceiver bluetoothStateBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);
            final int previousState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, BluetoothAdapter.STATE_OFF);

            switch (state) {
                case BluetoothAdapter.STATE_ON:
                    // On older phones (tested on Nexus 4 with Android 5.0.1) the Bluetooth requires some time
                    // after it has been enabled before some operations can start. Starting the GATT server here
                    // without a delay is very likely to cause a DeadObjectException from BluetoothManager#openGattServer(...).
                    getHandler().postDelayed(() -> onBluetoothEnabled(), 600);
                    break;
                case BluetoothAdapter.STATE_TURNING_OFF:
                case BluetoothAdapter.STATE_OFF:
                    if (previousState != BluetoothAdapter.STATE_TURNING_OFF && previousState != BluetoothAdapter.STATE_OFF)
                        onBluetoothDisabled();
                    break;
            }
        }
    };

    /**
     * Method called when Bluetooth Adapter has been disabled.
     */
    protected void onBluetoothDisabled() {
        // do nothing, BleManagers have their own Bluetooth State broadcast received and will close themselves
    }

    /**
     * This method is called when Bluetooth Adapter has been enabled. It is also called
     * after the service was created if Bluetooth Adapter was enabled at that moment.
     * This method could initialize all Bluetooth related features, for example open the GATT server.
     * Make sure you call <code>super.onBluetoothEnabled()</code> at this methods reconnects to
     * devices that were connected before the Bluetooth was turned off.
     */
    protected void onBluetoothEnabled() {
        for (final BluetoothDevice device : managedDevices) {
            final BleManager manager = bleManagers.get(device);
            if (!manager.isConnected())
                manager.connect(device).enqueue();
        }
    }

    @Override
    public void onTaskRemoved(final Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        // This method is called when user removed the app from Recents.
        // By default, the service will be killed and recreated immediately after that.
        // However, all managed devices will be lost and devices will be disconnected.
        stopSelf();
    }

    /** Notification information **/
    void showToast(final String message) {
        getHandler().post(() -> Toast.makeText(EmgImuService.this, message, Toast.LENGTH_SHORT).show());
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
		if (managedDevices.isEmpty()) {
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

			final int numberOfConnectedDevices = managedDevices.size();
			if (numberOfConnectedDevices == 1) {
				final String name = getDeviceName(managedDevices.get(0));
				text.append(getString(R.string.emgimu_notification_summary_text_name, name));
			} else {
				text.append(getString(R.string.emgimu_notification_summary_text_number, numberOfConnectedDevices));
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

		/*
		// Add DISCONNECT action
		final Intent disconnect = new Intent(ACTION_DISCONNECT);
		disconnect.putExtra(EXTRA_DEVICE, device);
		final PendingIntent disconnectAction = PendingIntent.getBroadcast(this, DISCONNECT_REQ + device.hashCode(), disconnect, PendingIntent.FLAG_UPDATE_CURRENT);
		builder.addAction(new NotificationCompat.Action(R.drawable.ic_action_bluetooth, getString(R.string.emgimu_action_disconnect), disconnectAction));
		builder.setSortKey(getDeviceName(device) + device.getAddress()); // This will keep the same order of notification even after an action was clicked on one of them
		*/

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

    /********* End EMG Logging RACP callbacks ********/

    private String getDeviceName(final BluetoothDevice device) {
		String name = device.getName();
		if (TextUtils.isEmpty(name))
			name = getString(R.string.emgimu_default_device_name);
		return name;
	}
}
