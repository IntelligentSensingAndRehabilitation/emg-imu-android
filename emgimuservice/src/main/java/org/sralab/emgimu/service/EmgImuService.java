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
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.NotificationCompat;

import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Date;
import java.util.List;

import no.nordicsemi.android.log.ILogSession;
import no.nordicsemi.android.log.LogContract;
import no.nordicsemi.android.log.Logger;
import no.nordicsemi.android.nrftoolbox.profile.BleManager;
import no.nordicsemi.android.nrftoolbox.profile.multiconnect.BleMulticonnectProfileService;

import org.json.JSONArray;
import org.json.JSONException;
import org.sralab.emgimu.logging.EmgLogManager;

public class EmgImuService extends BleMulticonnectProfileService implements EmgImuManagerCallbacks, EmgImuServerManagerCallbacks {
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

    private static final String SERVICE_PREFERENCES = "org.sralab.emgimu.PREFERENCES";
	private static final String DEVICE_PREFERENCE = "org.sralab.emgimu.DEVICE_LIST";

	private final static String EMGIMU_GROUP_ID = "emgimu_connected_sensors";
	private final static int NOTIFICATION_ID = 1000;
	private final static int OPEN_ACTIVITY_REQ = 0;
	private final static int DISCONNECT_REQ = 1;

	private final EmgImuBinder mBinder = new EmgImuBinder();
	private EmgImuServerManager mServerManager;

	private int mAttempt;
	private final static int MAX_ATTEMPTS = 1;

	private FirebaseAuth mAuth;
    private FirebaseAnalytics mFirebaseAnalytics;

	/**
	 * This local binder is an interface for the bonded activity to operate with the proximity sensor
	 */
	public class EmgImuBinder extends LocalBinder {

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
        public int [] getEmgBuffValue(final BluetoothDevice device) {
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

        public int getLoggerProfileTitle() {
            //return R.string.emgimu_feature_title;
            return 0; // Use the line above to enable logging, but this slows down application
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

	/**
	 * This broadcast receiver listens for {@link #ACTION_FIND} or {@link #ACTION_SILENT} that may be fired by pressing Find me action button on the notification.
	 */
	private final BroadcastReceiver mToggleAlarmActionBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			final BluetoothDevice device = intent.getParcelableExtra(EXTRA_DEVICE);
			switch (intent.getAction()) {
				case ACTION_FIND:
					mBinder.log(device, LogContract.Log.Level.INFO, "[Notification] FIND action pressed");
					break;
				case ACTION_SILENT:
					mBinder.log(device, LogContract.Log.Level.INFO, "[Notification] SILENT action pressed");
					break;
			}
			createNotificationForConnectedDevice(device);
		}
	};

	@Override
	protected void onServiceCreated() {
		mServerManager = new EmgImuServerManager(this);
		mServerManager.setLogger(mBinder);

        loadAndConnectSavedDevices();

		registerReceiver(mDisconnectActionBroadcastReceiver, new IntentFilter(ACTION_DISCONNECT));
		final IntentFilter filter = new IntentFilter();
		filter.addAction(ACTION_FIND);
		filter.addAction(ACTION_SILENT);
		registerReceiver(mToggleAlarmActionBroadcastReceiver, filter);

        mAuth = FirebaseAuth.getInstance();

        // Check if user is signed in (non-null) and update UI accordingly.
        FirebaseUser currentUser = mAuth.getCurrentUser();
        Log.d(TAG, "User ID: " + currentUser);
        if (currentUser == null) {
            mAuth.signInAnonymously()
                    .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                        @Override
                        public void onComplete(@NonNull Task<AuthResult> task) {
                            if (task.isSuccessful()) {
                                // Sign in success, update UI with the signed-in user's information
                                FirebaseUser user = mAuth.getCurrentUser();

                                Log.d(TAG, "signInAnonymously:success. UID:" + user.getUid());
                                //updateUI(user);
                            } else {
                                // If sign in fails, display a message to the user.
                                Log.w(TAG, "signInAnonymously:failure", task.getException());
                                //Toast.makeText(AnonymousAuthActivity.this, "Authentication failed.",
                                //        Toast.LENGTH_SHORT).show();
                                //updateUI(null);
                            }
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

        // Test write
        EmgLogManager l = new EmgLogManager();
        long t0 = new Date().getTime();
        double Fs = 1000.0 / 1.0; // Sampling rate of sensor (1 Hz logs)
        for (int i = 0 ; i < (60 * 60 * 3 + 5); i++) {
            // Simulate three hours of data
            l.addSample(t0 + (long) Fs * i, (double) i);
        }
	}

	@Override
	public void onServiceStopped() {
		cancelNotifications();

		// Close the GATT server. If it hasn't been opened this method does nothing
		mServerManager.closeGattServer();

		unregisterReceiver(mDisconnectActionBroadcastReceiver);
		unregisterReceiver(mToggleAlarmActionBroadcastReceiver);

		super.onServiceStopped();

        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, "EMG_IMU_SERVICE_STOPPED");
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);
    }

	@Override
	protected void onBluetoothEnabled() {
		mAttempt = 0;
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				final Runnable that = this;
				// Start the GATT Server only if Bluetooth is enabled
				mServerManager.openGattServer(EmgImuService.this, new EmgImuServerManager.OnServerOpenCallback() {
					@Override
					public void onGattServerOpen() {
						// We are now ready to reconnect devices
						EmgImuService.super.onBluetoothEnabled();
					}

					@Override
					public void onGattServerFailed(final int error) {
						mServerManager.closeGattServer();

						if (mAttempt < MAX_ATTEMPTS) {
							mAttempt++;
							getHandler().postDelayed(that, 2000);
						} else {
							showToast(getString(R.string.emgimu_server_error, error));
							// GATT server failed to start, but we may connect as a client
							EmgImuService.super.onBluetoothEnabled();
						}
					}
				});
			}
		});
	}

	@Override
	protected void onBluetoothDisabled() {
		super.onBluetoothDisabled();
		// Close the GATT server
		mServerManager.closeGattServer();
	}

	@Override
	protected void onRebind() {
		// When the activity rebinds to the service, remove the notification
		cancelNotifications();
	}

	@Override
	public void onUnbind() {
		createBackgroundNotification();
	}

	@Override
	public void onDeviceConnected(final BluetoothDevice device) {
		super.onDeviceConnected(device);

		if (!mBinded) {
			createBackgroundNotification();
		}

        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, "EMG_IMU_SERVICE_CONNECTED");
        bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, device.getName());
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);

        // Save this list of devices for later
        updateSavedDevices();
    }

	@Override
	public void onServicesDiscovered(final BluetoothDevice device, final boolean optionalServicesFound) {
		super.onServicesDiscovered(device, optionalServicesFound);
		mServerManager.openConnection(device);
	}

	@Override
	public void onLinklossOccur(final BluetoothDevice device) {
		mServerManager.cancelConnection(device);
		super.onLinklossOccur(device);

		if (!mBinded) {
			createBackgroundNotification();
			if (BluetoothAdapter.getDefaultAdapter().isEnabled()) {
				// Do nothing
			} else
				cancelNotification(device);
		}
	}

	@Override
	public void onDeviceDisconnected(final BluetoothDevice device) {
		mServerManager.cancelConnection(device);
		super.onDeviceDisconnected(device);

		if (!mBinded) {
			cancelNotification(device);
			createBackgroundNotification();
		}

		// Save this list of devices for later
        updateSavedDevices();
	}

	private void updateSavedDevices() {
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
        editor.putString(DEVICE_PREFERENCE, names.toString());
        editor.commit();
	}

	private ILogSession getLogger(final BluetoothDevice device) {
        final int titleId = mBinder.getLoggerProfileTitle();
        ILogSession logSession = null;
        if (titleId > 0) {
            logSession = Logger.newSession(getApplicationContext(), getString(titleId), device.getAddress(), device.getName());
        }
        return logSession;
	}

	private void loadAndConnectSavedDevices() {
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

        try {
            JSONArray names = new JSONArray(deviceList_s);
            for (int i = 0; i < names.length(); i++) {
                String device_mac = names.getString(i);
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(device_mac);
                mBinder.connect(device,  getLogger(device));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
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
		builder.setContentTitle(getString(R.string.app_name));

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
		final Intent parentIntent = new Intent(this, EmgImuService.class);
		parentIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		final Intent targetIntent = new Intent(this, EmgImuService.class);

		// Both activities above have launchMode="singleTask" in the AndroidManifest.xml file, so if the task is already running, it will be resumed
		final PendingIntent pendingIntent = PendingIntent.getActivities(this, OPEN_ACTIVITY_REQ, new Intent[] { parentIntent, targetIntent }, PendingIntent.FLAG_UPDATE_CURRENT);

		final NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
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

    public void onEmgBuffReceived(final BluetoothDevice device, int [] value)
    {
        final Intent broadcast = new Intent(BROADCAST_EMG_BUFF);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_EMG_BUFF, value);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    public void onEmgClick(final BluetoothDevice device) {
        final Intent broadcast = new Intent(BROADCAST_EMG_CLICK);
        broadcast.putExtra(EXTRA_DEVICE, device);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

	private String getDeviceName(final BluetoothDevice device) {
		String name = device.getName();
		if (TextUtils.isEmpty(name))
			name = getString(R.string.emgimu_default_device_name);
		return name;
	}
}
