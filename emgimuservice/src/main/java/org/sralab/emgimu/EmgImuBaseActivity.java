package org.sralab.emgimu;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import org.jetbrains.annotations.NotNull;
import org.sralab.emgimu.service.EmgImuService;
import org.sralab.emgimu.service.IEmgImuServiceBinder;

import android.Manifest;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.sralab.emgimu.service.EmgImuService;
import org.sralab.emgimu.service.IEmgImuServiceBinder;

public abstract class EmgImuBaseActivity extends AppCompatActivity {

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
            onServiceBinded(mService);
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            mService = null;
        }
    };

    private static final int REQUEST_CODE_PERMISSIONS = 10;

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

    /**
    * Called when activity unbinds from the service. You may no longer use this binder methods.
    */
    protected abstract void onServiceUnbinded();

    protected Class<? extends EmgImuService> getServiceClass() {
        return EmgImuService.class;
    }

    private boolean hasPermissions(Context context, String... permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private String[] requiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS};
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION};
        } else {
            return new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN};
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.length > 0) {
                boolean allPermissionsGranted = true;
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        allPermissionsGranted = false;
                        break;
                    }
                }
                if (allPermissionsGranted) {
                    // Permissions granted, proceed with initializing Bluetooth operations
                    initializeBluetooth();
                } else {
                    // Permissions denied, show a message to the user
                    Toast.makeText(this, "Bluetooth permissions are required for this app to function.", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void initializeBluetooth() {
        // Initialize Bluetooth-related operations here
    }

}
