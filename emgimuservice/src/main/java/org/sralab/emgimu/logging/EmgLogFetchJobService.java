package org.sralab.emgimu.logging;

/**
 * Copyright R. James Cotton. 2018
 */

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.util.Log;

import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;

import org.json.JSONArray;
import org.json.JSONException;
import org.sralab.emgimu.service.EmgImuManager;
import org.sralab.emgimu.service.EmgImuManagerCallbacks;
import org.sralab.emgimu.service.EmgImuService;
import org.sralab.emgimu.service.EmgLogRecord;
import org.sralab.emgimu.service.R;

import android.support.v4.util.SimpleArrayMap;

import java.util.List;

import no.nordicsemi.android.log.ILogSession;
import no.nordicsemi.android.log.Logger;

public class EmgLogFetchJobService extends JobService implements EmgImuManagerCallbacks {

    private static String TAG = EmgLogFetchJobService.class.getName();
    public static String JOB_TAG = "emg-log-fetch";

    private final SimpleArrayMap<JobParameters, JobObjects> runningJobs = new SimpleArrayMap<JobParameters, JobObjects>();

    private class JobObjects {
        public EmgImuManager manager;
        public BluetoothDevice device;
    }

    @Override
    public boolean onStartJob(JobParameters job) {

        Log.i(TAG, "onStartJob: " + job.toString() + " " + job.getExtras().toString());

        String device_mac = job.getExtras().getString("device_mac");
        if (device_mac == null) {
            throw new IllegalArgumentException("Must have a device_mac in the job bundle");
        }


        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If no bluetooth adapter, this is not going to work
        if (bluetoothAdapter == null)
            return false;
        if (!bluetoothAdapter.isEnabled())
            return false;

        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(device_mac);
        Log.i(TAG, "Device to connect to: " + device);

        JobObjects jobObjects = new JobObjects();
        jobObjects.manager = new EmgImuManager(this);
        jobObjects.manager.setGattCallbacks(this);
        jobObjects.manager.setLogger(getLogger(device));
        jobObjects.device = device;
        synchronized(runningJobs) {
            runningJobs.put(job, jobObjects);
        }
        jobObjects.manager.connect(device);

        return true;
    }

    private JobParameters getJobFroDevice(BluetoothDevice device)  {
        synchronized (runningJobs) {
            for (int i = 0; i < runningJobs.size(); i++) {
                if (runningJobs.valueAt(i).device.getAddress().equals(device.getAddress())) {
                    return runningJobs.keyAt(i);
                }
            }
        }
        Log.e(TAG, "Could not find job for " + device.toString());
        throw new IndexOutOfBoundsException("Could not find job for " + device.toString());
    }

    private EmgImuManager getManagerForDevice(BluetoothDevice device) {
        return runningJobs.get(getJobFroDevice(device)).manager;
    }

    @Override
    public boolean onStopJob(JobParameters job) {
        Log.d(TAG, "onStopJob(): " + job.getExtras().toString());

        synchronized(runningJobs) {
            JobObjects jobObjects = runningJobs.get(job);
            jobObjects.manager.disconnect();
            jobObjects.manager.close();
            runningJobs.remove(job);
        }

        return true; // Answers the question: "Should this job be retried?"
    }

    /***** Everything below here is infrastructure required to use a BleManager ******/

    public int getLoggerProfileTitle() {
        // Return 0 to disable logging from this service
        return R.string.emgimu_log_fetch_name;
    }

    private ILogSession getLogger(final BluetoothDevice device) {
        final int titleId = getLoggerProfileTitle();
        ILogSession logSession = null;
        if (titleId > 0) {
            logSession = Logger.newSession(getApplicationContext(), getString(titleId), device.getAddress(), device.getName());
        }
        return logSession;
    }

    /**** These are when using real time control or streaming ****/
    @Override
    public void onEmgRawReceived(BluetoothDevice device, int value) {}

    @Override
    public void onEmgBuffReceived(BluetoothDevice device, int[] value) {}

    @Override
    public void onEmgPwrReceived(BluetoothDevice device, int value) {}

    @Override
    public void onEmgClick(BluetoothDevice device) {}

    /**** These are for downloading logging data from RACP ****/

    @Override
    public void onDeviceReady(BluetoothDevice device) {
        Log.d(TAG, "onDeviceReady()");
        EmgImuManager deviceManager = getManagerForDevice(device);
        deviceManager.getAllRecords();
    }

    @Override
    public void onOperationCompleted(BluetoothDevice device) {
        Log.i(TAG, "Operation completed");
        EmgImuManager deviceManager = getManagerForDevice(device);
        List<EmgLogRecord> records = deviceManager.getRecords();
        Log.i(TAG, "Received " + records.size() + " entries");
        deviceManager.disconnect();
    }

    @Override
    public void onDeviceDisconnected(BluetoothDevice device) {
        Log.i(TAG, "onDeviceDisconnected(" + device.getAddress() + ")");
        JobParameters job = getJobFroDevice(device);
        EmgImuManager deviceManager = getManagerForDevice(device);
        deviceManager.close();
        jobFinished(job, false);
    }

    @Override
    public void onOperationStarted(BluetoothDevice device) {
        Log.i(TAG, "Operation started");
    }

    @Override
    public void onDatasetClear(BluetoothDevice device) {
        Log.i(TAG, "Dataset cleared before fetch");
    }

    @Override
    public void onNumberOfRecordsRequested(BluetoothDevice device, int value) {
        Log.i(TAG, "Number of records available is: " + value);
    }

    @Override
    public void onEmgLogRecordReceived(BluetoothDevice device, EmgLogRecord record)
    {
        Log.i(TAG, "Received record with " + record.emgPwr);
    }

    @Override
    public void onOperationFailed(BluetoothDevice device) {
        Log.e(TAG, "Fetching records failed");
    }

    @Override
    public void onOperationAborted(BluetoothDevice device) {
        Log.e(TAG, "Fetching records aborted");
    }

    @Override
    public void onOperationNotSupported(BluetoothDevice device) {
        Log.e(TAG, "Operation not supported");
    }


    /**** These are core methods for anything using a BleManager ****/
    @Override
    public void onDeviceConnecting(BluetoothDevice device) {

    }

    @Override
    public void onDeviceConnected(BluetoothDevice device) {
        Log.i(TAG, "onDeviceConnected(" + device.getAddress() + ")");
    }

    @Override
    public void onDeviceDisconnecting(BluetoothDevice device) {

    }

    @Override
    public void onLinklossOccur(BluetoothDevice device) {

    }

    @Override
    public void onServicesDiscovered(BluetoothDevice device, boolean optionalServicesFound) {
        Log.d(TAG, "onServicesDiscovered()");
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


