/*
 * Copyright (c) 2015, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.sralab.emgimu.service;

import static android.bluetooth.BluetoothGattCharacteristic.FORMAT_UINT8;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import org.sralab.emgimu.logging.FirebaseEmgLogger;
import org.sralab.emgimu.logging.FirebaseStreamLogger;
import org.sralab.emgimu.parser.RecordAccessControlPointParser;
import org.sralab.emgimu.service.firebase.FirebaseMagCalibration;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.InvalidParameterException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import no.nordicsemi.android.ble.BleManager;
import no.nordicsemi.android.ble.ConnectionPriorityRequest;
import no.nordicsemi.android.ble.PhyRequest;
import no.nordicsemi.android.ble.RequestQueue;
import no.nordicsemi.android.ble.data.Data;
import no.nordicsemi.android.ble.data.MutableData;

public class EmgImuManager extends BleManager {
    private final String TAG = "EmgImuManager";

    /**
     * Device Information UUID
     **/
    private final static UUID DEVICE_INFORMATION_SERVICE = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb");
    private final static UUID MANUFACTURER_NAME_CHARACTERISTIC = UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb");
    private final static UUID SERIAL_NUMBER_CHARACTERISTIC = UUID.fromString("00002A25-0000-1000-8000-00805f9b34fb");
    private final static UUID HARDWARE_REVISION_CHARACTERISTIC = UUID.fromString("00002A27-0000-1000-8000-00805f9b34fb");
    private final static UUID FIRMWARE_REVISION_CHARACTERISTIC = UUID.fromString("00002A26-0000-1000-8000-00805f9b34fb");

    private BluetoothGattCharacteristic mManufacturerCharacteristic, mSerialNumberCharacteristic, mHardwareCharacteristic, mFirmwareCharacteristic;

    /**
     * Battery information
     **/
    private final static UUID BATTERY_SERVICE = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb");
    private final static UUID BATTERY_LEVEL_CHARACTERISTIC = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb");
    private BluetoothGattCharacteristic mBatteryCharacteristic;

    /**
     * EMG Service UUID
     **/
    public final static UUID EMG_SERVICE_UUID = UUID.fromString("00001234-1212-EFDE-1523-785FEF13D123");
    public final static UUID EMG_BUFF_CHAR_UUID = UUID.fromString("00001236-1212-EFDE-1523-785FEF13D123");
    public final static UUID EMG_PWR_CHAR_UUID = UUID.fromString("00001237-1212-EFDE-1523-785FEF13D123");

    /**
     * IMU Service UUID
     **/
    public final static UUID IMU_SERVICE_UUID = UUID.fromString("00002234-1212-EFDE-1523-785FEF13D123");
    public final static UUID IMU_ACCEL_CHAR_UUID = UUID.fromString("00002235-1212-EFDE-1523-785FEF13D123");
    public final static UUID IMU_GYRO_CHAR_UUID = UUID.fromString("00002236-1212-EFDE-1523-785FEF13D123");
    public final static UUID IMU_MAG_CHAR_UUID = UUID.fromString("00002237-1212-EFDE-1523-785FEF13D123");
    public final static UUID IMU_ATTITUDE_CHAR_UUID = UUID.fromString("00002238-1212-EFDE-1523-785FEF13D123");
    public final static UUID IMU_CALIBRATION_CHAR_UUID = UUID.fromString("00002239-1212-EFDE-1523-785FEF13D123");

    /**
     * FORCE UUID
     **/
    public final static UUID FORCE_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    public final static UUID FORCE_WRITE_CHAR_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");


    private final float EMG_FS = 2000.0f;
    private final int EMG_BUFFER_LEN = (40 / 2); // elements in UINT16

    private BluetoothGattCharacteristic mEmgBuffCharacteristic, mEmgPwrCharacteristic;
    private BluetoothGattCharacteristic mImuAccelCharacteristic, mImuGyroCharacteristic, mImuMagCharacteristic, mImuAttitudeCharacteristic;
    private BluetoothGattCharacteristic mImuCalibrationCharacteristic;
    private BluetoothGattCharacteristic mForceCharacteristic;

    /**
     * Record Access Control Point characteristic UUID
     */
    private final static UUID EMG_RACP_CHAR_UUID = UUID.fromString("00002a52-1212-EFDE-1523-785FEF13D123");
    private final static UUID EMG_LOG_CHAR_UUID = UUID.fromString("00001240-1212-EFDE-1523-785FEF13D123");

    private final static byte OP_CODE_REPORT_STORED_RECORDS = 1;
    //private final static byte OP_CODE_DELETE_STORED_RECORDS = 2;
    private final static byte OP_CODE_ABORT_OPERATION = 3;
    private final static byte OP_CODE_REPORT_NUMBER_OF_RECORDS = 4;
    private final static byte OP_CODE_NUMBER_OF_STORED_RECORDS_RESPONSE = 5;
    private final static byte OP_CODE_RESPONSE_CODE = 6;
    private final static byte OP_CODE_SET_TIMESTAMP = 7;
    private final static byte OP_CODE_SET_TIMESTAMP_COMPLETE = 8;

    private final static byte OPERATOR_NULL = 0;
    private final static byte OPERATOR_ALL_RECORDS = 1;
    //private final static byte OPERATOR_LESS_THEN_OR_EQUAL = 2;
    //private final static byte OPERATOR_GREATER_THEN_OR_EQUAL = 3;
    //private final static byte OPERATOR_WITHING_RANGE = 4;
    //private final static byte OPERATOR_FIRST_RECORD = 5;
    //private final static byte OPERATOR_LAST_RECORD = 6;

    private final int BLE_MSG_HEADER_SIZE = 6; // Used for 2-channel power parsing.

    /**
     * The filter type is used for range operators ({@link #OPERATOR_LESS_THEN_OR_EQUAL}, {@link #OPERATOR_GREATER_THEN_OR_EQUAL}, {@link #OPERATOR_WITHING_RANGE}.<br/>
     * The syntax of the operand is: [Filter Type][Minimum][Maximum].<br/>
     * This filter selects the records by the sequence number.
     */
    //private final static int FILTER_TYPE_SEQUENCE_NUMBER = 1;
    /**
     * The filter type is used for range operators ({@link #OPERATOR_LESS_THEN_OR_EQUAL}, {@link #OPERATOR_GREATER_THEN_OR_EQUAL}, {@link #OPERATOR_WITHING_RANGE}.<br/>
     * The syntax of the operand is: [Filter Type][Minimum][Maximum].<br/>
     * This filter selects the records by the user facing time (base time + offset time).
     */
    //private final static int FILTER_TYPE_USER_FACING_TIME = 2;
    private final static int RESPONSE_SUCCESS = 1;
    private final static int RESPONSE_OP_CODE_NOT_SUPPORTED = 2;
    //private final static int RESPONSE_INVALID_OPERATOR = 3;
    //private final static int RESPONSE_OPERATOR_NOT_SUPPORTED = 4;
    //private final static int RESPONSE_INVALID_OPERAND = 5;
    private final static int RESPONSE_NO_RECORDS_FOUND = 6;
    private final static int RESPONSE_ABORT_UNSUCCESSFUL = 7;
    private final static int RESPONSE_PROCEDURE_NOT_COMPLETED = 8;
    //private final static int RESPONSE_OPERAND_NOT_SUPPORTED = 9;

    private FirebaseEmgLogger fireLogger;
    private FirebaseStreamLogger streamLogger;
    private List<EmgLogRecord> mRecords = new ArrayList<>();

    private BluetoothGattCharacteristic mRecordAccessControlPointCharacteristic, mEmgLogCharacteristic;

    //! Data relating to logging to FireBase
    private boolean mLogging = true;

    private String mManufacturer;
    private String mHardwareRevision;
    private String mFirmwareRevision;
    private int mChannels;

    // region Fields for Callback Section
    private List<IEmgImuPwrDataCallback> emgPwrCbs = new ArrayList<>();
    private List<IEmgImuSenseCallback> imuAccelCbs = new ArrayList<>();
    private List<IEmgImuSenseCallback> imuGyroCbs = new ArrayList<>();
    private List<IEmgImuSenseCallback> imuMagCbs = new ArrayList<>();
    private List<IEmgImuQuatCallback> imuQuatCbs = new ArrayList<>();
    private List<IEmgImuBatCallback> batCbs = new ArrayList<>();
    private List<IEmgImuStreamDataCallback> emgStreamCbs = new ArrayList<>();
    // endregion

    // region Register/Unregister Callbacks Section
    public void registerEmgPwrCallback(IEmgImuPwrDataCallback callback)
    {
        /* Do not duplicate the callback. */
        if (emgPwrCbs.contains(callback)) {
            return;
        }
        emgPwrCbs.add(callback);
        /*
        *  Wait until the sensor is ready, then enable the notification.
        *  The notification allows the sensor to actually start streaming the data.
        *  There are 2 pathways to enable the notifications:
        *   (1) through the game: Bridge-->Service-->Manager (here)
        *   (2) through the Config app: ViewModel-->Manager & Service
        */
        if (isReady()) {
            enableEmgPwrNotifications();
        }
    }

    public void unregisterEmgPwrCallback(IEmgImuPwrDataCallback callback)
    {
        /* Order here is important: disable the notification first, then remove it from the list. */
        disableEmgPwrNotifications();
        if (!emgPwrCbs.isEmpty()) {
            emgPwrCbs.remove(callback);
        }
    }

    public void registerEmgStreamCallback(IEmgImuStreamDataCallback callback)
    {
        if(emgStreamCbs.contains(callback)) {
            return;
        }
        emgStreamCbs.add(callback);
        if (isReady()) {
            enableEmgBuffNotifications(); // assuming that EmgBuff pairs with emgStreamCbs
        }
    }

    public void unregisterEmgStreamCallback(IEmgImuStreamDataCallback callback)
    {
        disableEmgBuffNotifications(); // assuming that EmgBuff pairs with emgStreamCbs
        if(!emgStreamCbs.isEmpty()) {
            emgStreamCbs.remove(callback);
        }
    }

    public void registerImuAccelCallback(IEmgImuSenseCallback callback)
    {
        if(imuAccelCbs.contains(callback)) {
            return;
        }
        imuAccelCbs.add(callback);
        if (isReady()) {
            enableAccelNotifications();
        }
    }

    public void unregisterImuAccelCallback(IEmgImuSenseCallback callback)
    {
        disableAccelNotifications();
        if(!imuAccelCbs.isEmpty()) {
            imuAccelCbs.remove(callback);
        }
    }

    public void registerImuGyroCallback(IEmgImuSenseCallback callback)
    {
        if(imuGyroCbs.contains(callback)) {
            return;
        }
        imuGyroCbs.add(callback);
        if (isReady()) {
            enableGyroNotifications();
        }
    }

    public void unregisterImuGyroCallback(IEmgImuSenseCallback callback)
    {
        disableGyroNotifications();
        if(!imuGyroCbs.isEmpty()) {
            imuGyroCbs.remove(callback);
        }
    }

    public void registerImuMagCallback(IEmgImuSenseCallback callback)
    {
        if(imuMagCbs.contains(callback)) {
            return;
        }
        imuMagCbs.add(callback);
        if (isReady()) {
            enableMagNotifications();
        }
    }

    public void unregisterImuMagCallback(IEmgImuSenseCallback callback)
    {
        disableMagNotifications();
        if(!imuMagCbs.isEmpty()) {
            imuMagCbs.remove(callback);
        }
    }

    public void registerImuQuatCallback(IEmgImuQuatCallback callback)
    {
        if(imuQuatCbs.contains(callback)) {
            return;
        }
        imuQuatCbs.add(callback);
        if (isReady()) {
            enableAttitudeNotifications(); // assuming Quaternion pairs with Attitude
        }
    }

    public void unregisterImuQuatCallback(IEmgImuQuatCallback callback)
    {
        disableAttitudeNotifications(); // assuming Quaternion pairs with Attitude
        if(!imuQuatCbs.isEmpty()) {
            imuQuatCbs.remove(callback);
        }
    }

    public void registerBatCallback(IEmgImuBatCallback callback)
    {
        if(batCbs.contains(callback)) {
            return;
        }
        batCbs.add(callback);
        if (isReady()) {
            setNotificationCallback(mBatteryCharacteristic);
        }
    }

    public void unregisterBatCallback(IEmgImuBatCallback callback)
    {
        disableNotifications(mBatteryCharacteristic);
        if(!batCbs.isEmpty()) {
            batCbs.remove(callback);
        }
    }
    // endregion

    public EmgImuManager(final Context context) {
		super(context);
        mSynced = false;
        mFetchRecords = false;
	}

    @NonNull
	@Override
	protected BleManagerGattCallback getGattCallback()
    {
		return new EmgImuManagerGattCallback();
	}

    public void close() {
	    super.close();

        if (mLogging && streamLogger != null) {
            Log.d(TAG, "Closing stream logger: " + getAddress());
            streamLogger.close();
            streamLogger = null;
        }
    }

	/**
	 * BluetoothGatt callbacks for connection/disconnection, service discovery, receiving indication, etc
	 */
	private class EmgImuManagerGattCallback extends BleManagerGattCallback {

        @Override
        protected void initialize() {
            super.initialize();

            log(Log.INFO, "Initializing connection");

            RequestQueue initQueue = beginAtomicRequestQueue()
                    .add(readCharacteristic(mHardwareCharacteristic)
                        .with((device, data) -> {
                            mHardwareRevision = data.getStringValue(0);
                            log(Log.INFO, "Hardware revision: " + mHardwareRevision);
                            if (mHardwareRevision.equals("v0.3")) {
                                mChannels = 8;
                                log(Log.INFO, "Setting channels to 8");
                            } else if (mHardwareRevision.equals("v0.7")) {
                                mChannels = 2;
                                log(Log.INFO, "Setting channels to 2");
                            } else
                                mChannels = 1;
                        }))
                    .add(readCharacteristic(mManufacturerCharacteristic)
                        .with((device, data) -> mManufacturer = data.getStringValue(0))
                        .fail((device, status) -> Log.d(TAG, "Could not read manufacturer (" + status + ")")))
                    .add(readCharacteristic(mFirmwareCharacteristic)
                        .with((device, data) -> mFirmwareRevision = data.getStringValue(0))
                        .fail((device, status) -> log(Log.WARN, "Unable to read firmware version: " + status)))
                    /*.add(readCharacteristic(mSerialNumberCharacteristic)
                        .with((device, data) -> log(Log.INFO, "Serial number; " + data.getStringValue(0)))) */
                    .done(device -> log(Log.INFO, "Target initialized. Hardware: " + mHardwareRevision + " Firmware: " + mFirmwareRevision))
                    .add(requestMtu(517)
                            .with((device, mtu) -> log(Log.INFO, "MTU set to " + mtu))
                            .fail((device, status) -> log(Log.WARN, "Requested MTU not supported: " + status)))
                    .add(requestConnectionPriority(ConnectionPriorityRequest.CONNECTION_PRIORITY_HIGH)
                            .fail((device, status) -> log(Log.WARN, "Failed to set connection priority: " + status)));

            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

            if (!adapter.isLe2MPhySupported()) {
                log(Log.ERROR, "2M PHY not supported!");
            } else {
                initQueue.add(setPreferredPhy(PhyRequest.PHY_LE_2M_MASK, PhyRequest.PHY_LE_2M_MASK, PhyRequest.PHY_OPTION_NO_PREFERRED)
                        .fail((device, status) -> log(Log.WARN, "Request PHY failed: " + status))
                        .done(device -> log(Log.WARN, "Setting PHY succeeded")));
            }
            if (!adapter.isLeExtendedAdvertisingSupported()) {
                log(Log.ERROR, "LE Extended Advertising not supported!");
            }
            initQueue.done(device -> syncDevice());
            initQueue.enqueue();

            setNotificationCallback(mBatteryCharacteristic).with((device ,data) -> parseBattery(device, data));
            enableNotifications(mBatteryCharacteristic)
                    .done(device -> log(Log.DEBUG, "Battery characteristic notification enabled"))
                    .fail((d, status) -> log(Log.DEBUG, "Failed to enable battery characteristic notification "))
                    .enqueue();
            readCharacteristic(mBatteryCharacteristic).with((device, data) -> parseBattery(device, data))
                    .enqueue();

            if(mForceCharacteristic != null) {
                enableNotifications(mForceCharacteristic)
                        .before(device -> setNotificationCallback(mForceCharacteristic).with((_device, data) -> parseForceMeterBLECharacteristic(_device, data)))
                        .done(device -> log(Log.DEBUG, "Force characteristic notification enabled"))
                        .fail((d, status) -> log(Log.DEBUG, "Failed to enable force characteristic notification"))
                        .enqueue();
            }

        }

		boolean isDeviceInfoServiceSupported(final BluetoothGatt gatt) {
            final BluetoothGattService deviceInfoService = gatt.getService(DEVICE_INFORMATION_SERVICE);
            if (deviceInfoService != null) {
                mManufacturerCharacteristic = deviceInfoService.getCharacteristic(MANUFACTURER_NAME_CHARACTERISTIC);
                mSerialNumberCharacteristic = deviceInfoService.getCharacteristic(SERIAL_NUMBER_CHARACTERISTIC);
                mHardwareCharacteristic = deviceInfoService.getCharacteristic(HARDWARE_REVISION_CHARACTERISTIC);
                mFirmwareCharacteristic = deviceInfoService.getCharacteristic(FIRMWARE_REVISION_CHARACTERISTIC);
            } else {
                return false;
            }
            log(Log.INFO, "Device information characteristics for service " + deviceInfoService.getUuid());

            for (BluetoothGattCharacteristic c : deviceInfoService.getCharacteristics() ) {
                log(Log.INFO, "Device information found: " + c.getUuid());
            }

            return  (mManufacturerCharacteristic != null) &&
                    (mSerialNumberCharacteristic != null) &&
                    (mHardwareCharacteristic != null) &&
                    (mFirmwareCharacteristic != null);
        }

        boolean isBatteryServiceSupported(final BluetoothGatt gatt) {
            final BluetoothGattService batteryService = gatt.getService(BATTERY_SERVICE);
            if (batteryService != null) {
                mBatteryCharacteristic = batteryService.getCharacteristic(BATTERY_LEVEL_CHARACTERISTIC);
            } else {
                return false;
            }

            return  mBatteryCharacteristic != null;
        }

        @Override
        protected boolean isRequiredServiceSupported(final BluetoothGatt gatt) {
            return true;
        }

        @Override
        protected boolean isOptionalServiceSupported(final BluetoothGatt gatt) {
            final BluetoothGattService llService = gatt.getService(EMG_SERVICE_UUID);
            if (llService != null) {
                mEmgBuffCharacteristic = llService.getCharacteristic(EMG_BUFF_CHAR_UUID);
                mEmgPwrCharacteristic  = llService.getCharacteristic(EMG_PWR_CHAR_UUID);

                log(Log.INFO, "Characteristics for service " + llService.getUuid());
                for (BluetoothGattCharacteristic c : llService.getCharacteristics() ) {
                    log(Log.INFO, "Found: " + c.getUuid());
                }
            }

            boolean deviceInfoSupported = isDeviceInfoServiceSupported(gatt);
            if (!deviceInfoSupported)
                log(Log.ERROR, "Device info is not supported");

            boolean batterySupported = isBatteryServiceSupported(gatt);
            if (!batterySupported)
                log(Log.ERROR, "Battery is not supported");

            // Determine if logging is supported
            if (llService != null) {
                mRecordAccessControlPointCharacteristic = llService.getCharacteristic(EMG_RACP_CHAR_UUID);
                mEmgLogCharacteristic = llService.getCharacteristic(EMG_LOG_CHAR_UUID);
            }
            boolean supportsLogging = (mEmgLogCharacteristic != null) && (mRecordAccessControlPointCharacteristic != null);

            // IMU
            final BluetoothGattService iaService = gatt.getService(IMU_SERVICE_UUID);
            if (iaService != null) {
                mImuAccelCharacteristic = iaService.getCharacteristic(IMU_ACCEL_CHAR_UUID);
                mImuGyroCharacteristic = iaService.getCharacteristic(IMU_GYRO_CHAR_UUID);
                mImuMagCharacteristic = iaService.getCharacteristic(IMU_MAG_CHAR_UUID);
                mImuAttitudeCharacteristic = iaService.getCharacteristic(IMU_ATTITUDE_CHAR_UUID);
                mImuCalibrationCharacteristic = iaService.getCharacteristic(IMU_CALIBRATION_CHAR_UUID);
                log(Log.INFO, "IMU Service Detected!");
            }
            boolean supportsImu = mImuAccelCharacteristic != null &&
                    mImuGyroCharacteristic != null &&
                    mImuMagCharacteristic != null &&
                    mImuAttitudeCharacteristic != null;


            // FORCE
            final BluetoothGattService forceService = gatt.getService(FORCE_SERVICE_UUID);
            if (forceService != null) {
                log(Log.INFO, "Force Service Detected!");
                mForceCharacteristic = forceService.getCharacteristic(FORCE_WRITE_CHAR_UUID);
            } else {
                log(Log.INFO, "Force Service NOT detected!");
            }
            return true;
        }

        @Override
        public void onDeviceReady() {
            //Log.d(TAG, "emgPwr onDeviceReady() got called");
		    // Complete some of our initialization once we have a device connected
            fireLogger = new FirebaseEmgLogger(EmgImuManager.this);

            if (mLogging) {
                log(Log.INFO, "Created stream logger");
                streamLogger = new FirebaseStreamLogger(EmgImuManager.this, getContext());
            }

            connectionState.postValue(getConnectionState());

            if (!emgPwrCbs.isEmpty()) {
                enableEmgPwrNotifications();
            }

            if(!emgStreamCbs.isEmpty()) {
                enableEmgBuffNotifications();
            }

            if (!imuAccelCbs.isEmpty()) {
                enableAccelNotifications();
            }

            if (!imuGyroCbs.isEmpty() ) {
                enableGyroNotifications();
            }

            if (!imuMagCbs.isEmpty()) {
                enableMagNotifications();
            }

            if (!imuQuatCbs.isEmpty()) {
                enableAttitudeNotifications();
            } else

            super.onDeviceReady();
        }

        @Override
		protected void onServicesInvalidated() {
            log(Log.INFO, "onServicesInvalidated");

            connectionState.postValue(getConnectionState());

		    // Clear the Device Information characteristics
            mManufacturerCharacteristic = null;
            mSerialNumberCharacteristic = null;
            mHardwareCharacteristic = null;
            mFirmwareCharacteristic = null;

            // Clear the IMU characteristics
            mImuAccelCharacteristic = null;
            mImuGyroCharacteristic = null;
            mImuMagCharacteristic = null;
            mImuAttitudeCharacteristic = null;
            mImuCalibrationCharacteristic = null;

            // Clear the EMG characteristics
            mEmgPwrCharacteristic = null;
            mEmgBuffCharacteristic = null;
            mEmgLogCharacteristic = null;
            mRecordAccessControlPointCharacteristic = null;

            mChannels = 0;

            if (mLogging && streamLogger != null) {
                log(Log.INFO, "Closing stream logger: " + getAddress());
                streamLogger.close();
                streamLogger = null;
            }
		}

    };

	public MutableLiveData<Integer> connectionState = new MutableLiveData<>(BluetoothGatt.STATE_DISCONNECTED);
	public LiveData<Integer> getConnectionLiveState() { return connectionState; }

    class TimestampResolved {

        int last_counter;
        long last_sensor_timestamp;

        float sensor_Fs;
        float rtc_Fs;

        double delta = 0;
        boolean updated = false;
        int alias = 65536;

        String name;

        public TimestampResolved(float Fs, String name) {
            this.sensor_Fs = Fs;
            rtc_Fs = 8.0f;
            updated = false;
            this.name = name;
        }

        public TimestampResolved setAlias(int alias) {
            this.alias = alias;
            return this;
        }

        public long resolveTime(int counter, long timestamp, int samples) {

            float counter_timestamp = counter / (sensor_Fs / samples);
            if (!updated) {
                updated = true;
                this.delta = new Date().getTime() - (long) (counter_timestamp * 1000.0f);
            }

            long counter_diff = counter - last_counter;
            if (counter_diff > 1) {
                Log.d(TAG, this.name + " Missed sample " + counter + " " + last_counter);
            } else if (counter_diff < 0) {
                Log.d(TAG, this.name + " Wraparound. " + this.alias + " " + counter);
                this.delta = this.delta + this.alias / (sensor_Fs / samples) * 1000.0f;
            }
            last_counter = counter;

            long final_timestamp = (long) (this.delta + counter_timestamp * 1000.0f);
            long real_delta = new Date().getTime() - final_timestamp;
            if (real_delta > 200 || real_delta < -200) {
                Log.e(TAG, this.name + " Significant sensor drift detected. " + final_timestamp + " " + real_delta);
                this.delta = this.delta + real_delta;
            }

            //final_timestamp = timestampToReal(timestamp);
            return final_timestamp;
        }
    }

    TimestampResolved emgPwrResolver = new TimestampResolved(EMG_FS / 20, "EmgPwr").setAlias(256);
    TimestampResolved emgStreamResolver = new TimestampResolved(EMG_FS, "EmgStream").setAlias(256);

    /**
     * @brief   This method parses the EMG power value from a Bluetooth LE message
     *          sent from wearable sensors.
     * @details This method enables several other methods, which are responsible for the following:
     *              - Plotting the power curve in the Config.
     *              - Sending the data to the database.
     *
     *          The structure of the message (format) is as follows:
     *              - Byte 0-5: header:
     *                  - Byte 0: format
     *                      - bit 0-3: reserved
     *                      - bit 4-7: number of channels
     *                  - Byte 1: packet counter
     *                  - Byte 2-5: timestamp
     *                  - Byte 6-onward: power data for each channel, each channel takes 2-bytes
     *                    of data, 16-bit resolution starting with LSB in it's first byte
     * @note    The following are 2 examples of data contained in the characteristic parameter.
     *              Example 1, when the sensor sends 1 channel of data, the total message will contain:
     *              - Byte 0-5: header
     *              - Byte 6: channel 0 LSB
     *              - Byte 7: channel 0 MSB
     *
     *              Example 2, when the sensor sends 2 channels of data, the total message will contain:
     *              - Byte 0-5: header
     *              - Byte 6: channel 0 LSB
     *              - Byte 7: channel 0 MSB
     *              - Byte 8: channel 1 LSB
     *              - Byte 9: channel 1 MSB
     * @param device            Specific sensor connected via Bluetooth LE, recognized by it's MAC address.
     * @param characteristic    Bluetooth LE message received from the wearable sensor. See details
     *                          for format.
     */
    private void parseEmgPwr(BluetoothDevice device,  Data characteristic) {
        int expectedNumberOfChannels = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0) >> 4;
        int counter = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1);
        long timestamp = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 2);
        timestamp = timestampToReal(timestamp);
        long timestamp_ms = emgPwrResolver.resolveTime(counter, timestamp, 1);
        int[] emgPowerChannels = new int[expectedNumberOfChannels];

        // Parses the characteristic array for the emgPwr and packs it into n-dim array.
        for(int messageIndex=BLE_MSG_HEADER_SIZE, channelIndex=0; messageIndex<characteristic.size(); messageIndex+=2, channelIndex++) {
            emgPowerChannels[channelIndex] = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, messageIndex);
        }

        // Sends the data to the rest of the application.
        onEmgPwrReceived(device, timestamp_ms, expectedNumberOfChannels, emgPowerChannels);

        // Saves data to a .json log file locally on device & transmits the data to the cloud.
        if (mLogging && streamLogger != null) {
            streamLogger.addPwrSample(new Date().getTime(), timestamp, counter, emgPowerChannels);
        }
    }

    private void parseEmgBuff(final BluetoothDevice device, final Data characteristic) {
        final byte [] buffer = characteristic.getValue();
        int len = buffer.length;

        int format = buffer[0] & 0xFF;

        double microvolts_per_lsb;

        int counter = characteristic.getIntValue(FORMAT_UINT8, 1);
        long timestamp = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 2);
        timestamp = timestampToReal(timestamp);

        long buf_ts_ms = 0;

        double [][] data;
        int channels;
        int samples;

        int HDR_LEN = 6;
        switch(format) {
            // Data from single channel system
            case 16: // 1 channel of 16 bit data
                buf_ts_ms = emgStreamResolver.resolveTime(counter, timestamp, EMG_BUFFER_LEN);

                // Have to manually combine to get the endian right

                double ina333_gain = 1+(100.0 / 10.0);   // for 10k resistor
                double bandpass_gain = 10;  //# 1M / 100k
                double nrf52383_gain = 1.0 / 1.0; // more easily programmable now differential
                double analog_gain = ina333_gain * bandpass_gain;

                double lsb_per_v = analog_gain * nrf52383_gain / 0.6 * (1<<13);
                microvolts_per_lsb = 1.0e6 / lsb_per_v;

                channels = 1;
                samples = EMG_BUFFER_LEN;
                data = new double[channels][samples];

                for (int i = 0; i < samples; i++) {
                    byte [] array = {buffer[HDR_LEN + i*2], buffer[HDR_LEN + i*2+1]};

                    // check the sample counter and make sure no data was lost
                    int count = ByteBuffer.wrap(array).order(ByteOrder.LITTLE_ENDIAN).getShort();
                    data[0][i] = count * microvolts_per_lsb;
                }

                break;
            case 0x21:
            case 0x81:

                channels = format >> 4;
                samples = (characteristic.getValue().length - HDR_LEN) / 3 / channels;

                final double ads1298_gain = 8;
                final double vref = 2.42e6;
                double full_scale_range = 2 * vref / ads1298_gain;
                microvolts_per_lsb = full_scale_range / ((1<<24) - 1);

                buf_ts_ms = emgStreamResolver.resolveTime(counter, timestamp, samples);

                // representation of the data is 3 bytes per sample, 8 channels, and then N samples
                data = new double[channels][samples];
                for (int ch = 0; ch < channels; ch++) {
                    for (int sample = 0; sample < samples; sample++) {
                        int idx = HDR_LEN + 3 * (ch + channels * sample);
                        byte sign = ((buffer[idx] & 0x80) == 0x80) ? (byte) 0xff : (byte) 0x00;
                        byte [] t_array = {sign, buffer[idx], buffer[idx+1], buffer[idx+2]};
                        data[ch][sample] = ByteBuffer.wrap(t_array).order(ByteOrder.BIG_ENDIAN).getInt();
                        data[ch][sample] = microvolts_per_lsb * data[ch][sample];
                    }
                }

                break;
            default:
                log(Log.ERROR, "Unsupported data format");
                return;
        }

        if (mChannels != channels){
            log(Log.ERROR, "Current channel expected: " + mChannels);
            log(Log.ERROR, "Received: " + channels);
            throw new RuntimeException("Channel count seemed to change between calls");
        }

        onEmgStreamReceived(device, buf_ts_ms, data);

        if (mLogging && streamLogger != null) {
            streamLogger.addStreamSample(new Date().getTime(), timestamp, counter, channels, samples, data);
        }
    }

    float IMU_FS = 562.5f;
    TimestampResolved accelResolver = new TimestampResolved(IMU_FS, "Accel");
    TimestampResolved gyroResolver = new TimestampResolved(IMU_FS, "Gyro");
    TimestampResolved magResolver = new TimestampResolved(IMU_FS, "Mag");
    TimestampResolved attitudeResolver = new TimestampResolved(IMU_FS / 10.0f, "Attitude");

    private void parseImuAccel(final BluetoothDevice device, final Data characteristic) {
        int counter = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
        long timestamp = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 2);
        timestamp = timestampToReal(timestamp);

        int len = characteristic.getValue().length - 6;
        int samples = len / 6; // 6 bytes per entry

        accelResolver.resolveTime(counter, timestamp, samples);

        final float ACCEL_SCALE = 9.8f * 16.0f / (float) Math.pow(2.0f, 15.0f);  // for 16G to m/s
        float accel[][] = new float[3][samples];
        for (int idx = 0; idx < samples; idx++)
            for (int chan = 0; chan < 3; chan++)
                accel[chan][idx] = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, (chan + idx * 3) * 2 + 6) * ACCEL_SCALE;

        onImuAccelReceived(device, accel);

        if (mLogging && streamLogger != null) {
            // long sensor_timestamp, int sensor_counter
            streamLogger.addAccelSample(new Date().getTime(), timestamp, counter, accel);
        }
    }

    private void parseImuGyro(final BluetoothDevice device, final Data characteristic) {
        int counter = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
        long timestamp = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 2);
        timestamp = timestampToReal(timestamp);

        int len = characteristic.getValue().length - 6;
        int samples = len / 6; // 6 bytes per entry

        gyroResolver.resolveTime(counter, timestamp, samples);

        final float GYRO_SCALE = 2000.0f / (float) Math.pow(2.0f, 15.0f);  // at 2000 deg/s to deg/s
        float gyro[][] = new float[3][samples];
        for (int idx = 0; idx < samples; idx++)
            for (int chan = 0; chan < 3; chan++)
                gyro[chan][idx] = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, (chan + idx * 3) * 2 + 6) * GYRO_SCALE;

        onImuGyroReceived(device, gyro);

        if (mLogging && streamLogger != null) {
            streamLogger.addGyroSample(new Date().getTime(), timestamp, counter, gyro);
        }
    }

    private void parseImuMag(final BluetoothDevice device, final Data characteristic) {
        int counter = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
        long timestamp = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 2);
        timestamp = timestampToReal(timestamp);

        int len = characteristic.getValue().length - 6;
        int samples = len / 6; // 6 bytes per entry

        magResolver.resolveTime(counter, timestamp, samples);

        float mag[][] = new float[3][samples];
        for (int idx = 0; idx < samples; idx++)
            for (int chan = 0; chan < 3; chan++)
                mag[chan][idx] = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, (chan + idx * 3) * 2 + 6);

        onImuMagReceived(device, mag);

        if (mLogging && streamLogger != null) {
            streamLogger.addMagSample(new Date().getTime(), timestamp, counter, mag);
        }
    }

    private void parseImuAttitude(final BluetoothDevice device, final Data characteristic) {
        int counter = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
        long timestamp = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 2);
        timestamp = timestampToReal(timestamp);

        attitudeResolver.resolveTime(counter, timestamp, 1);

        final float scale = 1.0f / 32767f;
        float quat[] = new float[4];
        for (int i = 0; i < 4; i++)
            quat[i] = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, i * 2 + 6) * scale;
        onImuAttitudeReceived(device, quat);

        if (mLogging && streamLogger != null) {
            streamLogger.addAttitudeSample(new Date().getTime(), timestamp, counter, quat);
        }
    }

    /**
     * This method transformes force meter BLE characteristic into a packaged messaged.
     * @param device            MAC address of the BLE device (force meter).
     * @param characteristic    Force measurement value.
     */
    private void parseForceMeterBLECharacteristic(BluetoothDevice device, Data characteristic) {
        long timestamp_ms = new Date().getTime();
        mForce = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 3);

        //TODO create separate data handling method for the force meter
        //onEmgPwrReceived(device, timestamp_ms, mForce * 10);

        // logging to firebase db
        if (mLogging && streamLogger != null) {
            double [] data = {(double) mForce};
            streamLogger.addForceSample(timestamp_ms, data);
        }
    }

    public interface CalibrationListener
    {
        void onUploading();
        void onComputing();
        void onReceivedCal(List<Float> Ainv, List<Float> b, float len_var, List<Float> angles);
        void onReceivedIm(Bitmap im);
        void onSent();
        void onError(String msg);
    }

    void startCalibration(CalibrationListener listener) {
        // Zero out prior calibration as first step
        FirebaseMagCalibration zeroCalibration = new FirebaseMagCalibration();
        // Notice using 1e-3 as the scale is increased by 1000 when sending.
        zeroCalibration.Ainv = new ArrayList<>(Arrays.asList(1.0e-3f, 0.0f, 0.0f, 0.0f, 1.0e-3f, 0.0f, 0.0f, 0.0f, 1.0e-3f));
        zeroCalibration.b = new ArrayList<>(Arrays.asList(0f,0f,0f));
        writeImuCalibration(zeroCalibration, listener);
    }

    void finishCalibration(CalibrationListener listener) {
        // TODO: needs a callback for when the stream logging stops to time
        // the write

        synchronized (this) {
            if (mLogging && streamLogger != null) {
                log(Log.INFO, "Closing stream logger");
                String path = streamLogger.getReference();

                // Called when the stream logger completes the upload
                streamLogger.addObserver((o, arg) -> {

                    // We are essentially using closing and opening to flush a log to the
                    // server, so start a new one.
                    log(Log.INFO, "Streamed uploaded. Creating a new stream logger.");
                    streamLogger = new FirebaseStreamLogger(EmgImuManager.this, getContext());

                    FirebaseAuth mAuth = FirebaseAuth.getInstance();
                    FirebaseUser mUser = mAuth.getCurrentUser();

                    if (mUser == null) {
                        log(Log.ERROR, "Should have a user assigned here");
                        throw new InvalidParameterException("No FirebaseUser");
                    }

                    FirebaseFirestore mDb = FirebaseFirestore.getInstance();
                    if (mDb == null) {
                        log(Log.ERROR, "Unable to get Firestore DB");
                        throw new InvalidParameterException("No Firestore DB");
                    }

                    TimeZone tz = TimeZone.getTimeZone("UTC");
                    DateFormat df = new SimpleDateFormat("yyyyMMdd-HHmmss'Z'"); // Quoted "Z" to indicate UTC, no timezone offset
                    df.setTimeZone(tz);
                    String dateName = df.format(new Date());
                    String fileName = getAddress() + "_" + dateName;

                    // Create entry
                    //FirebaseMagCalibration calibration = new FirebaseMagCalibration("a", "b", "c"); //getAddress(), path, mUser.getUid());
                    Map<String, Object> calibration = new HashMap<>();
                    calibration.put("path", path);
                    calibration.put("uuid", mUser.getUid());
                    calibration.put("sensor",getAddress());

                    // Write it
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    mainHandler.post(()-> {
                        DocumentReference doc = mDb.collection("magCalibration").document(fileName);

                        doc.addSnapshotListener((documentSnapshot, e) -> {
                            log(Log.DEBUG, "Snapshot update event");
                            if (documentSnapshot.exists()) {

                                log(Log.DEBUG, "Snapshot exists");
                                Double len_var = documentSnapshot.getDouble("len_var");

                                if (len_var == null) {
                                    log(Log.DEBUG, "Not computed yet");
                                } else if (len_var > 0.1) {
                                    log(Log.WARN, "Poor calibration. Not using");
                                    if (listener != null) listener.onError("Poor quality calibration");
                                } else {
                                    log(Log.INFO, "Calibration: " + documentSnapshot.getData());
                                    FirebaseMagCalibration updatedCalibration = documentSnapshot.toObject(FirebaseMagCalibration.class);

                                    // The first update will have no image but has the values. Can use here.
                                    if (updatedCalibration.calibration_image == null)
                                    {
                                        if (listener != null) listener.onReceivedCal(updatedCalibration.Ainv, updatedCalibration.b, updatedCalibration.len_var, updatedCalibration.angles);
                                        writeImuCalibration(updatedCalibration, listener);
                                    }
                                    else
                                    {
                                        byte [] png_bytes = Base64.decode(updatedCalibration.calibration_image.toBytes(), Base64.DEFAULT);
                                        Bitmap bmp = BitmapFactory.decodeByteArray(png_bytes, 0, png_bytes.length);
                                        if (listener != null) listener.onReceivedIm(bmp);
                                    }
                                }

                            } else {
                                log(Log.DEBUG, "Calibration document did not exist");
                            }
                        });

                        doc.set(calibration)
                                .addOnSuccessListener(aVoid ->
                                {
                                    Log.d(TAG, "Wrote: " + doc.getPath() + " successfully");
                                    if (listener != null) listener.onComputing();
                                })
                                .addOnFailureListener(e -> Log.e(TAG, "Unable to save log: " + e.getMessage()));
                    });

                });
                streamLogger.close();
                streamLogger = null;
                if (listener != null) listener.onUploading();
            } else {
                log(Log.ERROR, "Unable to calibrate as data was not streaming");
            }
        }

    }

    private static final int EXPONENT_MASK = 0xff000000;
    private static final int EXPONENT_SHIFT = 24;
    private static final int MANTISSA_MASK = 0x00ffffff;
    private static final int MANTISSA_SHIFT = 0;

    private void writeImuCalibration(FirebaseMagCalibration cal, CalibrationListener listener) {
        if (mImuCalibrationCharacteristic == null) {
            log(Log.ERROR, "Calibration characteristic not found");
            return;
        }
        log(Log.DEBUG, "Writing calibration");
        MutableData characteristic = new MutableData(new byte[48]);

        // Pack floating point calibration
        for (int i = 0; i < 9; i++) {
            // Mag scales for making data spherical. Note the scaling by 1000 to keep magnitude
            // reasonable.
            int bits = Float.floatToIntBits(cal.Ainv.get(i) * 1000f);
            characteristic.setValue(bits, BluetoothGattCharacteristic.FORMAT_SINT32, i*4);

            // Alternative approach
            // int exponent = (bits & EXPONENT_MASK) >>> EXPONENT_SHIFT;
            // int mantissa = (bits & MANTISSA_MASK) >>> MANTISSA_SHIFT;
            // characteristic.setValue(mantissa, exponent, BluetoothGattCharacteristic.FORMAT_FLOAT, i*4);
        }

        // Mag bias
        characteristic.setValue((int) Math.round(cal.b.get(0)), BluetoothGattCharacteristic.FORMAT_SINT16, 36);
        characteristic.setValue((int) Math.round(cal.b.get(1)), BluetoothGattCharacteristic.FORMAT_SINT16, 38);
        characteristic.setValue((int) Math.round(cal.b.get(2)), BluetoothGattCharacteristic.FORMAT_SINT16, 40);

        // Accel bias
        characteristic.setValue(0, BluetoothGattCharacteristic.FORMAT_SINT16, 42);
        characteristic.setValue(0, BluetoothGattCharacteristic.FORMAT_SINT16, 44);
        characteristic.setValue(0, BluetoothGattCharacteristic.FORMAT_SINT16, 46);

        Log.d(TAG, "Calibration characteristic: " + mImuCalibrationCharacteristic);
        Log.d(TAG, Arrays.toString(characteristic.getValue()));

        //Log.d(TAG, "New value: " + characteristic.getStringValue());
        writeCharacteristic(mImuCalibrationCharacteristic, characteristic)
                .invalid(() -> Log.d(TAG, "Invalid request"))
                .done(dev -> {
                    log(Log.INFO, "Sent new calibration settings");
                    if (listener != null) listener.onSent();
                })
                .fail((dev, status) ->
                {
                    log(Log.ERROR, "Failed to send calibration");
                    if (listener != null) listener.onError("Failed to send calibration");
                })
                .enqueue();
    }

    private void parseLog(final BluetoothDevice device, final Data characteristic) {
        int totalSize = characteristic.getValue().length;
        long timestamp = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0);
        timestamp = timestampToReal(timestamp);
        if (totalSize < 6) {
            throw new NegativeArraySizeException("Log characteristic too short to contain any values");
        }
        int offset = 4;
        List<Integer> emgPwr = new ArrayList<Integer>();
        while (offset < totalSize) {
            int val = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);
            emgPwr.add(val);
            offset += 2;
        }
        log(Log.VERBOSE, "Log record received with " + emgPwr.size() + " samples and timestamp " + new Date(timestamp));
        final EmgLogRecord emgRecord = new EmgLogRecord(timestamp, emgPwr);
        mRecords.add(emgRecord);
    }

    private void parseRACPIndication(final BluetoothDevice device, @NonNull final Data characteristic) {
        log(Log.VERBOSE, "RACP Indication: \"" + RecordAccessControlPointParser.parse(characteristic) + "\" received");

        // Record Access Control Point characteristic
        int offset = 0;
        final int opCode = characteristic.getIntValue(FORMAT_UINT8, offset);
        offset += 2; // skip the operator

         if (opCode == OP_CODE_SET_TIMESTAMP_COMPLETE) {
            // Now the timestamp has been acknowledged, we can fetch the records

            if(mFetchRecords) {
                mFetchRecords = false;

                log(Log.VERBOSE, "Timestamp set. Requesting all records.");

                // Clear any prior records before fetching
                mRecords.clear();

                writeCharacteristic(mRecordAccessControlPointCharacteristic,
                        Data.opCode(OP_CODE_REPORT_NUMBER_OF_RECORDS, OPERATOR_ALL_RECORDS))
                        .done(dev -> log(Log.INFO, "Request number of records from RACP"))
                        .fail((dev, status) -> logFetchFailed(dev,"Failed to request number of records from RACP"))
                        .enqueue();

            } else {
                log(Log.VERBOSE, "Device synced, but no record request made.");
            }

        } else if (opCode == OP_CODE_NUMBER_OF_STORED_RECORDS_RESPONSE) {
             // We've obtained the number of all records
             final int number = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);

             // Request the records
             if (number > 0) {
                 // TODO: this could be made more precise. It ignores the timestamp on device

                 // ms since a fixed date
                 float dt = 5000;
                 long now = new Date().getTime();
                 long T0 = now - (long) (dt * number);

                 log(Log.VERBOSE, "There are " + number + " records. Preparing firebase log for T0: " + T0);

                 // This calls back to firebaseLogReady
                 fireLogger.prepareLog(T0);
             } else {
                 log(Log.VERBOSE, "No records found");
                 if (successCallback != null)
                     successCallback.onFetchSucceeded(getBluetoothDevice());
             }
         } else if (opCode == OP_CODE_RESPONSE_CODE) {
            final int requestedOpCode = characteristic.getIntValue(FORMAT_UINT8, offset);
            final int responseCode = characteristic.getIntValue(FORMAT_UINT8, offset + 1);
            log(Log.VERBOSE, "Response result for: " + requestedOpCode + " is: " + responseCode);

            switch (responseCode) {
                case RESPONSE_SUCCESS:
                    // Defer operation complete operator until log is saved
                    storeRecordsToDb();
                    break;
                case RESPONSE_NO_RECORDS_FOUND:
                    log(Log.INFO, "No records found (op code)");
                    if (successCallback != null)
                        successCallback.onFetchSucceeded(getBluetoothDevice());
                    break;
                case RESPONSE_OP_CODE_NOT_SUPPORTED:
                case RESPONSE_PROCEDURE_NOT_COMPLETED:
                case RESPONSE_ABORT_UNSUCCESSFUL:
                default:
                    logFetchFailed(device, "Received bad op code on RACP. Either unknown or not completed/successful");
                    break;
            }
        }
    }

    /**** code for downloading logs and uploading to firestore ****/

    public void firebaseLogReady(FirebaseEmgLogger logger) {
        log(Log.VERBOSE, "Log ready. Requesting records from device");

        writeCharacteristic(mRecordAccessControlPointCharacteristic,
                Data.opCode(OP_CODE_REPORT_STORED_RECORDS, OPERATOR_ALL_RECORDS))
                .done(device -> Log.d(TAG, "Send report all records to RACP"))
                .fail((device, status) -> logFetchFailed(device,"Failed to request all records"))
                .enqueue();
    }

    private void storeRecordsToDb() {
        log(Log.VERBOSE, "storeRecordsToDb");

        List<Long> timestamps = new ArrayList<>();

        // TODO: come up with better way of getting the sampling rate that takes into account device
        // for now this is mathematically accurate.
        double dt_ms = 5000;
        if (mRecords.size() > 1) {
            // If multiple records calculate dt in ms.
            dt_ms = mRecords.get(1).timestamp - mRecords.get(0).timestamp;
            log(Log.DEBUG, "Difference in timestamps " + dt_ms);
            dt_ms = dt_ms / mRecords.get(0).emgPwr.size();
            log(Log.DEBUG, "Calculated sample period as " + dt_ms + " ms");
        }
        for (EmgLogRecord r : mRecords) {
            int i = 0;
            long timestamp = r.timestamp;
            for (Integer pwr : r.emgPwr) {
                fireLogger.addSample(timestamp + (long) (i * dt_ms), pwr);
                i = i + 1;
            }
        }

        log(Log.VERBOSE,"Entries added.");

        fireLogger.updateDb();

        // Record analytic data that might be useful
        // Obtain the FirebaseAnalytics instance.
        FirebaseAnalytics mFirebaseAnalytics = FirebaseAnalytics.getInstance(getContext());

        Bundle bundle = new Bundle();
        bundle.putString("DEVICE_NAME", getBluetoothDevice().getName());
        bundle.putString("DEVICE_MAC", getBluetoothDevice().getAddress());
        bundle.putString("NUM_RECORDS", Integer.toString(mRecords.size()));
        mFirebaseAnalytics.logEvent("DOWNLOAD_SENSOR_LOG", bundle);

        if (successCallback != null)
            successCallback.onFetchSucceeded(getBluetoothDevice());
    }

    private boolean mSynced;
    private long t0() {
        return new GregorianCalendar(2021, 0, 0).getTime().getTime();
    }
    /**
     * Pass the current time into the sensor. This is in a strange format to keep thinks
     * in range given the sensor timer running at 8hz. We will use 8hz units since the
     * beginning of 2018. This is set on the sensor by writing to the logging characteristic.
     * It will only sync the first time this is written since power up.
     */
    synchronized
    private void syncDevice() {
        mSynced = true;

        final int dt = (int) nowToTimestamp();

        log(Log.VERBOSE, "Sending sync signal with offset " + dt);

        final int size = 6;
        MutableData data = new MutableData(new byte[size]);
        data.setByte(OP_CODE_SET_TIMESTAMP, 0);
        data.setByte(0, 1);
        data.setValue(dt, BluetoothGattCharacteristic.FORMAT_UINT32, 2);

        // No need to use a success callback to continue trail as the device
        // sends an acknowledgement on RACP and we trigger from that
        writeCharacteristic(mRecordAccessControlPointCharacteristic, data)
                .done(device -> log(Log.INFO, "Send timestamp to RACP"))
                .fail((device, status) -> logFetchFailed(device, "Synchronization failed (" + status + ")"))
                .enqueue();

    }

    // Convert from the device format (8 Hz units since 2018 beginning) to the
    // android time format
    private long timestampToReal(long device_ts) {
        return t0() + (device_ts * 1000 / 8);
    }

    // Convert from now to device format (8 Hz units since 2018 beginning)
    private long nowToTimestamp() {
        long now = new Date().getTime();
        return ((now - t0()) * 8 / 1000);
    }

    /**
     * Sends abort operation signal to the device
     */
    public void logFetchAbort() {
        writeCharacteristic(mRecordAccessControlPointCharacteristic,
                Data.opCode(OP_CODE_ABORT_OPERATION, OPERATOR_NULL))
                .done(device -> log(Log.INFO, "Sent abort operation to RACP"))
                .fail((device, status) -> log(Log.WARN, "Failed to send abort operation to RACP: " + status))
                .enqueue();
    }

    /**
     * Sends the request to obtain all records from glucose device. Initially we want to notify him/her about the number of the records so the {@link #OP_CODE_REPORT_NUMBER_OF_RECORDS} is send. The
     * data will be returned to Glucose Measurement characteristic as a notification followed by Record Access Control Point indication with status code ({@link #RESPONSE_SUCCESS} or other in case of
     * error.
     */
    /*
        public interface LogFetchSuccessCallback {
        void onFetchSucceeded(@NonNull final BluetoothDevice device);
    }

    public interface LogFetchFailedCallback {
        void onFetchFailed(@NonNull final BluetoothDevice device, String reason);
    }
     */

    LogFetchSuccessCallback successCallback;
    LogFetchFailedCallback failCallback;

    private boolean mFetchRecords = false;
    public void fetchLogRecords(LogFetchSuccessCallback successCallback, LogFetchFailedCallback failCallback) {
        this.successCallback = successCallback;
        this.failCallback = failCallback;

        log(Log.INFO, "fetchLogRecords()");

        // Indicate when the synchronization of the device has completed that we should
        // then start downloading records
        mFetchRecords = true;

        // Set callbacks regarding log fetching. This is fairly ugly code
        // the chain of success callbacks. Alternatively could block on these
        // since service is probably not doing anything else, but avoiding
        // that for now. Essentially it
        // 1. enable RACP indications
        // 2. enable Log notifications
        // 3. calls syncDevice
        // if those fails it calls to logFetchFailed

        setIndicationCallback(mRecordAccessControlPointCharacteristic)
                .with((dev, data) -> parseRACPIndication(dev, data));
        enableIndications(mRecordAccessControlPointCharacteristic)
            .done(dev ->
            {
                log(Log.VERBOSE, "RACP indication enabled");

                setNotificationCallback(mEmgLogCharacteristic)
                        .with((d, data) -> parseLog(d, data));
                enableNotifications(mEmgLogCharacteristic)
                        .done(device ->
                        {
                            log(Log.VERBOSE, "Log characteristic indication enabled");
                            syncDevice();
                        })
                        .fail((d, status) -> logFetchFailed(d,"Failed to enable Log characteristic notifications (" + status + ")"))
                        .enqueue();
            })
            .fail((device, status) -> logFetchFailed(device, "Failed to enable RACP characteristic indications (" + status + ")"))
            .enqueue();

    }

    private void logFetchFailed(@NonNull final BluetoothDevice device, String reason)
    {
        mFetchRecords = false;

        // TODO: decide if this is needed or benficial
        logFetchAbort();

        if (failCallback != null)
            failCallback.onFetchFailed(device, reason);
    }


    // region Helper Methods to Enable and Disable Bluetooth Notifications
    // Controls to enable what data we are receiving from the sensor
    public void enableEmgPwrNotifications() {
        enableNotifications(mEmgPwrCharacteristic)
                .before(device -> setNotificationCallback(mEmgPwrCharacteristic).with((_device, data) -> parseEmgPwr(_device ,data)))
                .done(device -> log(Log.INFO, "EMG power notifications enabled successfully"))
                .fail((device, status) -> log(Log.ERROR, "Unable to enable EMG power notification"))
                .enqueue();
    }

    public void disableEmgPwrNotifications() {
        disableNotifications(mEmgPwrCharacteristic).enqueue();
    }

    public void enableEmgBuffNotifications() {
        enableNotifications(mEmgBuffCharacteristic)
                .before(device -> setNotificationCallback(mEmgBuffCharacteristic).with((_device, data) -> parseEmgBuff(_device ,data)))
                .done(device -> log(Log.INFO, "EMG buffer notifications enabled successfully"))
                .fail((device, status) -> log(Log.ERROR, "Unable to enable EMG buffer notification"))
                .enqueue();
    }

    public void disableEmgBuffNotifications() {
        disableNotifications(mEmgBuffCharacteristic).enqueue();
    }

    public void enableAttitudeNotifications() {
        enableNotifications(mImuAttitudeCharacteristic)
                .before(device -> setNotificationCallback(mImuAttitudeCharacteristic).with((_device, data) -> parseImuAttitude(_device, data)))
                .done(device -> log(Log.INFO, "Attitude notifications enabled successfully"))
                .fail((device, status) -> log(Log.ERROR, "Unable to enable Attitude notification: " + status))
                .enqueue();
    }

    public void disableAttitudeNotifications() {
        disableNotifications(mImuAttitudeCharacteristic).enqueue();
    }

    public void enableAccelNotifications() {
        enableNotifications(mImuAccelCharacteristic)
                .before(device -> setNotificationCallback(mImuAccelCharacteristic).with((_device, data) -> parseImuAccel(_device, data)))
                .done(device -> log(Log.INFO, "Accel notifications enabled successfully"))
                .fail((device, status) -> log(Log.ERROR, "Unable to enable Accel notification: " + status))
                .enqueue();
    }

    public void disableAccelNotifications() {
        disableNotifications(mImuAccelCharacteristic).enqueue();
    }

    public void enableGyroNotifications() {
        enableNotifications(mImuGyroCharacteristic)
                .before(device -> setNotificationCallback(mImuGyroCharacteristic).with((_device, data) -> parseImuGyro(_device, data)))
                .done(device -> log(Log.INFO, "Gyro notifications enabled successfully"))
                .fail((device, status) -> log(Log.ERROR, "Unable to enable Gyro notification: " + status))
                .enqueue();
    }

    public void disableGyroNotifications() {
        disableNotifications(mImuGyroCharacteristic).enqueue();
    }

    public void enableMagNotifications() {
        enableNotifications(mImuMagCharacteristic)
                .before(device -> setNotificationCallback(mImuMagCharacteristic).with((_device, data) -> parseImuMag(_device, data)))
                .done(device -> log(Log.INFO, "Mag notifications enabled successfully"))
                .fail((device, status) -> log(Log.ERROR, "Unable to enable Mag notification: " + status))
                .enqueue();
    }

    public void disableMagNotifications() {
        disableNotifications(mImuMagCharacteristic).enqueue();
    }
    // endregion

    /**** Public API for controlling what we are listening to ****/
    // This is mostly a very thin shim to the above methods

    // Accessors for the raw EMG
	private int mEmgRaw = -1;
    int getEmgRaw() { return mEmgRaw; }

    // Accessors for the EMG power
    private int mEmgPwr = -1;
    int getEmgPwr() { return mEmgPwr; }

    // Accessors for the FORCE power
    private int mForce = -1;
    int getForce() {return mForce; }

    // TODO: this should be user calibrate-able, or automatic
    private double MAX_SCALE = Short.MAX_VALUE * 2;
    private double MIN_SCALE = 0x0000; // TODO: this should be user calibrate-able, or automatic

    // Scale EMG from 0 to 1.0 using configurable endpoints
    double getEmgPwrScaled() {
        double val = (mEmgPwr - MIN_SCALE) / (MAX_SCALE - MIN_SCALE);
        if (val > 1.0)
            val = 1.0;
        else if (val < 0.0)
            val = 0.0;
        return val;
    }

    // Accessors for the EMG buffer
    private double [][] mEmgBuff;
    double[][] getEmgBuff() {
        return mEmgBuff;
    }

    public String getAddress() {
        if (getBluetoothDevice() == null)
            return "";
        return getBluetoothDevice().getAddress();
    }

    private MutableLiveData<Double> batteryVoltage = new MutableLiveData<>();
    public LiveData<Double> getBatteryVoltage() {
        return batteryVoltage;
    }

    private void parseBattery(@NonNull final BluetoothDevice device, @NonNull final Data data) {
        int batteryLevel = data.getIntValue(Data.FORMAT_UINT8, 0);

        double voltage = 3.0 + 1.35 * (batteryLevel / 100.0);
        batteryVoltage.setValue(voltage);

        onBatteryReceived(device, (float) voltage);
        log(Log.DEBUG, "Received battery level: " + batteryLevel);
    }

    public String getManufacturer() {
        return mManufacturer;
    }

    public String getFirmwareRevision() {
        return mFirmwareRevision;
    }

    public String getHardwareRevision() {
        return mHardwareRevision;
    }

    public String getLoggingRef() {
        if (mLogging == false || streamLogger == null) {
            return "";
        }

        return streamLogger.getReference();
    }

    public void filterInfoPrint(@NonNull final String message) {
        if (message.startsWith("Notification received")) {
            // Drop this message as we get too verbose
        } else {
            Log.i(TAG, getBluetoothDevice() + " " + message);
        }
    }
    public void log(final int priority, @NonNull final String message)
    {
        final int threshold = Log.VERBOSE;
        if (priority < threshold)
            return;

        switch(priority) {
            case Log.VERBOSE: Log.v(TAG, getBluetoothDevice() + " " + message); break;
            case Log.DEBUG: Log.d(TAG, getBluetoothDevice() + " " + message); break;
            case Log.INFO: filterInfoPrint(message);  break;
            case Log.WARN: Log.w(TAG, getBluetoothDevice() + " " + message); break;
            // TODO: log errors to the cloud somewhere
            case Log.ERROR: Log.e(TAG, getBluetoothDevice() + " " + message); break;
            default: Log.d(TAG, getBluetoothDevice() + " " + message); break;
        }
    }

    public interface LogFetchSuccessCallback {
        void onFetchSucceeded(@NonNull final BluetoothDevice device);
    }

    public interface LogFetchFailedCallback {
        void onFetchFailed(@NonNull final BluetoothDevice device, String reason);
    }

    public void onBatteryReceived(BluetoothDevice device, float battery) {
        for (IEmgImuBatCallback cb : batCbs) {
            try {
                cb.handleData(device, battery);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * @brief   This method packages the emgPwr from each sensor channel.
     * @details The data is packaged int EmgPwrData data structure defined in the EmgPwrData.aidl
     *          as a parcelable, which is consumed by the IEmgImuPwrDataCallback.aidl as an
     *          "in" parameter, indicating that the data flow is from the client to the server.
     *          Primarily, this method calls handleData() method implemented in the EmgImuViewModel.
     * @param device            MAC address of the sensor.
     * @param timestamp_ms      timestamp in milliseconds of the data packet.
     * @param channelCount      the total number of channels of the emgPwr value.
     * @param emgPowerValues    an array, zero indexed, of emgPwr value in mV * s, corresponding to
     *                          each channel. Generated by the waveform algorithm in firmware. It is not
     *                          particularly - designed to reasonably fill the UINT16 range.
     */
    public void onEmgPwrReceived(final BluetoothDevice device, long timestamp_ms, int channelCount, int[] emgPowerValues)
    {
        EmgPwrData dataMessage = new EmgPwrData();
        String firm = getFirmwareRevision();
        LiveData<Double> batteryVoltage = getBatteryVoltage();
        Log.d(TAG, "manager, firmware v = " + firm + " batteryVoltage = " + batteryVoltage.getValue());
        dataMessage.firmwareVersion = getFirmwareRevision();
        dataMessage.batteryVoltage = batteryVoltage.getValue();
        dataMessage.channels = channelCount;
        dataMessage.power = new int[channelCount];
        for (int channelIndex = 0; channelIndex < channelCount; channelIndex++) {
            dataMessage.power[channelIndex] = emgPowerValues[channelIndex];
        }
        dataMessage.ts = timestamp_ms;
        for (IEmgImuPwrDataCallback cb : emgPwrCbs) {
            try {
                cb.handleData(device, dataMessage);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public void onEmgStreamReceived(BluetoothDevice device, long ts_ms, double[][] data) {

        // TODO: fair bit of semi-redundant data serialization. Should consolidate.

        // First: send data to subscribed callbacks
        final int CHANNELS = data.length;
        final int SAMPLES = data[0].length;
        double [] linearizedData = Stream.of(data).flatMapToDouble(DoubleStream::of).toArray();

        // For reference. Might ultimately make sense to wrapper this in a utility class
        // double [][] reconstructed = IntStream.range(0, CHANNELS)
        //        .mapToObj(i -> Arrays.copyOfRange(linearizedData2, i * SAMPLES, (i + 1) * SAMPLES))
        //        .toArray(double[][]::new);

        EmgStreamData dataMsg = new EmgStreamData();
        dataMsg.channels = CHANNELS;
        dataMsg.samples = SAMPLES;
        dataMsg.voltage = linearizedData;
        dataMsg.ts = ts_ms;
        dataMsg.Fs = 2000; // TODO: access real data

        for (IEmgImuStreamDataCallback cb : emgStreamCbs) {
            try {
                cb.handleData(device, dataMsg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public void onImuAccelReceived(BluetoothDevice device, float[][] accel) {
        float [] linearizedData = new float[3 * 3];

        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                linearizedData[i + j * 3] = accel[i][j];

        ImuData dataMsg = new ImuData();
        dataMsg.samples = accel[0].length;
        dataMsg.x = accel[0];
        dataMsg.y = accel[1];
        dataMsg.z = accel[2];
        dataMsg.ts = new Date().getTime();
        for (IEmgImuSenseCallback cb : imuAccelCbs) {
            try {
                cb.handleData(device, dataMsg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public void onImuGyroReceived(BluetoothDevice device, float[][] gyro) {
        float [] linearizedData = new float[3 * 3];

        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                linearizedData[i + j * 3] = gyro[i][j];

        ImuData dataMsg = new ImuData();
        dataMsg.samples = gyro[0].length;
        dataMsg.x = gyro[0];
        dataMsg.y = gyro[1];
        dataMsg.z = gyro[2];
        dataMsg.ts = new Date().getTime();
        for (IEmgImuSenseCallback cb : imuGyroCbs) {
            try {
                cb.handleData(device, dataMsg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public void onImuMagReceived(BluetoothDevice device, float[][] mag) {
        float [] linearizedData = new float[3 * 3];

        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                linearizedData[i + j * 3] = mag[i][j];

        ImuData dataMsg = new ImuData();
        dataMsg.samples = mag[0].length;
        //dataMsg.ts = ts_ms;
        dataMsg.x = mag[0];
        dataMsg.y = mag[1];
        dataMsg.z = mag[2];
        dataMsg.ts = new Date().getTime();
        for (IEmgImuSenseCallback cb : imuMagCbs) {
            try {
                cb.handleData(device, dataMsg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public void onImuAttitudeReceived(BluetoothDevice device, float[] quaternion) {
        ImuQuatData dataMsg = new ImuQuatData();
        dataMsg.ts = 0; // TODO
        dataMsg.q0 = quaternion[0];
        dataMsg.q1 = quaternion[1];
        dataMsg.q2 = quaternion[2];
        dataMsg.q3 = quaternion[3];
        dataMsg.ts = new Date().getTime();
        for (IEmgImuQuatCallback cb : imuQuatCbs) {
            try {
                cb.handleData(device, dataMsg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }
}