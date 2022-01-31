package org.sralab.emgimu.unity_bindings;

//import needed packages / classes.
import android.app.Application;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.nfc.Tag;
import android.os.Debug;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import org.sralab.emgimu.service.EmgPwrData;
import org.sralab.emgimu.service.EmgStreamData;
import org.sralab.emgimu.service.IEmgImuPwrDataCallback;
import org.sralab.emgimu.service.IEmgImuServiceBinder;
import org.sralab.emgimu.service.IEmgImuStreamDataCallback;

import java.util.Arrays;
import java.util.Date;

public class Bridge extends Application
{
    public static final String TAG = Bridge.class.getSimpleName();

    /** Unity registers the callback to receive messages from android */
    private PluginCallback callback;

    private final IEmgImuPwrDataCallback.Stub pwrObserver = new IEmgImuPwrDataCallback.Stub() {
        @Override
        public void handleData(BluetoothDevice device, EmgPwrData data) throws RemoteException {
            /*// ########### SPECIAL NOTE ##################################### //
            // EVERY TIME YOU MAKE CHANGE TO THIS FILE, DO THIS: //
            // 1. rebuild the project
            // 2. drag-and-drop "\emg-imu\android\common\build\outputs\aar\common-debug.aar"
            //      file "common-debug.aar" into unity/assets/android
            // 3. build-and-run unity project
            // stick the code logic here*/

            String device_mac = (String) device.toString();
            Log.d(TAG, "device_mac = " + device_mac + ", len(str) = " + device_mac.length());
            //Log.d(TAG, "unity_mac = " + unitySelectedDevice + ", len(str) = " + unitySelectedDevice.length());


//            if (callback != null) {
//                if (unitySelectedDevice != null) {
//                    Log.d(TAG, "Device was selected --> " + unitySelectedDevice);
//                    Log.d(TAG, "Current Device --> " + device);
//                    Log.d(TAG, "device.toString()=" + device.toString() + " | unitySelectedDevice=" + unitySelectedDevice);
//                    Log.d(TAG, "BOOOOOOOOOMM!");
//                    if (device_mac.equals(unitySelectedDevice))
//                    {
//                        Log.d(TAG, "MATCH!!!! " + device_mac + "=" + unitySelectedDevice);
//                        callback.onSuccess(Integer.toString(data.power[0]));
//                    }
///*                    if (device.toString() == unitySelectedDevice)
//                    {
//                        Log.d(TAG, "We have a match! --> " + device.toString() + " = " + unitySelectedDevice);
//                    }*/
//                }
//                //callback.onSuccess(Integer.toString(data.power[0]));
//            }
        }
    };

    private final IEmgImuStreamDataCallback.Stub streamObserver  = new IEmgImuStreamDataCallback.Stub() {
        @Override
        public void handleData(BluetoothDevice device, EmgStreamData data) {}
    };

    private IEmgImuServiceBinder service;
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @SuppressWarnings("unchecked")
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder binder) {
            service = IEmgImuServiceBinder.Stub.asInterface(binder);
            try {
                Log.d(TAG, "Managed Devices: " + service.getManagedDevices().toString());
                callback.sendDeviceList(Arrays.toString(service.getManagedDevices().toArray()));

                // stream data from all sensors
//                service.registerEmgPwrObserver(pwrObserver);
                //service.registerEmgStreamObserver(streamObserver);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            service = null;
        }
    };

    // These methods get called from Unity
    long startTime;
    String gameName;
    String gameLog;
    private static String gameSelectedDeviceMac;
    private static int gameSelectedDeviceChannel;

    public void logTrial(String roundInfo) {
        // expects to receive something that can be added to a list, which can
        // be serialized to JSON
        if (gameLog == null || gameLog.length() == 0) {
            gameLog = "[" + roundInfo + "]";
        } else {
            gameLog = gameLog.substring(0, gameLog.length() - 1) + ", " + roundInfo + "]";
        }

        try {
            service.storeGameplayRecord(gameName, startTime, gameLog);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void connectService(final Context ctx, final PluginCallback callback) {
        // This method gets called from Unity, which created the connection
        Log.d(TAG, "connectService");
        final Intent service = new Intent();
        service.setComponent(new ComponentName("org.sralab.emgimu", "org.sralab.emgimu.service.EmgImuService"));
        ctx.bindService(service, mServiceConnection, Context.BIND_AUTO_CREATE);
        this.callback = callback;  // avoid dead links

        startTime = new Date().getTime();
        gameName = ctx.getPackageName();
    }

    protected void disconnectService(final Context ctx) {
        Log.d(TAG, "disconnectService");
        ctx.unbindService(mServiceConnection);
        service = null;
        callback = null;
    }

    /**
     * This method is called from the game, when the user selects the sensor and corresponding
     * channel they wish to stream emg power value from. The main performs the following:
     *  (1) parse the String parameter into a device MAC address and the corresponding channel.
     *  (2) registers Emg power observer callback, which will start streaming the data for the
     *      selected sensor.
     * @param deviceMacAndChannel String representing a MAC address and the channel in the following
     *                            format: XX:XX:XX:XX:XX:XX - ch-y, where each part is separated
     *                            by a dash (" - "), respectively.
     *                            For example, for "F9:C9:C5:5F:73:56 - ch-1", the device MAC
     *                            address is "F9:C9:C5:5F:73:56" and the channel is "ch-1".
     */
    public void selectDevice(String deviceMacAndChannel) {
        /*
        Temporary array to hold the mac and channel for further processing.
        Results in [XX:XX:XX:XX:XX:XX, ch-y].
         */
        String[] temp = deviceMacAndChannel.split(" - ");
        Log.d(TAG, "Bridge, temp.length = " + temp.length);
        for(int i = 0; i < temp.length; i++) {
            Log.d(TAG, "Bridge, before: temp[" + i + "]=" + temp[i] + ", length=" + temp[i].length());
        }

        gameSelectedDeviceMac = temp[0];
        /*
        Parsing the channel string and converting it into an integer.
        Start with "ch-y", then split it into [ch, y], then take the second element.
         */
        gameSelectedDeviceChannel = Integer.parseInt((temp[1].split("-"))[1]);
        Log.d(TAG, "Bridge, after: Mac=" + gameSelectedDeviceMac + ", length=" + gameSelectedDeviceMac.length());
        Log.d(TAG, "Bridge, after: ch=" + gameSelectedDeviceChannel);

        /*
        Register the power observer for the emg power callback to stream the emg power data.
         */
        try {
            service.registerEmgPwrObserver(gameSelectedDeviceMac, pwrObserver); // pass this to service
            //service.registerEmgPwrObserver(pwrObserver);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}