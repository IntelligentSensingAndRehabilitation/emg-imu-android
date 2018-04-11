package org.sralab.emgimu.service;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.support.annotation.StringRes;
import android.util.Log;

import no.nordicsemi.android.ble.utils.ILogger;
import no.nordicsemi.android.log.ILogSession;
import no.nordicsemi.android.log.LogContract;
import no.nordicsemi.android.log.Logger;
import no.nordicsemi.android.nrftoolbox.profile.multiconnect.IDeviceLogger;

// implement both a nRF logging session for the service (not associated with a device)
// and allow mirroring this output to logcat. later this may be mirrored or replace
// with firebase logging. Individual devices also contain their own nRF logs, but that
// calling path should not end up here. Any logging events passed to the service binder
// associated with the device will go to the specific device logger and this one.

public class ServiceLogger implements ILogger, IDeviceLogger {

    private ILogSession mLogSession;
    private String mTag;
    private Context mContext;

    public ServiceLogger(String tag, Context context, ILogSession session) {
        mTag = tag;
        mLogSession = session;
        mContext = context;
    }

    // map from nordic log levels to
    private void logcatLog(int level, String msg) {
        switch(level) {
            case LogContract.Log.Level.DEBUG:
                Log.d(mTag, msg);
                break;
            case LogContract.Log.Level.WARNING:
                Log.w(mTag, msg);
                break;
            case LogContract.Log.Level.INFO:
            case LogContract.Log.Level.APPLICATION:
                Log.i(mTag, msg);
                break;
            case LogContract.Log.Level.VERBOSE:
                Log.v(mTag, msg);
                break;
            case LogContract.Log.Level.ERROR:
                Log.e(mTag, msg);
                break;
            default:
                Log.e(mTag, msg + " reported with unknown logging level: " + level);
        }
    }

    @Override
    public void log(int level, @StringRes final int messageRes, Object... params) {
        logcatLog(level, mContext.getString(messageRes));
        Logger.log(mLogSession, level, messageRes, params);
    }

    public void log(int level, String message) {
        logcatLog(level, message);
        Logger.log(mLogSession, level, message);
    };

    @Override
    public void log(BluetoothDevice device, int level, String message) {
        logcatLog(level, device.getAddress() + " : " + message);
        Logger.log(mLogSession, level, device.getAddress() + " : " + message);
    }

    @Override
    public void log(BluetoothDevice device, int level,  @StringRes final int messageRes, Object... params) {
        logcatLog(level, device.getAddress() + " : " + mContext.getString(messageRes));
        Logger.log(mLogSession, level, messageRes, params);
    }

    /***** helper methods to make an android Logger style interface *****/
    public void d(String message) { log(LogContract.Log.Level.DEBUG, message); }
    public void i(String message) { log(LogContract.Log.Level.INFO, message); }
    public void w(String message) { log(LogContract.Log.Level.WARNING, message); }
    public void e(String message) { log(LogContract.Log.Level.ERROR, message); }

}