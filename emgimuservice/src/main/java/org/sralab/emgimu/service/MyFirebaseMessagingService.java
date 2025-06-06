package org.sralab.emgimu.service;

import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static String TAG = MyFirebaseMessagingService.class.getSimpleName();

    public MyFirebaseMessagingService() {
    }

    /**
     * Called if InstanceID token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the InstanceID token
     * is initially generated so this is where you would retrieve the token.
     */

    @Override
    public void onNewToken(String token) {
        Log.d(TAG, "Refreshed token: " + token);

        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // Instance ID token to your app server.

        Log.d(TAG, "New token: " + token);
        //sendRegistrationToServer(token);
    }


}
