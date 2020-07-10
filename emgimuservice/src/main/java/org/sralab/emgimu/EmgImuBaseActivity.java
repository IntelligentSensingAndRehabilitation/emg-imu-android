package org.sralab.emgimu;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.sralab.emgimu.service.EmgImuManager;
import org.sralab.emgimu.service.EmgImuObserver;
import org.sralab.emgimu.service.EmgImuService;
import org.sralab.emgimu.service.IEmgImuServiceBinder;

import java.util.UUID;

import no.nordicsemi.android.nrftoolbox.profile.multiconnect.BleMulticonnectProfileService;
import no.nordicsemi.android.nrftoolbox.profile.multiconnect.BleMulticonnectProfileServiceReadyActivity;

public abstract class EmgImuBaseActivity extends BleMulticonnectProfileServiceReadyActivity implements EmgImuObserver {

    private final BroadcastReceiver mCommonBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final BluetoothDevice bluetoothDevice = intent.getParcelableExtra(EmgImuService.EXTRA_DEVICE);
            final String action = intent.getAction();
            switch (action) {
                case EmgImuService.BROADCAST_BATTERY_LEVEL: {
                    Log.d("BaseActivity", "Battery ");
                    final float value = intent.getIntExtra(EmgImuService.EXTRA_BATTERY_LEVEL, -1);
                    if (value > 0)
                        onBatteryReceived(bluetoothDevice, value);
                    break;
                }
                case EmgImuService.BROADCAST_EMG_RAW: {
                    final int value = intent.getIntExtra(EmgImuService.EXTRA_EMG_RAW, -1);
                    if (value > 0)
                        onEmgRawReceived(bluetoothDevice, value);
                    break;
                }
                case EmgImuService.BROADCAST_EMG_PWR: {
                    final int value = intent.getIntExtra(EmgImuService.EXTRA_EMG_PWR, -1);
                    if (value > 0)
                        onEmgPwrReceived(bluetoothDevice, value);
                    break;
                }
                case EmgImuService.BROADCAST_EMG_BUFF: {
                    final double[] value = intent.getDoubleArrayExtra(EmgImuService.EXTRA_EMG_BUFF);
                    final int CHANNELS = intent.getIntExtra(EmgImuService.EXTRA_EMG_CHANNELS, 0);
                    final int SAMPLES = value.length / CHANNELS;
                    final long ts_ms = intent.getLongExtra(EmgImuService.EXTRA_EMG_TS_MS, 0);
                    if (value != null) {
                        double [][] data = new double[CHANNELS][SAMPLES];
                        for (int idx = 0; idx < value.length; idx++) {
                            int i = idx % CHANNELS;
                            int j = idx / CHANNELS;
                            data[i][j] = value[idx];
                        }
                        onEmgBuffReceived(bluetoothDevice, ts_ms, data);
                    }
                    else {
                        throw new RuntimeException("Cannot parse EMG data");
                    }
                    break;
                }
                case EmgImuService.BROADCAST_EMG_CLICK: {
                    onEmgClick(bluetoothDevice);
                    break;
                }
                case EmgImuService.BROADCAST_IMU_ACCEL: {
                    final float [] value = intent.getFloatArrayExtra(EmgImuService.EXTRA_IMU_ACCEL);
                    final int CHANNELS = 3;
                    final int SAMPLES = 3;
                    if (value != null) {
                        float [][] data = new float[CHANNELS][SAMPLES];
                        for (int idx = 0; idx < value.length; idx++) {
                            int i = idx % CHANNELS;
                            int j = idx / CHANNELS;
                            data[i][j] = value[idx];
                        }
                        onImuAccelReceived(bluetoothDevice, data);
                    }

                    break;
                }
                case EmgImuService.BROADCAST_IMU_GYRO: {
                    final float [] value = intent.getFloatArrayExtra(EmgImuService.EXTRA_IMU_GYRO);
                    final int CHANNELS = 3;
                    final int SAMPLES = 3;
                    if (value != null) {
                        float [][] data = new float[CHANNELS][SAMPLES];
                        for (int idx = 0; idx < value.length; idx++) {
                            int i = idx % CHANNELS;
                            int j = idx / CHANNELS;
                            data[i][j] = value[idx];
                        }
                        onImuGyroReceived(bluetoothDevice, data);
                    }

                    break;
                }
                case EmgImuService.BROADCAST_IMU_MAG: {
                    final float [] value = intent.getFloatArrayExtra(EmgImuService.EXTRA_IMU_MAG);
                    final int CHANNELS = 3;
                    final int SAMPLES = 3;
                    if (value != null) {
                        float [][] data = new float[CHANNELS][SAMPLES];
                        for (int idx = 0; idx < value.length; idx++) {
                            int i = idx % CHANNELS;
                            int j = idx / CHANNELS;
                            data[i][j] = value[idx];
                        }
                        onImuMagReceived(bluetoothDevice, data);
                    }
                    break;
                }
                case EmgImuService.BROADCAST_IMU_ATTITUDE: {
                    final float [] quat = intent.getFloatArrayExtra(EmgImuService.EXTRA_IMU_ATTITUDE);
                    onImuAttitudeReceived(bluetoothDevice, quat);
                    break;
                }
            }
        }
    };

    @Override
    protected void onInitialize(final Bundle savedInstanceState) {
        LocalBroadcastManager.getInstance(this).registerReceiver(mCommonBroadcastReceiver, makeIntentFilter());
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mCommonBroadcastReceiver);
    }


    /**
     * Called when activity binds to the service. The parameter is the object returned in {@link Service#onBind(Intent)} method in your service.
     * It is safe to obtain managed devices now.
     */
    protected abstract void onServiceBinded(IEmgImuServiceBinder binder);

    private IEmgImuServiceBinder mService;
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @SuppressWarnings("unchecked")
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            mService = IEmgImuServiceBinder.Stub.asInterface(service);
            try {
                addManagedDevices(mService.getManagedDevices());
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            onServiceBinded(mService);

            // and notify user if device is connected
            for (final BluetoothDevice device : getManagedDevices()) {
                try {
                    if (mService.isConnected(device))
                        onDeviceConnected(device);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            mService = null;
            onServiceUnbinded();
        }
    };

    @Override
    public void onResume() {
        Log.d("EmgImuBaseActivity", "Starting service");
        // Start the service here because we want it to be sticky for at least
        // a short while when changing between activities to avoid constantly
        // rebinding the BLE device.
        final Intent service = new Intent(this, getServiceClass());
        startService(service);
        bindService(service, mServiceConnection, Context.BIND_AUTO_CREATE);
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mService != null) {
            // We don't want to perform some operations (e.g. disable Battery Level notifications) in the service if we are just rotating the screen.
            // However, when the activity will disappear, we may want to disable some device features to reduce the battery consumption.
            try {
                mService.setActivityIsChangingConfiguration(isChangingConfigurations());
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            // Log it here as there is no callback when the service gets unbound
            // and the mService will not be available later (the activity doesn't keep log sessions)
            //mService.log(LogContract.Log.Level.DEBUG, "Activity unbound from the service");
        }

        unbindService(mServiceConnection);
        mService = null;

        onServiceUnbinded();
    }

    /**
     * Returns the service interface that may be used to communicate with the sensor. This will return <code>null</code> if the device is disconnected from the
     * sensor.
     *
     * @return the service binder or <code>null</code>
     */
    protected IEmgImuServiceBinder getService() {
        return mService;
    }


    private static IntentFilter makeIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(EmgImuService.EXTRA_BATTERY_LEVEL);
        intentFilter.addAction(EmgImuService.BROADCAST_EMG_RAW);
        intentFilter.addAction(EmgImuService.BROADCAST_EMG_PWR);
        intentFilter.addAction(EmgImuService.BROADCAST_EMG_BUFF);
        intentFilter.addAction(EmgImuService.BROADCAST_EMG_CLICK);
        intentFilter.addAction(EmgImuService.BROADCAST_IMU_ACCEL);
        intentFilter.addAction(EmgImuService.BROADCAST_IMU_GYRO);
        intentFilter.addAction(EmgImuService.BROADCAST_IMU_MAG);
        intentFilter.addAction(EmgImuService.BROADCAST_IMU_ATTITUDE);
        return intentFilter;
    }


    @Override
    protected UUID getFilterUUID() {
        return EmgImuManager.EMG_SERVICE_UUID;
    }

    public void onDeviceSelected(final BluetoothDevice device, final String name) {
        /*
        final int titleId = getLoggerProfileTitle();
        ILogSession logSession = null;
        if (titleId > 0) {
            logSession = Logger.newSession(getApplicationContext(), getString(titleId), device.getAddress(), name);
            // If nRF Logger is not installed we may want to use local logger
            if (logSession == null && getLocalAuthorityLogger() != null) {
                logSession = LocalLogSession.newSession(getApplicationContext(), getLocalAuthorityLogger(), device.getAddress(), name);
            }
        }*/
        try {
            mService.connect(device); //, logSession);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * Note that this is called via the LocalBroadcastManager intents from the service
     */
    @Override
    public void onEmgRawReceived(final BluetoothDevice device, int value) {
        // Do nothing
    }

    @Override
    public void onEmgPwrReceived(final BluetoothDevice device, int value) {
        // Do nothing
    }


    @Override
    public void onEmgClick(final BluetoothDevice device) {
        // Do nothing
    }

    @Override
    protected Class<? extends BleMulticonnectProfileService> getServiceClass() {
        return EmgImuService.class;
    }

    @Override
    protected int getLoggerProfileTitle() {
        /*if (getService() != null)
            return getService().getLoggerProfileTitle();
        else*/
            return 0;
    }
}
