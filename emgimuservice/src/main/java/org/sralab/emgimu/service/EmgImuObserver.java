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

public interface EmgImuObserver {

    // Callback for battery updates
    void onBatteryReceived(final BluetoothDevice device, float battery);

    // Callbacks for EMG updates
    void onEmgStreamReceived(BluetoothDevice device, long ts_ms, double[][] data);
    void onEmgPwrReceived(final BluetoothDevice device, long ts_ms, int value);
    //void onEmgClick(final BluetoothDevice device);

    // Callbacks for IMU updates
    // TODO: parse this and set up callbacks
    void onImuAccelReceived(final BluetoothDevice device, float[][] accel);
    void onImuGyroReceived(final BluetoothDevice device, float[][] gyro);
    void onImuMagReceived(final BluetoothDevice device, float[][] mag);
    void onImuAttitudeReceived(final BluetoothDevice device, float[] quaternion);

    // Callbacks for Force updates
    void onForceReceived(final BluetoothDevice device, long ts_ms, int force);
}
