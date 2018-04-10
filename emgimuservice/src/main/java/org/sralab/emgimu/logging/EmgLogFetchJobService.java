package org.sralab.emgimu.logging;

/**
 * Copyright R. James Cotton. 2018
 */

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.sralab.emgimu.service.EmgImuManager;
import org.sralab.emgimu.service.EmgImuManagerCallbacks;
import org.sralab.emgimu.service.EmgLogRecord;

import static no.nordicsemi.android.nrftoolbox.wearable.common.Constants.ACTION_DISCONNECT;

public class EmgLogFetchJobService extends JobService implements EmgImuManagerCallbacks
{

    private static String TAG = EmgLogFetchJobService.class.getSimpleName();
    public static String JOB_TAG = "emg-log-fetch";

    private FirebaseAuth mAuth;
    private FirebaseAnalytics mFirebaseAnalytics;

    private EmgImuManager mManager;

    private JobParameters mJob;
    @Override
    public boolean onStartJob(JobParameters job) {

        Log.i(TAG, "onStartJob: " + job.toString() + " " + job.getExtras().toString());
        String device_mac = job.getExtras().getString("device_mac");

        mJob = job;

        /******** Log into Firebase ********/
        mAuth = FirebaseAuth.getInstance();

        // Check if user is signed in (non-null) and update UI accordingly.
        FirebaseUser currentUser = mAuth.getCurrentUser();
        //TODO: mServiceLogger.d("User ID: " + currentUser);
        if (currentUser == null) {
            mAuth.signInAnonymously()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            FirebaseUser user = mAuth.getCurrentUser();
                            //TODO: mServiceLogger.d("signInAnonymously:success. UID:" + user.getUid());
                        } else {
                            //TODO: mServiceLogger.w("signInAnonymously:failure" + task.getException());
                        }
                    });
        } else {
            Log.d(TAG, "Prior logged in user: " + currentUser.getUid());
        }

        // Obtain the FirebaseAnalytics instance.
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, "EMG_IMU_FETCH_STARTED");
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);

        /******* Attempt to open device ******/
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Unable to fetch logs as adapter is disabled");
            return true;
        }

        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(device_mac);
        mManager = new EmgImuManager(this);
        mManager.setGattCallbacks(this);
        // TODO: mManager.setLogger(session);
        mManager.connect(device);

        return true;
    }

    private void myJobFinished (JobParameters params, boolean needsReschedule) {

        Log.d(TAG, "myJobFinished:");

        if (mManager != null && mManager.isConnected()) {
            mManager.abort();
            mManager.disconnect();
            mManager.close();
            mManager = null;
        }

        jobFinished(params, needsReschedule);
    }

    @Override
    public boolean onStopJob(JobParameters job) {
        Log.d(TAG, "onStopJob(): " + job.getExtras().toString());

        if (mManager != null) {
            mManager.abort();
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

    }

    @Override
    public void onDeviceConnected(BluetoothDevice device) {

    }

    @Override
    public void onDeviceDisconnecting(BluetoothDevice device) {

    }

    @Override
    public void onDeviceDisconnected(BluetoothDevice device) {

    }

    @Override
    public void onLinklossOccur(BluetoothDevice device) {

    }

    @Override
    public void onServicesDiscovered(BluetoothDevice device, boolean optionalServicesFound) {

    }

    @Override
    public void onDeviceReady(BluetoothDevice device) {
        Log.d(TAG, "Device connected and ready. Fetching log.");
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

    }

    @Override
    public void onDeviceNotSupported(BluetoothDevice device) {

    }
}


