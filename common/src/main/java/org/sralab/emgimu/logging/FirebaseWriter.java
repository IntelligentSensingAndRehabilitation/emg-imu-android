package org.sralab.emgimu.logging;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Observable;
import java.util.TimeZone;
import java.util.zip.GZIPOutputStream;

public class FirebaseWriter extends Observable {
    private static final String TAG = FirebaseWriter.class.getSimpleName();

    private Context context;
    private Handler handler;
    private FirebaseUser user;
    private StorageReference storageRef;

    private String suffix;
    private String dateName;
    private String subpath;
    private String basepath;

    private OutputStream localWriter;
    private OutputStream dataStream;
    private boolean firstEntry = false;

    public FirebaseWriter(Context context, String suffix, String basepath, String subpath) {

        this.context = context;
        this.suffix = suffix;
        if (suffix == null)
            this.suffix = "";
        this.basepath = basepath;
        this.subpath = subpath;

        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        user = mAuth.getCurrentUser(); // Log in performed by main service

        if (user == null) {
            mAuth.addAuthStateListener(firebaseAuth -> {
                user = firebaseAuth.getCurrentUser();
                createLog(user);
            });
        } else
            createLog(user);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    void createLog(FirebaseUser user) {
        if (user == null)
            throw new RuntimeException("No firebase user");

        // File name is UTC
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyyMMdd-HHmmss'Z'"); // Quoted "Z" to indicate UTC, no timezone offset
        df.setTimeZone(tz);
        dateName = df.format(new Date()) + suffix;

        FirebaseStorage storage = FirebaseStorage.getInstance();
        storageRef = storage.getReference().child(getFilename());

        // Create stream objects and use buffers to prevent deadlocks
        PipedOutputStream pos = new PipedOutputStream();
        PipedInputStream pis = new PipedInputStream();

        try {
            // Connect streams
            pis.connect(pos);

            // Use in stream compression
            dataStream = new GZIPOutputStream(pos);
            dataStream.write("[".getBytes());
            firstEntry = true;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        HandlerThread t = new HandlerThread("Logging") {};
        t.start();
        handler = new Handler(t.getLooper());

        InputStream logStream = pis;

        // only create upload task if online
        if (isNetworkAvailable()) {
            Log.d(TAG, "Creating upload task for " + getReference());

            UploadTask uploadTask = storageRef.putStream(logStream);
            uploadTask.addOnFailureListener(exception -> {
                Log.e(TAG, "Failed to upload: " + getReference(), exception);
                synchronized (this) {
                    this.notify();
                }
            }).addOnSuccessListener(taskSnapshot -> {
                Log.d(TAG, "Upload of log succeeded " + getReference() + " " + taskSnapshot.toString());
                synchronized (this) {
                    this.notify();
                }
                setChanged();
                notifyObservers();
            });
        }

        try {
            File file = new File(context.getExternalFilesDir("stream_logs"), getLocalFilename());
            String fileName = file.getAbsolutePath();
            localWriter = new FileOutputStream(fileName);
            Log.d(TAG, "Opened: " + fileName);

            localWriter = new GZIPOutputStream(localWriter);
            localWriter.write("[".getBytes());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        Log.d(TAG, "Closing PipedOutputStream");
        handler.post(() -> {
            try {
                Log.d(TAG, "Close started");

                if (dataStream != null) {
                    try {
                        dataStream.write("]".getBytes());
                        dataStream.flush();
                    } catch (IOException e) {
                        Log.e(TAG, "Error flushing dataStream", e);
                    } finally {
                        try {
                            dataStream.close();
                            Log.d(TAG, "dataStream closed");
                        } catch (IOException e) {
                            Log.e(TAG, "Error closing dataStream", e);
                        }
                    }
                }

                if (localWriter != null) {
                    try {
                        localWriter.write("]".getBytes());
                        localWriter.flush();
                    } catch (IOException e) {
                        Log.e(TAG, "Error flushing localWriter", e);
                    } finally {
                        try {
                            localWriter.close();
                            Log.d(TAG, "localWriter closed");
                        } catch (IOException e) {
                            Log.e(TAG, "Error closing localWriter", e);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error during close", e);
            }
        });
    }

    public class MsgWriteRunnable implements Runnable {

        private String msg;

        public MsgWriteRunnable(String msg) {
            this.msg = msg;
        }

        @Override
        public void run() {
            try {
                if (isNetworkAvailable()) {
                dataStream.write(msg.getBytes());
                }
                localWriter.write(msg.getBytes());
            } catch (IOException e) {
                Log.e(TAG, "Error writing to stream.", e);
            }
        }
    }

    synchronized
    public void addJson(String json) {
        if (firstEntry) {
            handler.post(new MsgWriteRunnable(json));
            firstEntry = false;
        } else
            handler.post(new MsgWriteRunnable(",\n" + json));
    }

    private String getFilename() {
        if (subpath == null) {
            Log.d(TAG, "No subpath");
            return basepath + "/" + user.getUid() + "/" + dateName + suffix + ".json.gz";
        } else
            return basepath + "/" + user.getUid() + "/" + subpath + "/" + dateName + suffix + ".json.gz";
    }

    private String getLocalFilename() {
        if (subpath == null)
            return dateName + suffix + ".json.gz";
        else
            return subpath + "_" + dateName + suffix + ".json.gz";
    }

    public String getReference() {
        return storageRef.getPath();
    }
}