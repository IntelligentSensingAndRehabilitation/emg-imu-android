
package org.sralab.emgimu.service;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

//import org.sralab.emgimu.service.EmgImuService.

public class UnityEmgImuBridge {

    private static String TAG = UnityEmgImuBridge.class.getSimpleName();

    static Activity myActivity;

    // Called From C# to get the Activity Instance
    public static void receiveActivityInstance(Activity tempActivity) {
        Log.d(TAG, "Bridge updated with activity handle");
        myActivity = tempActivity;
    }

    public static void StartEmgImuService() {
        Log.d(TAG, "Service start requested");
        //myActivity.startService(new Intent(myActivity, EmgImuService.class));
    }
}
