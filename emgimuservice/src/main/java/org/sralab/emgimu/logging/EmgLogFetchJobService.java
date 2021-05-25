package org.sralab.emgimu.logging;

/**
 * Copyright R. James Cotton. 2018
 */

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

// import com.firebase.jobdispatcher.JobParameters;
// import com.firebase.jobdispatcher.JobService;

import com.google.common.util.concurrent.ListenableFuture;

import org.sralab.emgimu.service.EmgImuService;

public class EmgLogFetchJobService extends ListenableWorker
{

    private static String TAG = EmgLogFetchJobService.class.getSimpleName();
    public static String JOB_TAG = "emg-log-fetch";

    private WorkerParameters params;

    public EmgLogFetchJobService(@NonNull Context appContext, @NonNull WorkerParameters params) {
        super(appContext, params);
        this.params = params;
    }

    @Override
    public ListenableFuture<Result> startWork() {

        Data input = getInputData();

        /**** Store information required to get log ****/
        String device_mac = input.getString("device_mac");

        Log.d(TAG, "onStartJob: " + device_mac);

        /*
        // Sent intent to service indicating to download the logs. Could do this as
        // a bound service if we want to wait for the results and reschedule this
        // task accordingly, but will avoid for now.
        //final Intent service = new Intent(this, EmgImuService.class);
        service.putExtra(EmgImuService.INTENT_FETCH_LOG, true);
        service.putExtra(EmgImuService.INTENT_DEVICE_MAC, device_mac);

        // As of Oreo, a background service must use this to launch a foreground
        // service. Note the foreground service must also show a notification while
        // this is going on.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(service);
        } else {
            startService(service);
        }

        return false;
        */

        return null;
    }

    @Override
    public void onStopped() {
    }

}


