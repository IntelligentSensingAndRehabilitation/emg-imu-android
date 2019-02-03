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
import no.nordicsemi.android.ble.BleManagerCallbacks;

public interface EmgImuManagerCallbacks extends BleManagerCallbacks {

    // Callbacks for EMG updates
    void onEmgRawReceived(final BluetoothDevice device, int value);
    void onEmgBuffReceived(BluetoothDevice device, int count, double[][] data);
    void onEmgPwrReceived(final BluetoothDevice device, int value);
    void onEmgClick(final BluetoothDevice device);

    // Callbacks for IMU updates
    // TODO: parse this and set up callbacks
    void onImuAccelReceived(final BluetoothDevice device, float[][] accel);
    void onImuGyroReceived(final BluetoothDevice device, float[][] gyro);
    //void onImuMagReceived(final BluetoothDevice device, double[][] mag);
    void onImuAttitudeReceived(final BluetoothDevice device, float[] quaternion);

    // TODO: consider splitting this into two sets of callbacks since not
    // all listeners need to be able to handle retrieving logs
    /**
     * Called when new CGM value has been obtained from the sensor.
     */
    void onEmgLogRecordReceived(final BluetoothDevice device, final EmgLogRecord record);
    void onOperationStarted(final BluetoothDevice device);
    void onOperationCompleted(final BluetoothDevice device);
    void onOperationFailed(final BluetoothDevice device);
    void onOperationAborted(final BluetoothDevice device);
    void onOperationNotSupported(final BluetoothDevice device);
    void onDatasetClear(final BluetoothDevice device);
    void onNumberOfRecordsRequested(final BluetoothDevice device, final int value);
}
