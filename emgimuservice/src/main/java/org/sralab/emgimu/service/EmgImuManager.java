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

import java.util.Deque;
import java.util.LinkedList;
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

    enum CHARACTERISTIC_TYPE {
        EMG_RAW,
        EMG_BUFF,
        EMG_PWR,
        UNKNOWN
    };

	private BluetoothGattCharacteristic mEmgRawCharacteristic, mEmgBuffCharacteristic, mEmgPwrCharacteristic, mImuAccelCharacteristic;

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
    }

	/**
	 * BluetoothGatt callbacks for connection/disconnection, service discovery, receiving indication, etc
	 */
	private final BleManagerGattCallback mGattCallback = new BleManagerGattCallback() {

		@Override
		protected Deque<Request> initGatt(final BluetoothGatt gatt) {
			final LinkedList<Request> requests = new LinkedList<>();
            // When initially connected default to updates when the PWR is updated
            requests.add(Request.newEnableNotificationsRequest(mEmgPwrCharacteristic));
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
			}
			return (mEmgRawCharacteristic != null) && (mEmgPwrCharacteristic != null) && (mEmgBuffCharacteristic != null);
		}

		@Override
		protected boolean isOptionalServiceSupported(final BluetoothGatt gatt) {
			final BluetoothGattService iaService = gatt.getService(IMU_SERVICE_UUID);
			if (iaService != null) {
                mImuAccelCharacteristic = iaService.getCharacteristic(IMU_ACCEL_CHAR_UUID);
			}
			return mImuAccelCharacteristic != null;
		}

		@Override
		protected void onDeviceDisconnected() {
            mImuAccelCharacteristic = null;
            mEmgPwrCharacteristic = null;
            mEmgBuffCharacteristic = null;
		}

		@Override
		protected void onCharacteristicWrite(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
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
                case UNKNOWN:
                    Logger.a(mLogSession, "Received unknown characteristic: \"" + characteristic + "\"");
                    break;
            }
        }
	};

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

    private CHARACTERISTIC_TYPE getCharacteristicType(final BluetoothGattCharacteristic characteristic) {
        if (isEmgRawCharacteristic(characteristic))
            return CHARACTERISTIC_TYPE.EMG_RAW;
        if (isEmgPwrCharacteristic(characteristic))
            return CHARACTERISTIC_TYPE.EMG_PWR;
        if (isEmgBuffCharacteristic(characteristic))
            return CHARACTERISTIC_TYPE.EMG_BUFF;
        return CHARACTERISTIC_TYPE.UNKNOWN;
    }
}
