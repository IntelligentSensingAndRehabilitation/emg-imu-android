package org.sralab.martianrun;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;

import org.sralab.emgimu.service.EmgImuManager;
import org.sralab.emgimu.service.EmgImuManagerCallbacks;
import org.sralab.emgimu.service.EmgImuService;
import org.sralab.emgimu.service.EmgLogRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import no.nordicsemi.android.nrftoolbox.profile.multiconnect.BleMulticonnectProfileService;
import no.nordicsemi.android.nrftoolbox.scanner.ScannerFragment;
import no.nordicsemi.android.nrftoolbox.utility.DebugLogger;

public class EmgImuServiceHolder<E extends EmgImuService.EmgImuBinder> implements EmgImuManagerCallbacks {

    private static final String TAG = "EmgImuServiceHolder";

    private Context mContext;
    private E mService;
    private List<BluetoothDevice> mManagedDevices;

    public EmgImuServiceHolder(Context context) {
        mContext = context;
    }

    protected void onServiceBinded(final E binder) { mService = binder; }
    protected void onServiceUnbinded() { mService = null; }
    protected Class<? extends BleMulticonnectProfileService> getServiceClass() {
        return EmgImuService.class;
    }

    protected static final int REQUEST_ENABLE_BT = 2;

    private final BroadcastReceiver mCommonBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BleMulticonnectProfileService.EXTRA_DEVICE);
            final String action = intent.getAction();
            switch (action) {
                case BleMulticonnectProfileService.BROADCAST_CONNECTION_STATE: {
                    final int state = intent.getIntExtra(BleMulticonnectProfileService.EXTRA_CONNECTION_STATE, BleMulticonnectProfileService.STATE_DISCONNECTED);

                    switch (state) {
                        case BleMulticonnectProfileService.STATE_CONNECTED: {
                            onDeviceConnected(bluetoothDevice);
                            break;
                        }
                        case BleMulticonnectProfileService.STATE_DISCONNECTED: {
                            onDeviceDisconnected(bluetoothDevice);
                            break;
                        }
                        case BleMulticonnectProfileService.STATE_LINK_LOSS: {
                            onLinklossOccur(bluetoothDevice);
                            break;
                        }
                        case BleMulticonnectProfileService.STATE_CONNECTING: {
                            onDeviceConnecting(bluetoothDevice);
                            break;
                        }
                        case BleMulticonnectProfileService.STATE_DISCONNECTING: {
                            onDeviceDisconnecting(bluetoothDevice);
                            break;
                        }
                        default:
                            // there should be no other actions
                            break;
                    }
                    break;
                }
                case BleMulticonnectProfileService.BROADCAST_SERVICES_DISCOVERED: {
                    final boolean primaryService = intent.getBooleanExtra(BleMulticonnectProfileService.EXTRA_SERVICE_PRIMARY, false);
                    final boolean secondaryService = intent.getBooleanExtra(BleMulticonnectProfileService.EXTRA_SERVICE_SECONDARY, false);

                    if (primaryService) {
                        onServicesDiscovered(bluetoothDevice, secondaryService);
                    } else {
                        onDeviceNotSupported(bluetoothDevice);
                    }
                    break;
                }
                case BleMulticonnectProfileService.BROADCAST_DEVICE_READY: {
                    onDeviceReady(bluetoothDevice);
                    break;
                }
                case BleMulticonnectProfileService.BROADCAST_BOND_STATE: {
                    final int state = intent.getIntExtra(BleMulticonnectProfileService.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
                    switch (state) {
                        case BluetoothDevice.BOND_BONDING:
                            onBondingRequired(bluetoothDevice);
                            break;
                        case BluetoothDevice.BOND_BONDED:
                            onBonded(bluetoothDevice);
                            break;
                    }
                    break;
                }
                case BleMulticonnectProfileService.BROADCAST_BATTERY_LEVEL: {
                    final int value = intent.getIntExtra(BleMulticonnectProfileService.EXTRA_BATTERY_LEVEL, -1);
                    if (value > 0)
                        onBatteryValueReceived(bluetoothDevice, value);
                    break;
                }
                case BleMulticonnectProfileService.BROADCAST_ERROR: {
                    final String message = intent.getStringExtra(BleMulticonnectProfileService.EXTRA_ERROR_MESSAGE);
                    final int errorCode = intent.getIntExtra(BleMulticonnectProfileService.EXTRA_ERROR_CODE, 0);
                    onError(bluetoothDevice, message, errorCode);
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
                    final int count = intent.getIntExtra(EmgImuService.EXTRA_EMG_COUNT, 0);
                    if (value != null) {
                        double [][] data = new double[CHANNELS][SAMPLES];
                        for (int idx = 0; idx < value.length; idx++) {
                            int i = idx % CHANNELS;
                            int j = idx / CHANNELS;
                            data[i][j] = value[idx];
                        }
                        onEmgBuffReceived(bluetoothDevice, count, data);
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

            }
        }
    };

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @SuppressWarnings("unchecked")
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            final E bleService = mService = (E) service;
            //bleService.log(LogContract.Log.Level.DEBUG, "Activity bound to the service");
            mManagedDevices.addAll(bleService.getManagedDevices());
            onServiceBinded(bleService);

            // and notify user if device is connected
            for (final BluetoothDevice device : mManagedDevices) {
                if (bleService.isConnected(device))
                    onDeviceConnected(device);
            }
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            mService = null;
            onServiceUnbinded();
        }
    };

    //! Must be called by users onCreate
    public final void onCreate() {
        mManagedDevices = new ArrayList<>();

        ensureBLESupported();
        if (!isBLEEnabled()) {
            showBLEDialog();
        }

        LocalBroadcastManager.getInstance(mContext).registerReceiver(mCommonBroadcastReceiver, makeIntentFilter());
    }

    //! Must be called by users onResume
    public void onResume() {
		/*
		 * In comparison to BleProfileServiceReadyActivity this activity always starts the service when started.
		 * Connecting to a device is done by calling mService.connect(BluetoothDevice) method, not startService(...) like there.
		 * The service will stop itself when all devices it manages were disconnected and unmanaged and the last activity unbinds from it.
		 */
        final Intent service = new Intent(mContext, getServiceClass());
        mContext.startService(service);
        mContext.bindService(service, mServiceConnection, 0);
    }


    //! Must be called by users onDestroy
    public void onDestroy() {
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mCommonBroadcastReceiver);
    }

    private static IntentFilter makeIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleMulticonnectProfileService.BROADCAST_CONNECTION_STATE);
        intentFilter.addAction(BleMulticonnectProfileService.BROADCAST_SERVICES_DISCOVERED);
        intentFilter.addAction(BleMulticonnectProfileService.BROADCAST_DEVICE_READY);
        intentFilter.addAction(BleMulticonnectProfileService.BROADCAST_BOND_STATE);
        intentFilter.addAction(BleMulticonnectProfileService.BROADCAST_BATTERY_LEVEL);
        intentFilter.addAction(BleMulticonnectProfileService.BROADCAST_ERROR);
        intentFilter.addAction(EmgImuService.BROADCAST_EMG_RAW);
        intentFilter.addAction(EmgImuService.BROADCAST_EMG_PWR);
        intentFilter.addAction(EmgImuService.BROADCAST_EMG_BUFF);
        intentFilter.addAction(EmgImuService.BROADCAST_EMG_CLICK);
        return intentFilter;
    }

    @Override
    public void onDeviceConnecting(final BluetoothDevice device) {
        // empty default implementation
    }

    @Override
    public void onDeviceConnected(BluetoothDevice device) {

    }

    @Override
    public void onDeviceDisconnecting(final BluetoothDevice device) {
        // empty default implementation
    }

    @Override
    public void onDeviceDisconnected(BluetoothDevice device) {

    }

    @Override
    public void onLinklossOccur(final BluetoothDevice device) {
        // empty default implementation
    }

    @Override
    public void onServicesDiscovered(final BluetoothDevice device, final boolean optionalServicesFound) {
        // empty default implementation
    }

    @Override
    public void onDeviceReady(final BluetoothDevice device) {
        // empty default implementation
    }

    @Override
    public void onBondingRequired(final BluetoothDevice device) {
        // empty default implementation
    }

    @Override
    public void onBonded(final BluetoothDevice device) {
        // empty default implementation
    }

    @Override
    public void onDeviceNotSupported(final BluetoothDevice device) {
    }

    @Override
    public final boolean shouldEnableBatteryLevelNotifications(final BluetoothDevice device) {
        // This method will never be called.
        // Please see BleMulticonnectProfileService#shouldEnableBatteryLevelNotifications(BluetoothDevice) instead.
        throw new UnsupportedOperationException("This method should not be called");
    }

    @Override
    public void onBatteryValueReceived(final BluetoothDevice device, final int value) {
        // empty default implementation
    }

    @Override
    public void onError(final BluetoothDevice device, final String message, final int errorCode) {
        DebugLogger.e(TAG, "Error occurred: " + message + ",  error code: " + errorCode);
        showToast(message + " (" + errorCode + ")");
    }

    /**
     * Shows a message as a Toast notification. This method is thread safe, you can call it from any thread
     *
     * @param message a message to be shown
     */
    protected void showToast(final String message) {
        /*runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
        });*/
    }

    /**
     * Shows a message as a Toast notification. This method is thread safe, you can call it from any thread
     *
     * @param messageResId an resource id of the message to be shown
     */
    protected void showToast(final int messageResId) {
        /*runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(BleMulticonnectProfileServiceReadyActivity.this, messageResId, Toast.LENGTH_SHORT).show();
            }
        });*/
    }

    /**
     * The UUID filter is used to filter out available devices that does not have such UUID in their advertisement packet. See also:
     * {@link #isChangingConfigurations()}.
     *
     * @return the required UUID or <code>null</code>
     */
    protected UUID getFilterUUID() {
        return EmgImuManager.EMG_SERVICE_UUID;
    }

    /**
     * Returns unmodifiable list of managed devices. Managed device is a device the was selected on ScannerFragment until it's removed from the managed list.
     * It does not have to be connected at that moment.
     * @return unmodifiable list of managed devices
     */
    protected List<BluetoothDevice> getManagedDevices() {
        return Collections.unmodifiableList(mManagedDevices);
    }

    /**
     * Returns <code>true</code> if the device is connected. Services may not have been discovered yet.
     * @param device the device to check if it's connected
     */
    protected boolean isDeviceConnected(final BluetoothDevice device) {
        return mService != null && mService.isConnected(device);
    }

    /**
     * Shows the scanner fragment.
     *
     * @param filter               the UUID filter used to filter out available devices. The fragment will always show all bonded devices as there is no information about their
     *                             services
     * @see #getFilterUUID()
     */
    private void showDeviceScanningDialog(final UUID filter) {
        final ScannerFragment dialog = ScannerFragment.getInstance(filter);
        //dialog.show(getSupportFragmentManager(), "scan_fragment");
    }

    private void ensureBLESupported() {
        if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            //Toast.makeText(this, R.string.no_ble, Toast.LENGTH_LONG).show();
            //finish();
        }
    }

    protected boolean isBLEEnabled() {
        final BluetoothManager bluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothAdapter adapter = bluetoothManager.getAdapter();
        return adapter != null && adapter.isEnabled();
    }

    protected void showBLEDialog() {
        //final Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        //startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
    }

    @Override
    public void onEmgBuffReceived(BluetoothDevice device, int count, double[][] data) {

    }

    @Override
    public void onEmgRawReceived(BluetoothDevice device, int value) {

    }

    @Override
    public void onEmgPwrReceived(BluetoothDevice device, int value) {
        android.util.Log.d(TAG, "Update");
        if  (mCallbacks != null) {
            mCallbacks.onEmgPwrReceived(device, value);
        }
    }

    @Override
    public void onEmgClick(BluetoothDevice device) {
        android.util.Log.d(TAG, "Click");
        if  (mCallbacks != null) {
            mCallbacks.onEmgClick(device);
        }
    }

    @Override
    public void onEmgLogRecordReceived(BluetoothDevice device, EmgLogRecord record) {

    }

    @Override
    public void onOperationStarted(BluetoothDevice device) {

    }

    @Override
    public void onOperationCompleted(BluetoothDevice device) {

    }

    @Override
    public void onOperationFailed(BluetoothDevice device) {

    }

    @Override
    public void onOperationAborted(BluetoothDevice device) {

    }

    @Override
    public void onOperationNotSupported(BluetoothDevice device) {

    }

    @Override
    public void onDatasetClear(BluetoothDevice device) {

    }

    @Override
    public void onNumberOfRecordsRequested(BluetoothDevice device, int value) {

    }

    public interface Callbacks {
        public void onEmgPwrReceived(final BluetoothDevice device, int value);
        public void onEmgClick(final BluetoothDevice device);
    }
    private Callbacks mCallbacks;
    public void setCallbacks(Callbacks c) { mCallbacks = c; }
}
