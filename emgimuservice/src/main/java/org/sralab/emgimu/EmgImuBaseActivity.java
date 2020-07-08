package org.sralab.emgimu;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.sralab.emgimu.service.EmgImuManager;
import org.sralab.emgimu.service.EmgImuObserver;
import org.sralab.emgimu.service.EmgImuService;

import java.util.UUID;

import no.nordicsemi.android.nrftoolbox.profile.multiconnect.BleMulticonnectProfileService;
import no.nordicsemi.android.nrftoolbox.profile.multiconnect.BleMulticonnectProfileServiceReadyActivity;

public abstract class EmgImuBaseActivity extends BleMulticonnectProfileServiceReadyActivity<EmgImuService.EmgImuBinder> implements EmgImuObserver {


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

    @Override
    protected void onResume() {
        Log.d("EmgImuBaseActivity", "Starting service");
        // Start the service here because we want it to be sticky for at least
        // a short while when changing between activities to avoid constantly
        // rebinding the BLE device.
        final Intent service = new Intent(this, getServiceClass());
        startService(service);

        // This actually triggers binding to the service
        super.onResume();
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
        if (getService() != null)
            return getService().getLoggerProfileTitle();
        else
            return 0;
    }
}
