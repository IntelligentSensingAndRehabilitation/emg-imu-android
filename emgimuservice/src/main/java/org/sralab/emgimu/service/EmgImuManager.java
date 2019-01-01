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

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import com.google.firebase.analytics.FirebaseAnalytics;

import org.sralab.emgimu.logging.FirebaseEmgLogger;
import org.sralab.emgimu.logging.FirebaseStreamLogger;
import org.sralab.emgimu.parser.RecordAccessControlPointParser;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Deque;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import no.nordicsemi.android.log.Logger;
import no.nordicsemi.android.ble.BleManager;

import static java.lang.Math.abs;

public class EmgImuManager extends BleManager<EmgImuManagerCallbacks> {
	private final String TAG = "EmgImuManager";

	/** Device Information UUID **/
    private final static UUID DEVICE_INFORMATION_SERVICE = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb");
    private final static UUID MANUFACTURER_NAME_CHARACTERISTIC = UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb");
    private final static UUID SERIAL_NUMBER_CHARACTERISTIC = UUID.fromString("00002A25-0000-1000-8000-00805f9b34fb");
    private final static UUID HARDWARE_REVISION_CHARACTERISTIC = UUID.fromString("00002A27-0000-1000-8000-00805f9b34fb");
    private final static UUID FIRMWARE_REVISION_CHARACTERISTIC = UUID.fromString("00002A26-0000-1000-8000-00805f9b34fb");

    private BluetoothGattCharacteristic mManufacturerCharacteristic, mSerialNumberCharacteristic, mHardwareCharacteristic, mFirmwareCharacteristic;

    /** EMG Service UUID **/
	public final static UUID EMG_SERVICE_UUID = UUID.fromString("00001234-1212-EFDE-1523-785FEF13D123");
    public final static UUID EMG_RAW_CHAR_UUID = UUID.fromString("00001235-1212-EFDE-1523-785FEF13D123");
    public final static UUID EMG_BUFF_CHAR_UUID = UUID.fromString("00001236-1212-EFDE-1523-785FEF13D123");
    public final static UUID EMG_PWR_CHAR_UUID = UUID.fromString("00001237-1212-EFDE-1523-785FEF13D123");

    /** IMU Service UUID **/
    public final static UUID IMU_SERVICE_UUID = UUID.fromString("00002234-1212-EFDE-1523-785FEF13D123");
    public final static UUID IMU_ACCEL_CHAR_UUID = UUID.fromString("00002235-1212-EFDE-1523-785FEF13D123");
    public final static UUID IMU_GYRO_CHAR_UUID = UUID.fromString("00002236-1212-EFDE-1523-785FEF13D123");
    public final static UUID IMU_MAG_CHAR_UUID = UUID.fromString("00002237-1212-EFDE-1523-785FEF13D123");
    public final static UUID IMU_ATTITUDE_CHAR_UUID = UUID.fromString("00002238-1212-EFDE-1523-785FEF13D123");

    private final double EMG_FS = 2000.0;
    private final int EMG_BUFFER_LEN = (40 / 2); // elements in UINT16

    private BluetoothGattCharacteristic mEmgRawCharacteristic, mEmgBuffCharacteristic, mEmgPwrCharacteristic;
    private BluetoothGattCharacteristic mImuAccelCharacteristic, mImuGyroCharacteristic, mImuMagCharacteristic, mImuAttitudeCharacteristic;

    /**
     * Record Access Control Point characteristic UUID
     */
    private final static UUID EMG_RACP_CHAR_UUID = UUID.fromString("00002a52-1212-EFDE-1523-785FEF13D123");
    private final static UUID EMG_LOG_CHAR_UUID = UUID.fromString("00001240-1212-EFDE-1523-785FEF13D123");

    private final static int OP_CODE_REPORT_STORED_RECORDS = 1;
    private final static int OP_CODE_DELETE_STORED_RECORDS = 2;
    private final static int OP_CODE_ABORT_OPERATION = 3;
    private final static int OP_CODE_REPORT_NUMBER_OF_RECORDS = 4;
    private final static int OP_CODE_NUMBER_OF_STORED_RECORDS_RESPONSE = 5;
    private final static int OP_CODE_RESPONSE_CODE = 6;
    private final static int OP_CODE_SET_TIMESTAMP = 7;
    private final static int OP_CODE_SET_TIMESTAMP_COMPLETE = 8;

    private final static int OPERATOR_NULL = 0;
    private final static int OPERATOR_ALL_RECORDS = 1;
    private final static int OPERATOR_LESS_THEN_OR_EQUAL = 2;
    private final static int OPERATOR_GREATER_THEN_OR_EQUAL = 3;
    private final static int OPERATOR_WITHING_RANGE = 4;
    private final static int OPERATOR_FIRST_RECORD = 5;
    private final static int OPERATOR_LAST_RECORD = 6;



    /**
     * The filter type is used for range operators ({@link #OPERATOR_LESS_THEN_OR_EQUAL}, {@link #OPERATOR_GREATER_THEN_OR_EQUAL}, {@link #OPERATOR_WITHING_RANGE}.<br/>
     * The syntax of the operand is: [Filter Type][Minimum][Maximum].<br/>
     * This filter selects the records by the sequence number.
     */
    private final static int FILTER_TYPE_SEQUENCE_NUMBER = 1;
    /**
     * The filter type is used for range operators ({@link #OPERATOR_LESS_THEN_OR_EQUAL}, {@link #OPERATOR_GREATER_THEN_OR_EQUAL}, {@link #OPERATOR_WITHING_RANGE}.<br/>
     * The syntax of the operand is: [Filter Type][Minimum][Maximum].<br/>
     * This filter selects the records by the user facing time (base time + offset time).
     */
    private final static int FILTER_TYPE_USER_FACING_TIME = 2;
    private final static int RESPONSE_SUCCESS = 1;
    private final static int RESPONSE_OP_CODE_NOT_SUPPORTED = 2;
    private final static int RESPONSE_INVALID_OPERATOR = 3;
    private final static int RESPONSE_OPERATOR_NOT_SUPPORTED = 4;
    private final static int RESPONSE_INVALID_OPERAND = 5;
    private final static int RESPONSE_NO_RECORDS_FOUND = 6;
    private final static int RESPONSE_ABORT_UNSUCCESSFUL = 7;
    private final static int RESPONSE_PROCEDURE_NOT_COMPLETED = 8;
    private final static int RESPONSE_OPERAND_NOT_SUPPORTED = 9;

    private FirebaseEmgLogger fireLogger;
    private FirebaseStreamLogger streamLogger;
    private List<EmgLogRecord> mRecords = new ArrayList<>();
    private boolean mAbort;

    private boolean mReady = false;

    private BluetoothGattCharacteristic mRecordAccessControlPointCharacteristic, mEmgLogCharacteristic;

    enum CHARACTERISTIC_TYPE {
        EMG_RAW,
        EMG_BUFF,
        EMG_PWR,
        EMG_RACP,
        EMG_LOG,
        IMU_ACCEL,
        IMU_GYRO,
        IMU_MAG,
        IMU_ATTITUDE,
        UNKNOWN
    };

    //! Data relating to logging to FireBase
    private boolean mLogging = true;

    private String mManufacturer;
    private String mHardwareRevision;
    private String mFirmwareRevision;
    private int mChannels;

    public EmgImuManager(final Context context) {
		super(context);
        mSynced = false;
        mFetchRecords = false;

        Log.d(TAG, "EmgImuManager created");
	}

	@Override
	protected boolean shouldAutoConnect() {
		return true;
	}

	@Override
	protected BleManagerGattCallback getGattCallback() {
		return mGattCallback;
	}

	@Override
    public void connect(final BluetoothDevice device) {

        Log.d(TAG, "EmgImuManager connect called: " + device.getAddress());

        if (device.getName() == null) {
            Log.e(TAG, "Attempting to connect to device but name is unknown");
            // Assume that channels are 1 in this case
            // TODO: probably should make this a proper characteristic or fall back better.
            mChannels = 1;
        } else if (device.getName().startsWith("EMG")) {
            mChannels = 1;
        } else if (device.getName().startsWith("8ch")) {
            mChannels = 8;
        } else {
            throw new RuntimeException("Cannot parse device name");
        }

        super.connect(device);
        fireLogger = new FirebaseEmgLogger(this);

        synchronized (this) {
            if (mLogging) {
                Log.d(TAG, "Created stream logger");
                streamLogger = new FirebaseStreamLogger(this);
            }
        }

        loadThreshold();
        loadPwrRange();
    }

    public void close() {
	    super.close();

        synchronized (this) {
            if (mLogging && streamLogger != null) {
                Log.d(TAG, "Closing stream logger");
                streamLogger.close();
                streamLogger = null;
            }
        }
    }

	/**
	 * BluetoothGatt callbacks for connection/disconnection, service discovery, receiving indication, etc
	 */
	private final BleManagerGattCallback mGattCallback = new BleManagerGattCallback() {

		@Override
		protected Deque<Request> initGatt(final BluetoothGatt gatt) {
			final LinkedList<Request> requests = new LinkedList<>();
            // When initially connected default to updates when the PWR is updated
            //requests.add(Request.newEnableNotificationsRequest(mEmgPwrCharacteristic));

            // Fetch the device information for this sensor
            if (mManufacturerCharacteristic != null)
                requests.add(Request.newReadRequest(mManufacturerCharacteristic));
            if (mSerialNumberCharacteristic != null)
                requests.add(Request.newReadRequest(mSerialNumberCharacteristic));
            if (mHardwareCharacteristic != null)
                requests.add(Request.newReadRequest(mHardwareCharacteristic));
            if (mFirmwareCharacteristic != null)
                requests.add(Request.newReadRequest(mFirmwareCharacteristic));

            // Make sure we get updates from the RACP characteristic
            if (mRecordAccessControlPointCharacteristic != null)
                requests.add(Request.newEnableIndicationsRequest(mRecordAccessControlPointCharacteristic));
            if (mEmgLogCharacteristic != null)
                requests.add(Request.newEnableNotificationsRequest(mEmgLogCharacteristic));

            mStreamingMode = STREAMING_MODE.STREAMINNG_POWER;
			return requests;
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

            Log.v(TAG, "Device information characteristics for service " + deviceInfoService.getUuid());
            for (BluetoothGattCharacteristic c : deviceInfoService.getCharacteristics() ) {
                Log.v(TAG, "Device information found: " + c.getUuid());
            }

            return  (mManufacturerCharacteristic != null) &&
                    (mSerialNumberCharacteristic != null) &&
                    (mHardwareCharacteristic != null) &&
                    (mFirmwareCharacteristic != null);
        }

		@Override
		protected boolean isRequiredServiceSupported(final BluetoothGatt gatt) {
			final BluetoothGattService llService = gatt.getService(EMG_SERVICE_UUID);
			if (llService != null) {
                mEmgRawCharacteristic = llService.getCharacteristic(EMG_RAW_CHAR_UUID);
                mEmgBuffCharacteristic = llService.getCharacteristic(EMG_BUFF_CHAR_UUID);
                mEmgPwrCharacteristic  = llService.getCharacteristic(EMG_PWR_CHAR_UUID);

                Log.v(TAG, "Characteristics for service " + llService.getUuid());
                for (BluetoothGattCharacteristic c : llService.getCharacteristics() ) {
                    Log.v(TAG, "Found: " + c.getUuid());
                }
            }

            boolean deviceInfoSupported = isDeviceInfoServiceSupported(gatt);

			return  (mEmgRawCharacteristic != null) &&
                    (mEmgPwrCharacteristic != null) &&
                    (mEmgBuffCharacteristic != null) &&
                    deviceInfoSupported;
		}

		@Override
		protected boolean isOptionalServiceSupported(final BluetoothGatt gatt) {

		    // Determine if logging is supported
            final BluetoothGattService llService = gatt.getService(EMG_SERVICE_UUID);
            if (llService != null) {
                mRecordAccessControlPointCharacteristic = llService.getCharacteristic(EMG_RACP_CHAR_UUID);
                mEmgLogCharacteristic = llService.getCharacteristic(EMG_LOG_CHAR_UUID);
            }
            boolean supportsLogging = (mEmgLogCharacteristic != null) && (mRecordAccessControlPointCharacteristic != null);

            final BluetoothGattService iaService = gatt.getService(IMU_SERVICE_UUID);
            if (iaService != null) {
                mImuAccelCharacteristic = iaService.getCharacteristic(IMU_ACCEL_CHAR_UUID);
                mImuGyroCharacteristic = iaService.getCharacteristic(IMU_GYRO_CHAR_UUID);
                mImuMagCharacteristic = iaService.getCharacteristic(IMU_MAG_CHAR_UUID);
                mImuAttitudeCharacteristic = iaService.getCharacteristic(IMU_ATTITUDE_CHAR_UUID);
            }
            boolean supportsImu = mImuAccelCharacteristic != null &&
                    mImuGyroCharacteristic != null &&
                    //mImuMagCharacteristic != null && // TODO: mag not supported in firmware yet
                    mImuAttitudeCharacteristic != null;

            Log.d(TAG, "Optional services found. Logging: " + supportsLogging + " IMU: " + supportsImu);

            return supportsLogging && supportsImu;
		}

        @Override
        public void onDeviceReady() {
            super.onDeviceReady();
            mReady = true;
        }

        @Override
		protected void onDeviceDisconnected() {
		    Log.d(TAG, "onDeviceDisconnected");

		    // Clear the Device Information characteristics
            mManufacturerCharacteristic = null;
            mSerialNumberCharacteristic = null;
            mHardwareCharacteristic = null;
            mFirmwareCharacteristic = null;

            // Clear the IMU characteristics
            mImuAccelCharacteristic = null;

            // Clear the EMG characteristics
            mEmgPwrCharacteristic = null;
            mEmgBuffCharacteristic = null;
            mEmgLogCharacteristic = null;
            mRecordAccessControlPointCharacteristic = null;

            mReady = false;

            mChannels = 0;

            synchronized (this) {
                if (mLogging && streamLogger != null) {
                    Log.d(TAG, "Closing stream logger");
                    streamLogger.close();
                    streamLogger = null;
                }
            }
		}

		private long mPwrT0;
		private long mLastPwrCount;

		private long resolvePwrCounter(long timestamp, byte counter) {
            /**
             * Tracks if the counter is reliable and if so uses this to work
             * out the timestamp for this sample. If there is a drop resets
             * based on the transmitted timestamp.
             */


            // counter is the power sample counter which should be running at a fixed rate
            final double COUNTER_HZ = 100.0;

            // convert timestamp into ms since some arbitrary T0
            long timestamp_real = timestampToReal(timestamp);

            // convert counter into ms, although this aliases frequently
            byte counter_aliased = (byte) (mLastPwrCount % 256);
            byte err = (byte) (counter - counter_aliased);
            switch (err) {
                case 1:
                //case -255:
                    // ideal, no apparent dropped samples
                    mLastPwrCount++;
                    break;
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                    Log.d(TAG, "Dropped PWR samples detected but tolerable. " + err);
                    mLastPwrCount += err;
                    break;
                default:
                    // TODO: sometime err is 0. Determine if this is duplicate packets.
                    Log.d(TAG, "Too many dropped samples detected: " + err);
                    mLastPwrCount = counter;
                    mPwrT0 = timestamp_real - (long) (mLastPwrCount * 1000 / COUNTER_HZ);
                    break;
            }

            // nominal combination
            long resolved_ms = mPwrT0 + (long) (mLastPwrCount * 1000 / COUNTER_HZ);

            long diff = resolved_ms - timestamp_real;
            if (abs(diff) > 200) {
                Log.e(TAG, "Timebase drift. Diff: " + diff + " Resolved: " + resolved_ms + " transmitted: " + timestamp_real);
                mPwrT0 = timestamp_real - (long) (mLastPwrCount * 1000 / COUNTER_HZ);
                resolved_ms = mPwrT0 + (long) (mLastPwrCount * 1000 / COUNTER_HZ);
            }

            return resolved_ms;
        }

		private void parsePwr(final BluetoothDevice device, final BluetoothGattCharacteristic characteristic) {
            final byte [] buffer = characteristic.getValue();
            byte format = buffer[0];
            assert(format == 16);
            byte counter = buffer[1];
            long timestamp = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 2);

            int pwr_val = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 6) +
                characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 7) * 256;

            long ts_ms = resolvePwrCounter(timestamp, counter);

            mEmgPwr = pwr_val;
            mCallbacks.onEmgPwrReceived(device, mEmgPwr);
            checkEmgClick(device, pwr_val);

            if (mLogging && streamLogger != null) {
                double [] data = {(double) mEmgPwr};
                streamLogger.addPwrSample(ts_ms, data);
            }
        }

        private long mBufT0;
        private long mLastBufferCount;
        private long resolveBufCounter(long timestamp, byte counter, double Fs) {
            /**
             * Tracks if the counter is reliable and if so uses this to work
             * out the timestamp for this sample. If there is a drop resets
             * based on the transmitted timestamp.
             */

            // counter is the power sample counter which should be running at a fixed rate
            final double COUNTER_HZ = Fs;

            // convert timestamp into ms since some arbitrary T0
            long timestamp_real = timestampToReal(timestamp);

            // convert counter into ms, although this aliases frequently
            byte counter_aliased = (byte) (mLastBufferCount % 256);
            byte err = (byte) (counter - counter_aliased);
            switch (err) {
                case 1:
                    //case -255:
                    // ideal, no apparent dropped samples
                    mLastBufferCount++;
                    break;
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                    Log.d(TAG, "Dropped PWR samples detected but tolerable. " + err);
                    mLastBufferCount += err;
                    break;
                default:
                    // TODO: sometime err is 0. Determine if this is duplicate packets.
                    Log.d(TAG, "Too many dropped samples detected. Err: " + err + " Counter: " + counter + " Aliased: " + counter_aliased + " Count: " + mLastBufferCount);
                    mLastBufferCount = counter;
                    mBufT0 = timestamp_real - (long) (mLastBufferCount * 1000 / COUNTER_HZ);
                    break;
            }

            // nominal combination
            long resolved_ms = mBufT0 + (long) (mLastBufferCount * 1000 / COUNTER_HZ);

            long diff = resolved_ms - timestamp_real;
            if (abs(diff) > 200) {
                Log.e(TAG, "Timebase drift. Diff: " + diff + " Resolved: " + resolved_ms + " transmitted: " + timestamp_real);
                mBufT0 = timestamp_real - (long) (mLastBufferCount * 1000 / COUNTER_HZ);
                resolved_ms = mBufT0 + (long) (mLastBufferCount * 1000 / COUNTER_HZ);
            }

            return resolved_ms;
        }

		private void parseBuff(final BluetoothDevice device, final BluetoothGattCharacteristic characteristic) {
            final byte [] buffer = characteristic.getValue();
            int len = buffer.length;

            int format = buffer[0] & 0xFF;

            double microvolts_per_lsb;

            byte counter = buffer[1];
            long timestamp = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 2);
            long buf_ts_ms = 0;

            double [][] data;
            int channels;
            int samples;

            int HDR_LEN = 6;
		    switch(format) {
		        // Data from single channel system
                case 16: // 1 channel of 16 bit data

                    buf_ts_ms = resolveBufCounter(timestamp, counter, EMG_FS / EMG_BUFFER_LEN); // 2khz but 8 samples per buffer);

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
                case 0x81:

                    channels = 8;
                    samples = 9;

                    final double ads1298_gain = 8;
                    final double vref = 2.5e6;
                    double full_scale_range = 2 * vref / ads1298_gain;
                    microvolts_per_lsb = full_scale_range / (1<<24);

                    buf_ts_ms = resolveBufCounter(timestamp, counter, 500.0 / samples);
                    Log.d(TAG, " Counter: " + counter + " timestamp: " + timestamp + " ms: " + buf_ts_ms + " scale: " + microvolts_per_lsb);

                    // representation of the data is 3 bytes per sample, 8 channels, and then N samples
                    data = new double[channels][samples];
                    for (int ch = 0; ch < 8; ch++) {
                        for (int sample = 0; sample < samples; sample++) {
                            int idx = HDR_LEN + 3 * (ch + channels * sample);
                            byte sign = ((buffer[idx + 2] & 0x80) == 0x80) ? (byte) 0xff : (byte) 0x00;
                            byte [] t_array = {buffer[idx], buffer[idx+1], buffer[idx+2], sign};
                            data[ch][sample] = microvolts_per_lsb * ByteBuffer.wrap(t_array).order(ByteOrder.BIG_ENDIAN).getInt();
                        }
                        //Log.d(TAG, "Data[" + ch + "] = " + Arrays.toString(data[ch]));
                    }

                    break;
                default:
                    Log.e(TAG, "Unsupported data format");
                    return;
            }

            if (mChannels != channels){
                throw new RuntimeException("Channel count seemed to change between calls");
            }

            mEmgBuff = data;
            mCallbacks.onEmgBuffReceived(device, counter, data);

            if (mLogging && streamLogger != null) {
                streamLogger.addRawSample(buf_ts_ms, channels, samples, data);
            }

        }

        @Override
        protected void onCharacteristicRead(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {

            if (MANUFACTURER_NAME_CHARACTERISTIC.equals(characteristic.getUuid())) {
                mManufacturer = characteristic.getStringValue(0);
                Log.d(TAG, "Manufacturer: " + mManufacturer);
            }

            if (HARDWARE_REVISION_CHARACTERISTIC.equals(characteristic.getUuid())) {
                mHardwareRevision = characteristic.getStringValue(0);
                Log.d(TAG, "Hardware revision: " + mHardwareRevision);
            }

            if (FIRMWARE_REVISION_CHARACTERISTIC.equals(characteristic.getUuid())) {
                mFirmwareRevision = characteristic.getStringValue(0);
                Log.d(TAG, "Firmware revision: " + mFirmwareRevision);
            }

            if (SERIAL_NUMBER_CHARACTERISTIC.equals(characteristic.getUuid())) {
                Log.d(TAG, "Serial number: " + characteristic.getStringValue(0));
            }
        }

        @Override
		protected void onCharacteristicWrite(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            if (characteristic.getUuid().equals(EMG_RACP_CHAR_UUID)) {
                Logger.a(mLogSession, "\"" + RecordAccessControlPointParser.parse(characteristic) + "\" sent");
            } else
			    Logger.a(mLogSession, "\"" + characteristic + "\" sent");
		}

		@Override
        public void onCharacteristicNotified(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {

            final BluetoothDevice device = gatt.getDevice();
            switch(getCharacteristicType(characteristic)) {
                case EMG_RAW:
                    // Have to manually combine to get the endian right
                    int raw_val = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0) +
                            characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1) * 256;
                    mEmgRaw = raw_val;
                    mCallbacks.onEmgRawReceived(device, mEmgRaw);
                    break;
                case EMG_PWR:
                    parsePwr(device, characteristic);
                    break;
                case EMG_BUFF:
                    parseBuff(device, characteristic);
                    break;
                case EMG_LOG:
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
                    Log.d(TAG, getAddress() + " Log record received with " + emgPwr.size() + " samples and timestamp " + new Date(timestamp));
                    Logger.d(mLogSession, "Log record received with " + emgPwr.size() + " samples and timestamp " + new Date(timestamp));
                    final EmgLogRecord emgRecord = new EmgLogRecord(timestamp, emgPwr);
                    mRecords.add(emgRecord);
                    mCallbacks.onEmgLogRecordReceived(device, emgRecord);
                    break;
                case IMU_ACCEL:
                    final float ACCEL_SCALE = 9.8f / 2048.0f; // for 16G to m/s
                    float accel[][] = new float[3][3];
                    for (int idx = 0; idx < 3; idx++) // 3 comes from "BUNDLE" param in firmware
                        for (int chan = 0; chan < 3; chan++)
                            accel[idx][chan] = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, (chan + idx * 3) * 2) * ACCEL_SCALE;

                    mCallbacks.onImuAccelReceived(device, accel);

                    if (mLogging && streamLogger != null) {
                        streamLogger.addAccelSample(new Date().getTime(), accel);
                    }

                    break;
                case IMU_GYRO:
                    final float GYRO_SCALE = 1.0f / 65.5f; // at 500 deg/s to deg/s
                    float gyro[][] = new float[3][3];
                    for (int idx = 0; idx < 3; idx++) // 3 comes from "BUNDLE" param in firmware
                        for (int chan = 0; chan < 3; chan++)
                            gyro[idx][chan] = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, (chan + idx * 3) * 2) * GYRO_SCALE;

                    mCallbacks.onImuGyroReceived(device, gyro);

                    if (mLogging && streamLogger != null) {
                        streamLogger.addGyroSample(new Date().getTime(), gyro);
                    }

                    break;
                case IMU_ATTITUDE:
                    final float scale = 1.0f / 32767f;
                    float quat[] = new float[4];
                    for (int i = 0; i < 4; i++)
                        quat[i] = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, i * 2) * scale;
                    mCallbacks.onImuAttitudeReceived(device, quat);

                    if (mLogging && streamLogger != null) {
                        streamLogger.addAttitudeSample(new Date().getTime(), quat);
                    }
                    break;
                default:
                    Log.e(TAG, "Received unknown or unexpected notification of characteristic: \"" + characteristic.getUuid().toString() + "\"");
                    Logger.e(mLogSession, "Received unknown or unexpected notification of characteristic: \"" + characteristic.getUuid().toString() + "\"");
                    break;
            }
        }

        @Override
        protected void onCharacteristicIndicated(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            switch(getCharacteristicType(characteristic)) {
                case EMG_RACP:
                    Logger.a(mLogSession, "RACP Indication: \"" + RecordAccessControlPointParser.parse(characteristic) + "\" received");

                    // Record Access Control Point characteristic
                    int offset = 0;
                    final int opCode = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset);
                    offset += 2; // skip the operator

                    if (opCode == OP_CODE_NUMBER_OF_STORED_RECORDS_RESPONSE) {
                        // We've obtained the number of all records
                        final int number = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);

                        mCallbacks.onNumberOfRecordsRequested(gatt.getDevice(), number);

                        // Request the records
                        if (number > 0) {
                            // TODO: this could be made more precise. It ignores the timestamp on device

                            // ms since a fixed date
                            float dt = 5000;
                            long now = new Date().getTime();
                            long T0 = now - (long) (dt * number);

                            Log.d(TAG, getAddress() + " There are " + number + " records. Preparing firebase log for T0: " + T0);
                            Logger.d(mLogSession, "There are " + number + " records. Preparing firebase log for T0: " + T0);

                            fireLogger.prepareLog(T0);
                        } else {
                            Log.d(TAG, "No records found");
                            Logger.d(mLogSession, "No records found");
                            mCallbacks.onOperationCompleted(gatt.getDevice());
                        }
                    } else if (opCode == OP_CODE_SET_TIMESTAMP_COMPLETE) {
                        // Now the timestamp has been acknowledged, we can fetch the records

                        if(mFetchRecords) {
                            mFetchRecords = false;

                            Log.d(TAG, "Timestamp set");
                            Logger.d(mLogSession, "Timestamp set. Requesting all records.");

                            clear();
                            mCallbacks.onOperationStarted(mBluetoothDevice);

                            setOpCode(mRecordAccessControlPointCharacteristic, OP_CODE_REPORT_NUMBER_OF_RECORDS, OPERATOR_ALL_RECORDS);
                            writeCharacteristic(mRecordAccessControlPointCharacteristic);
                        } else {
                            Log.d(TAG, "Device synced, but no record request made.");
                            Logger.d(mLogSession, "Device synced, but no record request made.");
                        }

                    } else if (opCode == OP_CODE_RESPONSE_CODE) {
                        final int requestedOpCode = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset);
                        final int responseCode = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset + 1);
                        Logger.d(mLogSession, "Response result for: " + requestedOpCode + " is: " + responseCode);

                        switch (responseCode) {
                            case RESPONSE_SUCCESS:
                                if (!mAbort) {
                                    // Defer operation complete operator until log is saved
                                    storeRecordsToDb();
                                } else
                                    mCallbacks.onOperationAborted(gatt.getDevice());
                                break;
                            case RESPONSE_NO_RECORDS_FOUND:
                                mCallbacks.onOperationCompleted(gatt.getDevice());
                                break;
                            case RESPONSE_OP_CODE_NOT_SUPPORTED:
                                mCallbacks.onOperationNotSupported(gatt.getDevice());
                                break;
                            case RESPONSE_PROCEDURE_NOT_COMPLETED:
                            case RESPONSE_ABORT_UNSUCCESSFUL:
                            default:
                                mCallbacks.onOperationFailed(gatt.getDevice());
                                break;
                        }
                        mAbort = false;
                    }
                    break;
                case EMG_LOG:
                    Logger.a(mLogSession, "Received LOG_CHAR indication"); //"\"" + CGMSpecificOpsControlPointParser.parse(characteristic) + "\" received");
                    break;
                default:
                    Logger.e(mLogSession, "Received unexpected indication update: " + characteristic.getUuid().toString());
                    break;
            }
        }
	};

    /**
     * Writes given operation parameters to the characteristic
     *
     * @param characteristic the characteristic to write. This must be the Record Access Control Point characteristic
     * @param opCode         the operation code
     * @param operator       the operator (see {@link #OPERATOR_NULL} and others
     * @param params         optional parameters (one for >=, <=, two for the range, none for other operators)
     */
    private void setOpCode(final BluetoothGattCharacteristic characteristic, final int opCode, final int operator, final Integer... params) {
        final int size = 2 + ((params.length > 0) ? 1 : 0) + params.length * 2; // 1 byte for opCode, 1 for operator, 1 for filter type (if parameters exists) and 2 for each parameter
        characteristic.setValue(new byte[size]);

        // write the operation code
        int offset = 0;
        characteristic.setValue(opCode, BluetoothGattCharacteristic.FORMAT_UINT8, offset);
        offset += 1;

        // write the operator. This is always present but may be equal to OPERATOR_NULL
        characteristic.setValue(operator, BluetoothGattCharacteristic.FORMAT_UINT8, offset);
        offset += 1;

        // if parameters exists, append them. Parameters should be sorted from minimum to maximum. Currently only one or two params are allowed
        if (params.length > 0) {
            // our implementation use only sequence number as a filer type
            characteristic.setValue(FILTER_TYPE_SEQUENCE_NUMBER, BluetoothGattCharacteristic.FORMAT_UINT8, offset);
            offset += 1;

            for (final Integer i : params) {
                characteristic.setValue(i, BluetoothGattCharacteristic.FORMAT_UINT16, offset);
                offset += 2;
            }
        }
    }

    private boolean mSynced;
    private long t0() {
        return new GregorianCalendar(2018, 0, 0).getTime().getTime();
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

        if (mRecordAccessControlPointCharacteristic == null) {
            if (BuildConfig.DEBUG)
                throw new RuntimeException("mRecordAccessControlPointCharacteristic was null. This shouldn't really happen.");
            return;
        }

        Log.d(TAG, "Sending sync signal with offset " + dt);
        Logger.a(mLogSession, "Sending sync signal with offset " + dt);

        final BluetoothGattCharacteristic characteristic = mRecordAccessControlPointCharacteristic;

        // Send our custom "sync" RACP message
        final int size = 6;
        characteristic.setValue(new byte[size]);
        characteristic.setValue(OP_CODE_SET_TIMESTAMP, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        characteristic.setValue(0, BluetoothGattCharacteristic.FORMAT_UINT8, 1); // operator doesn't matter
        // sending 4 bytes for timestamp
        characteristic.setValue(dt, BluetoothGattCharacteristic.FORMAT_UINT32, 2);
        // write this
        writeCharacteristic(characteristic);
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
     * Returns a list of CGM records obtained from this device. The key in the array is the
     */
    public List<EmgLogRecord> getRecords() {
        return mRecords;
    }

    /**
     * Clears the records list locally
     */
    public void clear() {
        mRecords.clear();
        mCallbacks.onDatasetClear(mBluetoothDevice);
    }

    /**
     * Sends the request to obtain the last (most recent) record from glucose device. The data will be returned to Glucose Measurement characteristic as a notification followed by Record Access
     * Control Point indication with status code ({@link #RESPONSE_SUCCESS} or other in case of error.
     */
    public void getLastRecord() {
        if (mRecordAccessControlPointCharacteristic == null)
            return;

        clear();
        mCallbacks.onOperationStarted(mBluetoothDevice);

        final BluetoothGattCharacteristic characteristic = mRecordAccessControlPointCharacteristic;
        setOpCode(characteristic, OP_CODE_REPORT_STORED_RECORDS, OPERATOR_LAST_RECORD);
        writeCharacteristic(characteristic);
    }

    /**
     * Sends the request to obtain the first (oldest) record from glucose device. The data will be returned to Glucose Measurement characteristic as a notification followed by Record Access Control
     * Point indication with status code ({@link #RESPONSE_SUCCESS} or other in case of error.
     */
    public void getFirstRecord() {
        if (mRecordAccessControlPointCharacteristic == null)
            return;

        clear();
        mCallbacks.onOperationStarted(mBluetoothDevice);

        final BluetoothGattCharacteristic characteristic = mRecordAccessControlPointCharacteristic;
        setOpCode(characteristic, OP_CODE_REPORT_STORED_RECORDS, OPERATOR_FIRST_RECORD);
        writeCharacteristic(characteristic);
    }

    /**
     * Sends abort operation signal to the device
     */
    public void abort() {
        if (mRecordAccessControlPointCharacteristic == null)
            return;

        mAbort = true;
        final BluetoothGattCharacteristic characteristic = mRecordAccessControlPointCharacteristic;
        setOpCode(characteristic, OP_CODE_ABORT_OPERATION, OPERATOR_NULL);
        writeCharacteristic(characteristic);
    }

    /**
     * Sends the request to obtain all records from glucose device. Initially we want to notify him/her about the number of the records so the {@link #OP_CODE_REPORT_NUMBER_OF_RECORDS} is send. The
     * data will be returned to Glucose Measurement characteristic as a notification followed by Record Access Control Point indication with status code ({@link #RESPONSE_SUCCESS} or other in case of
     * error.
     */
    private boolean mFetchRecords = false;
    public void getAllRecords() {

        if (mRecordAccessControlPointCharacteristic == null)
            return;

        Log.d(TAG, "getAllRecords()");

        // Indicate when the synchronization of the device has completed that we should
        // then start downloading records
        mFetchRecords = true;
        syncDevice();
    }

    /**
     * Sends the request to obtain all records from glucose device. Initially we want to notify him/her about the number of the records so the {@link #OP_CODE_REPORT_NUMBER_OF_RECORDS} is send. The
     * data will be returned to Glucose Measurement characteristic as a notification followed by Record Access Control Point indication with status code ({@link #RESPONSE_SUCCESS} or other in case of
     * error.
     */
    public void refreshRecords() {
        if (mRecordAccessControlPointCharacteristic == null)
            return;

        getAllRecords();
        /*
        if (mRecords.size() == 0) {
            getAllRecords();
        } else {
            mCallbacks.onOperationStarted(mBluetoothDevice);

            // obtain the last sequence number
            final int sequenceNumber = mRecords.keyAt(mRecords.size() - 1) + 1;

            final BluetoothGattCharacteristic characteristic = mRecordAccessControlPointCharacteristic;
            setOpCode(characteristic, OP_CODE_REPORT_STORED_RECORDS, OPERATOR_GREATER_THEN_OR_EQUAL, sequenceNumber);
            writeCharacteristic(characteristic);
            // Info:
            // Operators OPERATOR_GREATER_THEN_OR_EQUAL, OPERATOR_LESS_THEN_OR_EQUAL and OPERATOR_RANGE are not supported by the CGMS sample from SDK
            // The "Operation not supported" response will be received
        }
        */
    }

    public void deleteAllRecords() {
        if (mRecordAccessControlPointCharacteristic == null)
            return;

        clear();
        mCallbacks.onOperationStarted(mBluetoothDevice);

        final BluetoothGattCharacteristic characteristic = mRecordAccessControlPointCharacteristic;
        setOpCode(characteristic, OP_CODE_DELETE_STORED_RECORDS, OPERATOR_ALL_RECORDS);
        writeCharacteristic(characteristic);
    }


    // Controls to enable what data we are receiving from the sensor
    private final boolean enableEmgPwrNotifications() {
        return enqueue(Request.newEnableNotificationsRequest(mEmgPwrCharacteristic));
    }

    private final boolean disableEmgPwrNotifications() {
        return enqueue(Request.newDisableNotificationsRequest(mEmgPwrCharacteristic));
    }

    private final boolean enableEmgBuffNotifications() {
        return enqueue(Request.newEnableNotificationsRequest(mEmgBuffCharacteristic));
    }

    private final boolean disableEmgBuffNotifications() {
        return enqueue(Request.newDisableNotificationsRequest(mEmgBuffCharacteristic));
    }

    private final boolean enableAttitudeNotifications() {
        return enqueue(Request.newEnableNotificationsRequest(mImuAttitudeCharacteristic));
    }

    private final boolean disableAttitudeNotifications() {
        return enqueue(Request.newDisableNotificationsRequest(mImuAttitudeCharacteristic));
    }

    private final boolean enableImuNotifications() {
        // TODO: add mag when firmware supports
        return enqueue(Request.newEnableNotificationsRequest(mImuAccelCharacteristic)) &&
                enqueue(Request.newEnableNotificationsRequest(mImuGyroCharacteristic));
    }

    private final boolean disableImuNotifications() {
        return enqueue(Request.newDisableNotificationsRequest(mImuAccelCharacteristic)) &&
                enqueue(Request.newDisableNotificationsRequest(mImuGyroCharacteristic));
    }

    // Handle the two streaming modes for EMG data (raw buffered data or processed power)

     public enum STREAMING_MODE {
        STREAMING_UNKNOWN,
        STREAMINNG_POWER,
        STREAMING_BUFFERED
    };

    private STREAMING_MODE mStreamingMode = STREAMING_MODE.STREAMING_UNKNOWN;
    public final void enableBufferedStreamingMode() {

        Log.d(TAG, "enabledBufferedStreamingMode: " + mSynced);

        enableEmgBuffNotifications();
        disableEmgPwrNotifications();
        mStreamingMode = STREAMING_MODE.STREAMING_BUFFERED;
    }

    public final void enablePowerStreamingMode() {

        Log.d(TAG, "enablePowerStreamingMode: " + mSynced);

        enableEmgPwrNotifications();
        disableEmgBuffNotifications();
        mStreamingMode = STREAMING_MODE.STREAMINNG_POWER;
    }

    public STREAMING_MODE getStreamingMode() { return mStreamingMode; }

    public final void enableAttitude() {
        enableAttitudeNotifications();
    }

    public final void disableAttitude() {
        disableAttitudeNotifications();
    }

    public final void enableImu() {
        enableImuNotifications();
    }

    public final void disableImu() {
        disableImuNotifications();
    }


    // Accessors for the raw EMG
	private int mEmgRaw = -1;
    public int getEmgRaw() { return mEmgRaw; }

    //! Return if this is the raw EMG characteristic
    private boolean isEmgRawCharacteristic(final BluetoothGattCharacteristic characteristic) {
        if (characteristic == null)
            return false;

        return EMG_RAW_CHAR_UUID.equals(characteristic.getUuid());
    }

    // Accessors for the EMG power
    private int mEmgPwr = -1;
    public int getEmgPwr() { return mEmgPwr; }

    // TODO: this should be user calibrate-able, or automatic
    private double MAX_SCALE = Short.MAX_VALUE * 2;
    private double MIN_SCALE = 0x0000; // TODO: this should be user calibrate-able, or automatic

    // Scale EMG from 0 to 1.0 using configurable endpoints
    public double getEmgPwrScaled() {
        double val = (mEmgPwr - MIN_SCALE) / (MAX_SCALE - MIN_SCALE);
        if (val > 1.0)
            val = 1.0;
        else if (val < 0.0)
            val = 0.0;
        return val;
    }

    //! Output true when EMG power goes over threshold
    private long THRESHOLD_TIME_NS = 500 * (int)1e6; // 500 ms
    private float max_pwr = 2000;
    private float min_pwr = 100;
    private float threshold_low = 200;
    private float threshold_high = 500;
    private long mThresholdTime = 0;
    private boolean overThreshold;

    /**
     * Check if a click event happened when EMG goes ovre threshold with hysteresis and
     * refractory period.
     */
    public void checkEmgClick(final BluetoothDevice device, int value) {
        // Have a refractory time to prevent noise making multiple events
        long eventTime = System.nanoTime();
        boolean refractory = (eventTime - mThresholdTime) > THRESHOLD_TIME_NS;

        if (value > threshold_high && overThreshold == false && refractory) {
            mThresholdTime = eventTime; // Store this time
            overThreshold = true;
            mCallbacks.onEmgClick(device);
        } else if (value < threshold_low && overThreshold == true) {
            overThreshold = false;
        }
    }

    // Accessors for the EMG buffer
    private double [][] mEmgBuff;
    public double[][] getEmgBuff() {
        return mEmgBuff;
    }

    //! Return if this is the EMG PWR characteristic
    private boolean isEmgPwrCharacteristic(final BluetoothGattCharacteristic characteristic) {
        if (characteristic == null)
            return false;

        return EMG_PWR_CHAR_UUID.equals(characteristic.getUuid());
    }

    //! Return if this is the EMG PWR characteristic
    private boolean isEmgBuffCharacteristic(final BluetoothGattCharacteristic characteristic) {
        if (characteristic == null)
            return false;

        return EMG_BUFF_CHAR_UUID.equals(characteristic.getUuid());
    }

    private boolean isEmgLogCharacteristic(final BluetoothGattCharacteristic characteristic) {
        if (characteristic == null)
            return false;

        return EMG_LOG_CHAR_UUID.equals(characteristic.getUuid());
    }

    private boolean isRACPCharacteristic(final BluetoothGattCharacteristic characteristic) {
        if (characteristic == null)
            return false;

        return EMG_RACP_CHAR_UUID.equals(characteristic.getUuid());
    }


    private boolean isImuAccelCharacteristic(final BluetoothGattCharacteristic characteristic) {
        if (characteristic == null)
            return false;

        return IMU_ACCEL_CHAR_UUID.equals(characteristic.getUuid());
    }

    private boolean isImuGyroCharacteristic(final BluetoothGattCharacteristic characteristic) {
        if (characteristic == null)
            return false;

        return IMU_GYRO_CHAR_UUID.equals(characteristic.getUuid());
    }

    private boolean isImuMagCharacteristic(final BluetoothGattCharacteristic characteristic) {
        if (characteristic == null)
            return false;

        return IMU_MAG_CHAR_UUID.equals(characteristic.getUuid());
    }

    private boolean isImuAttitudeCharacteristic(final BluetoothGattCharacteristic characteristic) {
        if (characteristic == null)
            return false;

        return IMU_ATTITUDE_CHAR_UUID.equals(characteristic.getUuid());
    }

    private CHARACTERISTIC_TYPE getCharacteristicType(final BluetoothGattCharacteristic characteristic) {

        if (isEmgRawCharacteristic(characteristic))
            return CHARACTERISTIC_TYPE.EMG_RAW;
        if (isEmgPwrCharacteristic(characteristic))
            return CHARACTERISTIC_TYPE.EMG_PWR;
        if (isEmgBuffCharacteristic(characteristic))
            return CHARACTERISTIC_TYPE.EMG_BUFF;
        if (isRACPCharacteristic(characteristic))
            return CHARACTERISTIC_TYPE.EMG_RACP;
        if (isEmgLogCharacteristic(characteristic))
            return CHARACTERISTIC_TYPE.EMG_LOG;
        if (isImuAccelCharacteristic(characteristic))
            return CHARACTERISTIC_TYPE.IMU_ACCEL;
        if (isImuGyroCharacteristic(characteristic))
            return CHARACTERISTIC_TYPE.IMU_GYRO;
        if (isImuMagCharacteristic(characteristic))
            return CHARACTERISTIC_TYPE.IMU_MAG;
        if (isImuAttitudeCharacteristic(characteristic))
            return CHARACTERISTIC_TYPE.IMU_ATTITUDE;

        return CHARACTERISTIC_TYPE.UNKNOWN;
    }

    /**** code for downloading logs and uploading to firestore ****/

    private void storeRecordsToDb() {
        Log.d(TAG, getAddress() + " storeRecordsToDb");
        Logger.d(mLogSession, "storeRecordsToDb");

        List<Long> timestamps = new ArrayList<>();

        // TODO: come up with better way of getting the sampling rate that takes into account device
        // for now this is mathematically accurate.
        double dt_ms = 5000;
        if (mRecords.size() > 1) {
            // If multiple records calculate dt in ms.
            dt_ms = mRecords.get(1).timestamp - mRecords.get(0).timestamp;
            Log.d(TAG, "Difference in timestamps " + dt_ms);
            dt_ms = dt_ms / mRecords.get(0).emgPwr.size();
            Log.d(TAG, "Calculated sample period as " + dt_ms + " ms");
        }
        for (EmgLogRecord r : mRecords) {
            int i = 0;
            long timestamp = r.timestamp;
            for (Integer pwr : r.emgPwr) {
                fireLogger.addSample(timestamp + (long) (i * dt_ms), pwr);
                i = i + 1;
            }
        }

        Log.d(TAG, "Entries added.");
        Logger.d(mLogSession,"Entries added.");

        fireLogger.updateDb();

        // Record analytic data that might be useful
        // Obtain the FirebaseAnalytics instance.
        FirebaseAnalytics mFirebaseAnalytics = FirebaseAnalytics.getInstance(getContext());

        Bundle bundle = new Bundle();
        bundle.putString("DEVICE_NAME", mBluetoothDevice.getName());
        bundle.putString("DEVICE_MAC", mBluetoothDevice.getAddress());
        bundle.putString("NUM_RECORDS", Integer.toString(mRecords.size()));
        // TODO: store battery value once this is obtained
        mFirebaseAnalytics.logEvent("DOWNLOAD_SENSOR_LOG", bundle);

        // Note: this does not wait for firebase to complete the set event
        mCallbacks.onOperationCompleted(mBluetoothDevice);
    }

    public String getAddress() {
        if (mBluetoothDevice == null)
            return "";
        return mBluetoothDevice.getAddress();
    }

    public void firebaseLogReady(FirebaseEmgLogger logger) {
        Log.d(TAG, getAddress() + " Log ready. Requesting records from device");
        Logger.d(mLogSession, "Log ready. Requesting records from device");

        final BluetoothGattCharacteristic racpCharacteristic = mRecordAccessControlPointCharacteristic;
        if (racpCharacteristic != null) {
            setOpCode(racpCharacteristic, OP_CODE_REPORT_STORED_RECORDS, OPERATOR_ALL_RECORDS);
            writeCharacteristic(racpCharacteristic);
        }
    }

    private String devicePrefName(String pref) {
        return pref + "_" + mBluetoothDevice;
    }

    void setClickThreshold(float min, float max) {
        Log.d(TAG, "New threshold " + min + " " + max);

        threshold_low = min;
        threshold_high = max;

        SharedPreferences sharedPref = getContext().getSharedPreferences(EmgImuService.SERVICE_PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putFloat(devicePrefName(EmgImuService.THRESHOLD_LOW_PREFERENCE), threshold_low);
        editor.putFloat(devicePrefName(EmgImuService.THRESHOLD_HIGH_PREFERENCE), threshold_high);
        editor.apply();
    }

    private void loadThreshold() {
        SharedPreferences sharedPref = getContext().getSharedPreferences(EmgImuService.SERVICE_PREFERENCES, Context.MODE_PRIVATE);
        threshold_low = sharedPref.getFloat(devicePrefName(EmgImuService.THRESHOLD_LOW_PREFERENCE), threshold_low);
        threshold_high = sharedPref.getFloat(devicePrefName(EmgImuService.THRESHOLD_HIGH_PREFERENCE), threshold_high);

        Log.d(TAG, "Loaded threshold " + threshold_low + " " + threshold_high);
    }

    void setPwrRange(float min, float max) {
        Log.d(TAG, "New range " + min + " " + max);

        min_pwr = min;
        max_pwr = max;

        SharedPreferences sharedPref = getContext().getSharedPreferences(EmgImuService.SERVICE_PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putFloat(devicePrefName(EmgImuService.MIN_PWR_PREFERENCE), min_pwr);
        editor.putFloat(devicePrefName(EmgImuService.MAX_PWR_PREFERENCE), max_pwr);
        editor.apply();
    }

    private void loadPwrRange() {
        SharedPreferences sharedPref = getContext().getSharedPreferences(EmgImuService.SERVICE_PREFERENCES, Context.MODE_PRIVATE);
        min_pwr = sharedPref.getFloat(devicePrefName(EmgImuService.MIN_PWR_PREFERENCE), min_pwr);
        max_pwr = sharedPref.getFloat(devicePrefName(EmgImuService.MAX_PWR_PREFERENCE), max_pwr);

        Log.d(TAG, "Loaded range " + min_pwr + " " + max_pwr + " From preference: " + devicePrefName(EmgImuService.MIN_PWR_PREFERENCE));
    }

    float getHighThreshold() {
        return threshold_high;
    }

    float getMinPwr() {
        return min_pwr;
    }

    float getMaxPwr() {
        return max_pwr;
    }

    public double getBatteryVoltage() {
        int battery = getBatteryValue();
        if (battery == -1)
            return -1;

        // Hardcoded conversion based on the firmware
        double voltage = 3.0 + 1.35 * (battery / 100.0);
        return voltage;
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

    public boolean isDeviceReady() {
        return mReady;
    }

    public String getLoggingRef() {
        if (mLogging == false || streamLogger == null) {
            return "";
        }

        return streamLogger.getReference();
    }

    public int getChannelCount() {
        return mChannels;
    }
}
