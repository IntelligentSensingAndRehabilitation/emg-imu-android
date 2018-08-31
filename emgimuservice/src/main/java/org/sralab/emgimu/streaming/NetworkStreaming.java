package org.sralab.emgimu.streaming;

import android.bluetooth.BluetoothDevice;
import android.util.Log;

import com.google.gson.Gson;

import org.sralab.emgimu.streaming.messages.EmgPwrMessage;
import org.sralab.emgimu.streaming.messages.EmgRawMessage;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class NetworkStreaming {

    private final static String TAG = NetworkStreaming.class.getSimpleName();

    /***
     * Handles a socket interface to a server that will receive
     * real time streaming from sensors connected to the service.
     * This is a unidirectional stream and the server just receives
     * data and does not alter the behavior of the sensors (at least
     * for now).
     *
     * Data format will be as follows:
     *   sync byte (0x0C)
     *   packet_size (1 byte)
     *   sensor_id (either byte numerical ID or bluetooth MAC address)
     *   data_type
     *      0x00 EMG raw
     *      0x01 EMG power
     *      0x80 IMU Accel
     *      0x81 IMU Gyro
     *      0x82 IMU Mag
     *    payload - format based on data_type
     */

    private ClientThread clientThread;
    private Thread socketThread;

    public NetworkStreaming() {
        Log.d(TAG, "Streaming initialized.");
    }

    public void start() {
        Log.d(TAG, "Socket thread started");
        clientThread = new ClientThread();
        socketThread = new Thread(clientThread);
        socketThread.start();
    }

    public void stop() {
        Log.d(TAG, "Stopping socket thread");
        mRun = false;
        socketThread.interrupt();
        try {
            socketThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public boolean isConnected() {
        return clientThread.isConnected();
    }

    public void streamEmgBuffer(BluetoothDevice dev,
                                long time,
                                int samples,
                                int channels,
                                double[][] data) {

        Gson gson = new Gson();
        EmgRawMessage msg = new EmgRawMessage(dev.getAddress(), time, channels, samples, data);
        String json = gson.toJson(msg);
        clientThread.write(json.getBytes());
    }

    public void streamEmgPwr(BluetoothDevice dev,
                                long time,
                                double data) {

        Gson gson = new Gson();
        EmgPwrMessage msg = new EmgPwrMessage(dev.getAddress(), time, data);
        String json = gson.toJson(msg);
        clientThread.write(json.getBytes());
    }

    private boolean mRun;

    class ClientThread implements Runnable {

        private Socket socket;
        private static final int SERVERPORT = 5000;
        private static final String SERVER_IP = "192.168.1.83";

        private OutputStream outputStream;

        @Override
        public void run() {

            mRun = true;

            Log.d(TAG, "Opening socket. IP: " + SERVER_IP + " Port: " + SERVERPORT);

            try {
                InetAddress serverAddr = InetAddress.getByName(SERVER_IP);

                socket = new Socket(serverAddr, SERVERPORT);
                outputStream = socket.getOutputStream();

                Log.d(TAG, "Streaming socket opened");

                while(mRun) {
                    synchronized (outputStream) {
                        try {
                            outputStream.wait(10);
                        } catch (InterruptedException e) {
                            Log.d(TAG, "Interruption indicates stopping requested.");
                        }
                    }
                }

                socket.close();
                Log.d(TAG, "Streaming socket closed");

            } catch (UnknownHostException e1) {
                e1.printStackTrace();
            } catch (IOException e1) {
                e1.printStackTrace();
            }

        }

        void write(byte[] b) {
            try {
                Log.d(TAG, "Writing " + b.length + " bytes and " + ByteBuffer.allocate(4).putInt(b.length).array().length);
                outputStream.write(ByteBuffer.allocate(4).putInt(b.length).array());
                outputStream.write(b);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        protected boolean isConnected() {
            return socket.isConnected();
        }

    }
}
