package org.sralab.emgimu.logging;

/**
 * Copyright R. James Cotton. 2018
 */

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;

public class EmgLogFetchJobService extends JobService {
    private static String TAG = EmgLogFetchJobService.class.getName();
    public static String JOB_TAG = TAG;

    /**
     * This asynctask will run a job once conditions are met with the constraints
     * As soon as user device gets connected with the power supply. it will generate
     * a notification showing that condition is met.
     */
    private AsyncTask mBackgroundTask;

    @Override
    public boolean onStartJob(JobParameters job) {
        mBackgroundTask = new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] objects) {
                Context context = EmgLogFetchJobService.this;
                //EmgLogFetchJobService.executeTasks(context, EmgLogFetchJobService.ACTION_CHARGING_REMINDER);
                Log.i(TAG, "onStartJob");
                return null;
            }

            @Override
            protected void onPostExecute(Object o) {
                jobFinished(job, true);
                Log.i(TAG, "onStartJob- OnPost");
            }
        };

        mBackgroundTask.execute();
        return true;    }

    @Override
    public boolean onStopJob(JobParameters job) {
        Log.d(TAG, "onStartJob()");
        return false; // Answers the question: "Should this job be retried?"
    }
}


