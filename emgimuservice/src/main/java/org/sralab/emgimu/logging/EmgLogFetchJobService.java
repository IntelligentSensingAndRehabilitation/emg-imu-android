package org.sralab.emgimu.logging;

/**
 * Copyright R. James Cotton. 2018
 */

import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.sralab.emgimu.service.EmgImuManager;
import org.sralab.emgimu.service.EmgImuManagerCallbacks;
import org.sralab.emgimu.service.EmgImuService;
import org.sralab.emgimu.service.EmgLogRecord;
import org.sralab.emgimu.service.ServiceLogger;

import no.nordicsemi.android.ble.error.GattError;
import no.nordicsemi.android.log.ILogSession;
import no.nordicsemi.android.log.Logger;

public class EmgLogFetchJobService extends JobService implements EmgImuManagerCallbacks
{

    private static String TAG = EmgLogFetchJobService.class.getSimpleName();
    public static String JOB_TAG = "emg-log-fetch";

    private ILogSession mLogSession;
    private final ServiceLogger mServiceLogger = new ServiceLogger(TAG, this, mLogSession);

    private FirebaseAuth mAuth;
    private FirebaseAnalytics mFirebaseAnalytics;

    private EmgImuManager mManager;

    private JobParameters mJob;
    @Override
    public boolean onStartJob(JobParameters job) {

        /******** Check service isn't running. If it is, then the user may be connected *******/

        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (EmgImuService.class.getName().equals(service.service.getClassName())) {
                Log.d(TAG, "Skipping log fetch. Service already bound.");
                return false; // False to indicate nothing pending
            }
        }

        /**** Store information required to get log ****/
        String device_mac = job.getExtras().getString("device_mac");
        mLogSession = Logger.newSession(getApplicationContext(), device_mac, "FetchLog");
        mJob = job;
        mServiceLogger.i("onStartJob");

        /******** Log into Firebase ********/
        mAuth = FirebaseAuth.getInstance();

        // Check if user is signed in (non-null) and update UI accordingly.
        FirebaseUser currentUser = mAuth.getCurrentUser();
        mServiceLogger.d("User ID: " + currentUser);
        if (currentUser == null) {
            mAuth.signInAnonymously()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            FirebaseUser user = mAuth.getCurrentUser();
                            mServiceLogger.d("signInAnonymously:success. UID:" + user.getUid());

                            attemptConnection();
                        } else {
                            mServiceLogger.w("signInAnonymously:failure" + task.getException());

                            // If user unknown, then no need to collect log
                            jobFinished(mJob, true);
                        }
                    });
        } else {
            attemptConnection();
        }

        // Obtain the FirebaseAnalytics instance.
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, "EMG_IMU_FETCH_STARTED");
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);

        return true;
    }

    private void attemptConnection() {
        String device_mac = mJob.getExtras().getString("device_mac");

        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            mServiceLogger.e("Unable to fetch logs as adapter is disabled");
            jobFinished(mJob, true);
        } else {

            mServiceLogger.d("Attempting to connect and fetch logs");
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(device_mac);

            mManager = new EmgImuManager(this);
            mManager.setGattCallbacks(this);
            mManager.setLogger(mLogSession);
            mManager.connect(device);
        }
    }

    private void myJobFinished (JobParameters params, boolean needsReschedule) {

        mServiceLogger.d("myJobFinished");

        if (mManager != null) {
            mManager.disconnect();
            mManager.close();
            mManager = null;
        }

        jobFinished(params, needsReschedule);
    }

    @Override
    public boolean onStopJob(JobParameters job) {
        mServiceLogger.d("onStopJob()");

        if (mManager != null) {
            mManager.disconnect();
            mManager.close();
            mManager = null;
        }

        return false;
    }

    @Override
    public void onEmgRawReceived(BluetoothDevice device, int value) {

    }

    @Override
    public void onEmgBuffReceived(BluetoothDevice device, int[] value) {

    }

    @Override
    public void onEmgPwrReceived(BluetoothDevice device, int value) {

    }

    @Override
    public void onEmgClick(BluetoothDevice device) {

    }

    @Override
    public void onEmgLogRecordReceived(BluetoothDevice device, EmgLogRecord record) {

    }

    @Override
    public void onOperationStarted(BluetoothDevice device) {

    }

    @Override
    public void onOperationCompleted(BluetoothDevice device) {
        myJobFinished(mJob, false);
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

    @Override
    public void onDeviceConnecting(BluetoothDevice device) {
        mServiceLogger.i("onDeviceConnecting");
    }

    @Override
    public void onDeviceConnected(BluetoothDevice device) {
        mServiceLogger.i("onDeviceConnected");
    }

    @Override
    public void onDeviceDisconnecting(BluetoothDevice device) {
        mServiceLogger.i("onDeviceDisconnecting");
    }

    @Override
    public void onDeviceDisconnected(BluetoothDevice device) {
        mServiceLogger.d("onDeviceDisconnected");
    }

    @Override
    public void onLinklossOccur(BluetoothDevice device) {
        // Note: with shouldAutoConnect true then it will attempt to reconnect
        // but this typically won't succeed since it is probably out of range
        // so just try again later.
        mServiceLogger.e("onLinklossOccurr: Stopping job.");
        myJobFinished(mJob, true);
    }

    @Override
    public void onServicesDiscovered(BluetoothDevice device, boolean optionalServicesFound) {

    }

    @Override
    public void onDeviceReady(BluetoothDevice device) {
        mServiceLogger.d("Device connected and ready. Fetching log.");
        mManager.getAllRecords();
    }

    @Override
    public boolean shouldEnableBatteryLevelNotifications(BluetoothDevice device) {
        return false;
    }

    @Override
    public void onBatteryValueReceived(BluetoothDevice device, int value) {

    }

    @Override
    public void onBondingRequired(BluetoothDevice device) {

    }

    @Override
    public void onBonded(BluetoothDevice device) {

    }

    @Override
    public void onError(BluetoothDevice device, String message, int errorCode) {
        // TODO: later it might be necessary to determine what type of error
        // or what was happening to know the right thing to do. For example,
        // most of the times I have observed this are during initial connection
        // and indicates that it will fail to complete the connection. In fact
        // this seems to be the last event that happens if it doesn't reestablish
        // so we must use this to kill the job unless there is a timeout that will
        // handle it instead. onStopJob seems to fire as a timeout but takes a very
        // long time.
        mServiceLogger.e("Bluetooth error for device " + device.toString() + ". Error (0x" + Integer.toHexString(errorCode) + "): " + GattError.parse(errorCode));
        myJobFinished(mJob, true);
    }

    @Override
    public void onDeviceNotSupported(BluetoothDevice device) {
        mServiceLogger.e("onDeviceNotSupported");
        myJobFinished(mJob, true);
    }
}


