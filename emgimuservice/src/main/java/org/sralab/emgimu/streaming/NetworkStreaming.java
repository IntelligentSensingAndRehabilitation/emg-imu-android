package org.sralab.emgimu.streaming;

import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;

import org.sralab.emgimu.streaming.messages.EmgPwrMessage;
import org.sralab.emgimu.streaming.messages.EmgRawMessage;
import org.sralab.emgimu.streaming.messages.TrackingXYCoordinate;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
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

    private HandlerThread handlerThread;
    private Handler handler;

    private Socket socket;
    private OutputStream outputStream;
    private DataInputStream inputStream;

    public NetworkStreaming() {
        Log.d(TAG, "Streaming initialized.");
    }

    public void start(String ip_address, int port) {
        handlerThread = new HandlerThread("NetworkStreaming Thread");
        handlerThread.start();
        Looper looper = handlerThread.getLooper();
        handler = new Handler(looper);

        handler.post(() -> {
            InetAddress serverAddr = null;
            try {
                serverAddr = InetAddress.getByName(ip_address);
            } catch (UnknownHostException e) {
                Log.e(TAG, "Unknown host. Unable to connect to server.");
                return;


            }

            try {
                socket = new Socket(serverAddr, port);
                socket.setSoTimeout(10);
                outputStream = socket.getOutputStream();
                inputStream = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "Unable to open network socket");
            }
        });
    }

    public void stop() {
        try {
            socket.close();
            socket = null;
        } catch (IOException e) {
            Log.e(TAG, "Unable to close socket");
        }

        handlerThread.interrupt();
        handlerThread = null;
        handler = null;
    }

    public boolean isConnected() {
        if (socket == null)
            return false;
        return socket.isConnected();
    }

    public interface MessageReceiver {
        void receiveMessage(byte [] msg);
    };

    int lastMessageSize = 0;

    public void checkForMessage(MessageReceiver receiver) {
        if (handler != null && isConnected()) {
            handler.post(() -> {
                try {

                    // First see if a message size is available, if needed
                    if (lastMessageSize == 0) {
                        if (inputStream.available() >= 4) {
                            lastMessageSize = inputStream.readInt();
                        }
                    }

                    // Then see if entire message has already arrived
                    if (lastMessageSize > 0 && inputStream.available() >= lastMessageSize) {

                        Log.d(TAG, "Data avilable on stream: " + lastMessageSize);
                        byte[] msg = new byte[lastMessageSize];
                        int data = inputStream.read(msg);
                        if (data != lastMessageSize) {
                            Log.e(TAG, "Did not receive expected size");
                            return;
                        }
                        if (receiver != null) {
                            lastMessageSize = 0; // Wait for next one
                            receiver.receiveMessage(msg);
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Could not read message from network stream", e);
                }
            });
        }
    }

    private void write(byte[] b) {
        if (handler != null) {
            handler.post(() -> {
                //Log.d(TAG, "Writing " + b.length + " bytes and " + ByteBuffer.allocate(4).putInt(b.length).array().length);
                try {
                    outputStream.write(ByteBuffer.allocate(4).putInt(b.length).array());
                    outputStream.write(b);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    public void streamEmgBuffer(BluetoothDevice dev,
                                long time,
                                int samples,
                                int channels,
                                double[][] data) {

        Gson gson = new Gson();
        EmgRawMessage msg = new EmgRawMessage(dev.getAddress(), time, channels, samples, data);
        String json = gson.toJson(msg);
        write(json.getBytes());
    }

    public void streamEmgPwr(BluetoothDevice dev,
                                long time,
                                double [] data) {

        Gson gson = new Gson();
        EmgPwrMessage msg = new EmgPwrMessage(dev.getAddress(), time, data);
        String json = gson.toJson(msg);
        write(json.getBytes());
    }

    public void streamTrackingXY(float goal_x, float goal_y, float decoded_x, float decoded_y, float position_x, float position_y, String mode) {
        Gson gson = new Gson();
        TrackingXYCoordinate xy = new TrackingXYCoordinate(goal_x, goal_y, decoded_x, decoded_y, position_x, position_y, mode);
        String json = gson.toJson(xy);
        write(json.getBytes());
    }

}
