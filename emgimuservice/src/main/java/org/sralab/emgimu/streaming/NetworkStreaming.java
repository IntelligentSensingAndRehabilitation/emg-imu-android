package org.sralab.emgimu.streaming;

import android.bluetooth.BluetoothDevice;
import android.util.Log;

import com.google.gson.Gson;

import org.sralab.emgimu.streaming.messages.EmgPwrMessage;
import org.sralab.emgimu.streaming.messages.EmgRawMessage;
import org.sralab.emgimu.streaming.messages.TrackingXYCoordinate;

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
     * Sends data as simple JSON messages compatible with how it is
     * stored in the server logs.
     */

    private ClientThread clientThread;
    private Thread socketThread;

    public NetworkStreaming() {
        Log.d(TAG, "Streaming initialized.");
    }

    public void start(String ip_address, int port) {
        clientThread = new ClientThread(ip_address, port);
        socketThread = new Thread(clientThread);
        socketThread.start();
    }

    public void stop() {
        mRun = false;
        socketThread.interrupt();
        try {
            socketThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public boolean isConnected() {
        if (clientThread == null)
            return false;
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
                                double [] data) {

        Gson gson = new Gson();
        EmgPwrMessage msg = new EmgPwrMessage(dev.getAddress(), time, data);
        String json = gson.toJson(msg);
        clientThread.write(json.getBytes());
    }

    public void streamTrackingXY(float x, float y) {
        Gson gson = new Gson();
        TrackingXYCoordinate xy = new TrackingXYCoordinate(x, y);
        String json = gson.toJson(xy);
        clientThread.write(json.getBytes());
    }

    private boolean mRun;

    class ClientThread implements Runnable {

        private Socket socket;
        private int port;
        private String ip_address;

        private OutputStream outputStream;

        public ClientThread(String ip_address, int port) {
            this.ip_address = ip_address;
            this.port = port;
        }

        @Override
        public void run() {

            mRun = true;

            Log.d(TAG, "Opening socket. IP: " + this.ip_address + " Port: " + this.port);

            try {
                InetAddress serverAddr = InetAddress.getByName(this.ip_address);

                socket = new Socket(serverAddr, this.port);
                outputStream = socket.getOutputStream();

                while(mRun) {
                    synchronized (outputStream) {
                        try {
                            outputStream.wait(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

                Log.d(TAG, "Streaming socket");

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
            if (socket == null)
                return false;
            return socket.isConnected();
        }

    }
}
