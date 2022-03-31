package org.sralab.emgimu.unity_bindings;

//import needed packages / classes.
import android.app.Application;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
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

/**
 * Special Note:
 * Each time you make a change to this file, do the following (~10 min cycle):
 * 1. rebuild the project
 * 2. drag-and-drop "\emg-imu\android\common\build\outputs\aar\common-debug.aar"
 *      into unity/Assets/Android
 * 3. build-and-run unity project
 */
public class Bridge extends Application
{
    public static final String TAG = Bridge.class.getSimpleName();
    private PluginCallback callback; // methods accessed through unity games
    private IEmgImuServiceBinder service;
    private long startTime;
    private String gameName;
    private String gameLog;
    private String gameSelectedDeviceMac;
    private int gameSelectedDeviceChannel;

    /**
     * Important part to note here is that the parameter data in the Overridden method handleData()
     * contains an array of emg power, index corresponds to the data channel.
     * callback.onSuccess() transmits the emgPwr data to the game (Unity).
     */
    private final IEmgImuPwrDataCallback.Stub pwrObserver = new IEmgImuPwrDataCallback.Stub() {
        @Override
        public void handleData(BluetoothDevice device, EmgPwrData data) throws RemoteException {
            if ((callback != null) && (gameSelectedDeviceMac != null)) {
                callback.onSuccess(Integer.toString(data.power[gameSelectedDeviceChannel]));
                callback.onBatteryLife(Double.toString(data.batteryVoltage));
                callback.onFirmwareVersion(data.firmwareVersion);
            }
        }
    };

    private final IEmgImuStreamDataCallback.Stub streamObserver  = new IEmgImuStreamDataCallback.Stub() {
        @Override
        public void handleData(BluetoothDevice device, EmgStreamData data) {}
    };

    /**
     * This field represents the service connection between the game (Unity) and the
     * Biofeedback App. The important thing to note here is that when the service
     * connection has been established, the Biofeedback app send to the game (Unity)
     * a list of sensors, which it maintains. The Config nested application inside
     * Biofeedback app (first app on the left), contains the list of sensors; new sensors
     * can be added here or existing sensors can be deleted inside that menu.
     * If these devices are in proximity the mobile device (phone/tablet), when the game
     * starts, these sensors automatically will connect to the device - characterized by
     * the solid-green LED state. When the data is transmitting, there should be both -
     * a solid-green & a flashing-red LEDs.
     */
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @SuppressWarnings("unchecked")
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder binder) {
            service = IEmgImuServiceBinder.Stub.asInterface(binder);
            try {
                /* Sends the list of devices to the game (unity). */
                callback.sendDeviceList(Arrays.toString(service.getManagedDevices().toArray()));
                Log.d(TAG, "Sent device list to the game" + service.getManagedDevices().toString());
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }
        @Override
        public void onServiceDisconnected(final ComponentName name) {
            service = null;
        }
    };

    // region Methods Called from the Game (Unity)
    /**
     * This method is called from the game (Unity) to log the data from each trial. Each trial
     * is characterized by a single pass of the curve on the screen.
     * @param roundInfo
     */
    public void logTrial(String roundInfo) {
        // expects to receive something that can be added to a list, which can
        // be serialized to JSON
        if (gameLog == null || gameLog.length() == 0) {
            gameLog = "[" + roundInfo + "]";
            Log.d(TAG, "Bridge, Unity roundInfo = " + roundInfo);
        } else {
            gameLog = gameLog.substring(0, gameLog.length() - 1) + ", " + roundInfo + "]";
        }

        try {
            service.storeGameplayRecord(gameName, startTime, gameLog);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * This method is called from the game (Unity). When the game first starts, it connects to the
     * Biofeedback app using the Inter-Process Communication (IPC), through it's AndroidBindings
     * object.
     * @param ctx
     * @param callback
     */
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
        gameSelectedDeviceMac = temp[0];
        /*
        Parsing the channel string and converting it into an integer.
        Start with "ch-y", then split it into [ch, y], then take the second element.
        The game sends channels using 1-index convention, while this app uses 0-index convention;
        thus, we subtract 1 from the input
         */
        gameSelectedDeviceChannel = (Integer.parseInt((temp[1].split("-"))[1])) - 1;
        Log.d(TAG, "Bridge, gameSelectedDeviceChannel = " + gameSelectedDeviceChannel);
        /* Register the power observer for the emg power callback to stream the emg power data. */
        try {
            for (BluetoothDevice device : service.getManagedDevices())
            {
                if (device.getAddress().equals(gameSelectedDeviceMac)) {
                    service.registerEmgPwrObserver(device, pwrObserver); // pass this to service
                    service.registerEmgStreamObserver(device, streamObserver);
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
    // endregion
}