package org.sralab.emgimu.logging;

/**
 * Copyright R. James Cotton. 2018
 */

import android.content.Intent;
import android.os.Build;

import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;

import org.sralab.emgimu.service.EmgImuService;

public class EmgLogFetchJobService extends JobService
{

    private static String TAG = EmgLogFetchJobService.class.getSimpleName();
    public static String JOB_TAG = "emg-log-fetch";

    @Override
    public boolean onStartJob(JobParameters job) {


        /**** Store information required to get log ****/
        String device_mac = job.getExtras().getString("device_mac");


        // Sent intent to service indicating to download the logs. Could do this as
        // a bound service if we want to wait for the results and reschedule this
        // task accordingly, but will avoid for now.
        final Intent service = new Intent(this, EmgImuService.class);
        service.putExtra(EmgImuService.INTENT_FETCH_LOG, true);
        service.putExtra(EmgImuService.INTENT_DEVICE_MAC, job.getExtras().getString("device_mac"));

        // As of Oreo, a background service must use this to launch a foreground
        // service. Note the foreground service must also show a notification while
        // this is going on.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(service);
        } else {
            startService(service);
        }

        return false;
    }

    @Override
    public boolean onStopJob(JobParameters job) {
        return false;
    }

}


