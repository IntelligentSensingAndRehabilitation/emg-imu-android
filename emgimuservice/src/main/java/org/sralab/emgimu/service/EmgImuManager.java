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
import android.os.Bundle;
import android.util.Log;

import com.google.firebase.analytics.FirebaseAnalytics;

import org.sralab.emgimu.logging.FirebaseEmgLogger;
import org.sralab.emgimu.parser.RecordAccessControlPointParser;

import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import no.nordicsemi.android.log.Logger;
import no.nordicsemi.android.ble.BleManager;

public class EmgImuManager extends BleManager<EmgImuManagerCallbacks> {
	private final String TAG = "EmgImuManager";

	/** EMG Service UUID **/
	public final static UUID EMG_SERVICE_UUID = UUID.fromString("00001234-1212-EFDE-1523-785FEF13D123");
    public final static UUID EMG_RAW_CHAR_UUID = UUID.fromString("00001235-1212-EFDE-1523-785FEF13D123");
    public final static UUID EMG_BUFF_CHAR_UUID = UUID.fromString("00001236-1212-EFDE-1523-785FEF13D123");
    public final static UUID EMG_PWR_CHAR_UUID = UUID.fromString("00001237-1212-EFDE-1523-785FEF13D123");
    /** IMU Service UUID **/
    public final static UUID IMU_SERVICE_UUID = UUID.fromString("00002234-1212-EFDE-1523-785FEF13D123");
    public final static UUID IMU_ACCEL_CHAR_UUID = UUID.fromString("00002235-1212-EFDE-1523-785FEF13D123");

    private final int EMG_BUFFER_LEN = (20 / 2); // elements in UINT16

    private BluetoothGattCharacteristic mEmgRawCharacteristic, mEmgBuffCharacteristic, mEmgPwrCharacteristic, mImuAccelCharacteristic;

    /**
     * Record Access Control Point characteristic UUID
     */
    private final static UUID EMG_RACP_CHAR_UUID = UUID.fromString("00002a52-0000-1000-8000-00805f9b34fb");
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
    private List<EmgLogRecord> mRecords = new ArrayList<>();
    private boolean mAbort;

    private BluetoothGattCharacteristic mRecordAccessControlPointCharacteristic, mEmgLogCharacteristic;

    enum CHARACTERISTIC_TYPE {
        EMG_RAW,
        EMG_BUFF,
        EMG_PWR,
        EMG_RACP,
        EMG_LOG,
        UNKNOWN
    };

	public EmgImuManager(final Context context) {
		super(context);
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
        createBond();
        super.connect(device);
        fireLogger = new FirebaseEmgLogger(this);
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

            requests.add(Request.newEnableIndicationsRequest(mRecordAccessControlPointCharacteristic));
            requests.add(Request.newEnableNotificationsRequest(mEmgLogCharacteristic));

            mStreamingMode = STREAMING_MODE.STREAMINNG_POWER;
			return requests;
		}

		@Override
		protected boolean isRequiredServiceSupported(final BluetoothGatt gatt) {
			final BluetoothGattService llService = gatt.getService(EMG_SERVICE_UUID);
			if (llService != null) {
                mEmgRawCharacteristic = llService.getCharacteristic(EMG_RAW_CHAR_UUID);
                mEmgBuffCharacteristic = llService.getCharacteristic(EMG_BUFF_CHAR_UUID);
                mEmgPwrCharacteristic  = llService.getCharacteristic(EMG_PWR_CHAR_UUID);
                mRecordAccessControlPointCharacteristic = llService.getCharacteristic(EMG_RACP_CHAR_UUID);
                mEmgLogCharacteristic = llService.getCharacteristic(EMG_LOG_CHAR_UUID);

                for (BluetoothGattCharacteristic c : llService.getCharacteristics() ) {
                    Log.v(TAG, "Found: " + c.getUuid());
                }
            }
			return (mEmgRawCharacteristic != null) && (mEmgPwrCharacteristic != null) && (mEmgBuffCharacteristic != null)
                    && (mEmgLogCharacteristic != null) && (mRecordAccessControlPointCharacteristic != null);
		}

		@Override
		protected boolean isOptionalServiceSupported(final BluetoothGatt gatt) {
			final BluetoothGattService iaService = gatt.getService(IMU_SERVICE_UUID);
			if (iaService != null) {
                mImuAccelCharacteristic = iaService.getCharacteristic(IMU_ACCEL_CHAR_UUID);
			}
            for (BluetoothGattCharacteristic c : iaService.getCharacteristics() ) {
                Log.v(TAG, "Optional Char Found: " + c.getUuid());
            }
			return mImuAccelCharacteristic != null;
		}

		@Override
		protected void onDeviceDisconnected() {
            mImuAccelCharacteristic = null;
            mEmgPwrCharacteristic = null;
            mEmgBuffCharacteristic = null;
            mEmgLogCharacteristic = null;
            mRecordAccessControlPointCharacteristic = null;
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
                    // Have to manually combine to get the endian right
                    int pwr_val = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0) +
                            characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1) * 256;
                    mEmgPwr = pwr_val;
                    mCallbacks.onEmgPwrReceived(device, mEmgPwr);
                    checkEmgClick(gatt.getDevice(), pwr_val);
                    break;
                case EMG_BUFF:
                    // Have to manually combine to get the endian right
                    byte [] buffer = characteristic.getValue();
                    int [] parsed = new int[EMG_BUFFER_LEN];
                    for (int i = 0; i < EMG_BUFFER_LEN; i++)
                        parsed[i] = buffer[i + 1] * 256 + buffer[i];
                    mEmgBuff = parsed;
                    mCallbacks.onEmgBuffReceived(device, mEmgBuff);
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

                        Log.d(TAG, "Timestamp set");
                        Logger.d(mLogSession, "Timestamp set. Requesting all records.");

                        clear();
                        mCallbacks.onOperationStarted(mBluetoothDevice);

                        setOpCode(mRecordAccessControlPointCharacteristic, OP_CODE_REPORT_NUMBER_OF_RECORDS, OPERATOR_ALL_RECORDS);
                        writeCharacteristic(mRecordAccessControlPointCharacteristic);
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

    private long t0() {
        return new GregorianCalendar(2018, 0, 0).getTime().getTime();
    }
    /**
     * Pass the current time into the sensor. This is in a strange format to keep thinks
     * in range given the sensor timer running at 8hz. We will use 8hz units since the
     * beginning of 2018. This is set on the sensor by writing to the logging characteristic.
     * It will only sync the first time this is written since power up.
     */
    private void syncDevice() {
        final int dt = (int) nowToTimestamp();

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
    public void getAllRecords() {

        if (mRecordAccessControlPointCharacteristic == null)
            return;

        Log.d(TAG, "getAllRecords()");
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

    // Handle the two streaming modes for EMG data (raw buffered data or processed power)

    public enum STREAMING_MODE {
        STREAMINNG_POWER,
        STREAMING_BUFFERED
    };

    private STREAMING_MODE mStreamingMode;
    public final void enableBufferedStreamingMode() {
        disableEmgPwrNotifications();
        enableEmgBuffNotifications();
        mStreamingMode = STREAMING_MODE.STREAMING_BUFFERED;
    }

    public final void enablePowerStreamingMode() {
        disableEmgBuffNotifications();
        enableEmgPwrNotifications();
        mStreamingMode = STREAMING_MODE.STREAMINNG_POWER;
    }

    public STREAMING_MODE getStreamingMode() { return mStreamingMode; }

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

    private double MAX_SCALE = 0x1500; // TODO: this should be user calibrate-able, or automatic
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
    // TODO: these should be in preferences or automatic
    private long THRESHOLD_TIME_NS = 500 * (int)1e6; // 500 ms
    private final int HIGH_THRESHOLD = 2000;
    private final int LOW_THRESHOLD = 1000;
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

        if (value > HIGH_THRESHOLD && overThreshold == false && refractory) {
            mThresholdTime = eventTime; // Store this time
            overThreshold = true;
            mCallbacks.onEmgClick(device);
        } else if (value < LOW_THRESHOLD && overThreshold == true) {
            overThreshold = false;
        }
    }

    //! Return if this is the EMG PWR characteristic
    private boolean isEmgPwrCharacteristic(final BluetoothGattCharacteristic characteristic) {
        if (characteristic == null)
            return false;

        return EMG_PWR_CHAR_UUID.equals(characteristic.getUuid());
    }

    // Accessors for the EMG buffer
    private int [] mEmgBuff;
    public int[] getEmgBuff() {
        return mEmgBuff;
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
        setOpCode(racpCharacteristic, OP_CODE_REPORT_STORED_RECORDS, OPERATOR_ALL_RECORDS);
        writeCharacteristic(racpCharacteristic);
    }

}
