package org.sralab.emgimu.imu_calibration;

import android.bluetooth.BluetoothDevice;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.sralab.emgimu.service.EmgImuService;

import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {

    private final String TAG = DeviceAdapter.class.getSimpleName();

    private final EmgImuService.EmgImuBinder mService;
    private final List<BluetoothDevice> mDevices;

    public DeviceAdapter(final EmgImuService.EmgImuBinder binder) {
        mService = binder;
        mDevices = mService.getManagedDevices();
    }

    @Override
    public ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {

        final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.view_calibration, parent, false);
        int height = parent.getMeasuredHeight();

        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.height = height;
        view.setLayoutParams(params);

        return new ViewHolder(view);
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
        //holder.mLayoutView.removeAllViews();
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

    void onDeviceRemoved(final BluetoothDevice device) {
        notifyDataSetChanged(); // we don't have position of the removed device here
    }

    void onDeviceStateChanged(final BluetoothDevice device) {
        final int position = getPosition(device);
        if (position >= 0)
            notifyItemChanged(position);
    }

    void onDeviceReady(final BluetoothDevice device) {
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private ViewGroup mLayoutView;


        ViewHolder(final View itemView) {
            super(itemView);

            //mLayoutView = itemView.findViewById(R.id.view_calibration);
        }

        private void bind(final BluetoothDevice device) {
        }
    }
}
