package org.sralab.emgimu;

import android.bluetooth.BluetoothDevice;
import android.util.Log;
import android.view.View;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;

import org.sralab.emgimu.service.BuildConfig;
import org.sralab.emgimu.service.EmgImuService;

import java.util.List;

import io.fabric.sdk.android.Fabric;
import no.nordicsemi.android.nrftoolbox.widget.DividerItemDecoration;

public abstract class EmgImuAdapterActivity extends EmgImuBaseActivity {

    static final private String TAG = EmgImuAdapterActivity.class.getSimpleName();

    RecyclerView mDevicesView;
    DeviceAdapter mAdapter;
    EmgImuService.EmgImuBinder mService;

    public EmgImuService.EmgImuBinder getService() {
        return mService;
    }

    protected void onCreateView(DeviceAdapter adapter, final RecyclerView view) {
        CrashlyticsCore crashlyticsCore = new CrashlyticsCore.Builder()
                .disabled(BuildConfig.DEBUG)
                .build();
        Fabric.with(this, new Crashlytics.Builder().core(crashlyticsCore).build());

        mAdapter = adapter;

        mDevicesView = view;
        mDevicesView.setLayoutManager(new LinearLayoutManager(this));
        mDevicesView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));
    }

    @Override
    protected void onServiceBinded(EmgImuService.EmgImuBinder binder) {
        Log.d(TAG, "onServiceBinded");
        mService = binder;
        mAdapter.setService(binder);
        mDevicesView.setAdapter(mAdapter);
    }

    @Override
    protected void onServiceUnbinded() {
        mDevicesView.setAdapter(null);
    }

    @Override
    public void onDeviceConnecting(final BluetoothDevice device) {
        Log.d(TAG, "onDeviceConnecting");
        super.onDeviceConnecting(device);
        //mAdapter.onDeviceAdded(device);
    }

    @Override
    public void onDeviceConnected(final BluetoothDevice device) {
        mAdapter.onDeviceStateChanged(device);

        // Is previously connected device might be ready and this event won't fire
        if (mService != null && mService.isReady(device)) {
            onDeviceReady(device);
        } else if (mService == null) {
            Log.w(TAG, "Probable race condition");
        }
    }

    public void onDeviceReady(final BluetoothDevice device) {
        super.onDeviceReady(device);
        mAdapter.onDeviceReady(device);
    }

    public void onDeviceDisconnecting(final BluetoothDevice device) {
        super.onDeviceDisconnecting(device);
        mAdapter.onDeviceStateChanged(device);
    }

    public void onDeviceDisconnected(final BluetoothDevice device, int reason) {
        mAdapter.onDeviceRemoved(device);
    }

    public void onDeviceNotSupported(final BluetoothDevice device) {
        super.onDeviceNotSupported(device);
        mAdapter.onDeviceRemoved(device);
    }

    public void onBatteryReceived(BluetoothDevice device, float battery) {
        mAdapter.onBatteryReceived(device, battery);
    }

    public void onEmgBuffReceived(BluetoothDevice device, long ts_ms, double[][] data) {
        mAdapter.onEmgBuffReceived(device, ts_ms, data);
    }

    public void onImuAccelReceived(BluetoothDevice device, float[][] accel) {
        mAdapter.onImuAccelReceived(device, accel);
    }

    public void onImuGyroReceived(BluetoothDevice device, float[][] gyro) {
        mAdapter.onImuGyroReceived(device, gyro);
    }

    public void onImuMagReceived(BluetoothDevice device, float[][] gyro) {
        mAdapter.onImuMagReceived(device, gyro);
    }

    public void onImuAttitudeReceived(BluetoothDevice device, float[] quaternion) {
        mAdapter.onImuAttitudeReceived(device, quaternion);
    }

    @Override
    protected int getAboutTextId() {
        return 0;
    }


    public static abstract class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {

        EmgImuService.EmgImuBinder mService;
        List<BluetoothDevice> mDevices;

        public EmgImuService.EmgImuBinder getService() {
            return mService;
        }

        public List<BluetoothDevice> getDevices() {
            return mDevices;
        }

        public void setService(final EmgImuService.EmgImuBinder binder) {
            mService = binder;
            mDevices = mService.getManagedDevices();
        }

        public class DefaultItemAnimatorNoChange extends DefaultItemAnimator {
            public DefaultItemAnimatorNoChange() {
                setSupportsChangeAnimations(false);
            }
        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            // This is important to make sure there are not two overlapping views for the
            // same element that the RecycleView tries to use for smoother updates. It
            // breaks the animation coming from the graph element
            recyclerView.setItemAnimator(new DefaultItemAnimatorNoChange());
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, final int position) {
            final BluetoothDevice d = mDevices.get(position);
            holder.bind(d);
        }

        @Override
        public int getItemCount() {
            return mDevices.size();
        }

        @Override
        public void onViewRecycled(final ViewHolder holder) {
        }

        //! Get the row in the adapters for the first channel of this device
        private int getPosition(final BluetoothDevice device) {
            int pos = 0;
            for (final BluetoothDevice d : mDevices) {
                if (device == d)
                    return pos;
                pos++;
            }
            return -1;
        }

        void onDeviceAdded(final BluetoothDevice device) {
            final int position = getPosition(device);
            if (position == -1) {
                notifyItemInserted(mDevices.size() - 1);
            } else {
                // This may happen when Bluetooth adapter was switched off and on again
                // while there were devices on the list.
                notifyItemChanged(position);
            }
        }

        public void onDeviceReady(final BluetoothDevice device) {
            final int position = getPosition(device);
            if (position == -1) {
                notifyItemInserted(mDevices.size() - 1);
            } else {
                // This may happen when Bluetooth adapter was switched off and on again
                // while there were devices on the list.
                notifyItemChanged(position);
            }
        }

        void onDeviceRemoved(final BluetoothDevice device) {
            notifyDataSetChanged(); // we don't have position of the removed device here
        }

        void onDeviceStateChanged(final BluetoothDevice device) {
            final int position = getPosition(device);
            if (position >= 0)
                notifyItemChanged(position);
        }

        public void onBatteryReceived(BluetoothDevice device, float battery) {}

        public void onEmgBuffReceived(BluetoothDevice device, long ts_ms, double[][] data) {}

        public void onImuAccelReceived(BluetoothDevice device, float[][] accel) {}

        public void onImuGyroReceived(BluetoothDevice device, float[][] gyro) {}

        public void onImuMagReceived(BluetoothDevice device, float[][] gyro) {}

        public void onImuAttitudeReceived(BluetoothDevice device, float[] quaternion) {}


        public class ViewHolder extends RecyclerView.ViewHolder {

            private BluetoothDevice mDev;

            public ViewHolder(final View itemView) {
                super(itemView);
            }

            public void bind(final BluetoothDevice device) {
                mDev = device;
            }

            public BluetoothDevice getDevice() {
                return mDev;
            }


        }
    }
}
