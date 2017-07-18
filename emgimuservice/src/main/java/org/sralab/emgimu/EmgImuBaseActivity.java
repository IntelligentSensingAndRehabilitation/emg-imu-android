package org.sralab.emgimu;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;

import org.sralab.emgimu.service.EmgImuManager;
import org.sralab.emgimu.service.EmgImuManagerCallbacks;
import org.sralab.emgimu.service.EmgImuService;

import java.util.UUID;

import no.nordicsemi.android.nrftoolbox.profile.multiconnect.BleMulticonnectProfileService;
import no.nordicsemi.android.nrftoolbox.profile.multiconnect.BleMulticonnectProfileServiceReadyActivity;

public abstract class EmgImuBaseActivity extends BleMulticonnectProfileServiceReadyActivity<EmgImuService.EmgImuBinder> implements EmgImuManagerCallbacks {


    private final BroadcastReceiver mCommonBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final BluetoothDevice bluetoothDevice = intent.getParcelableExtra(EmgImuService.EXTRA_DEVICE);
            final String action = intent.getAction();
            switch (action) {
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
                    final int [] value = intent.getIntArrayExtra(EmgImuService.EXTRA_EMG_BUFF);
                    if (value != null)
                        onEmgBuffReceived(bluetoothDevice, value);
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

    private static IntentFilter makeIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(EmgImuService.BROADCAST_EMG_RAW);
        intentFilter.addAction(EmgImuService.BROADCAST_EMG_PWR);
        intentFilter.addAction(EmgImuService.BROADCAST_EMG_BUFF);
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
    public void onEmgBuffReceived(final BluetoothDevice device, int [] value) {
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
