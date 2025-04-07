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
    private boolean isOnline = false;
    private PipedOutputStream pos;
    private PipedInputStream pis;

    public FirebaseWriter(Context context, String suffix, String basepath, String subpath) {
        this.context = context;
        this.suffix = suffix;
        if (suffix == null)
            this.suffix = "";
        this.basepath = basepath;
        this.subpath = subpath;

        // Check for internet connectivity first
        isOnline = isNetworkAvailable();

        if (isOnline) {
            // Only try to use Firebase if we're online
            FirebaseAuth mAuth = FirebaseAuth.getInstance();
            user = mAuth.getCurrentUser(); // Log in performed by main service

            if (user == null) {
                mAuth.addAuthStateListener(firebaseAuth -> {
                    user = firebaseAuth.getCurrentUser();
                    if (user != null) {
                        createLog(user);
                    } else {
                        // No user but we still want to create a local log
                        createLocalOnlyLog();
                    }
                });
            } else {
                createLog(user);
            }
        } else {
            // If offline, only set up local logging
            createLocalOnlyLog();
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void createLocalOnlyLog() {
        Log.d(TAG, "Creating local-only log due to no internet connection or no Firebase user");

        // File name is UTC
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyyMMdd-HHmmss'Z'"); // Quoted "Z" to indicate UTC, no timezone offset
        df.setTimeZone(tz);
        dateName = df.format(new Date()) + suffix;

        // Set up handler thread for logging
        HandlerThread t = new HandlerThread("Logging") {};
        t.start();
        handler = new Handler(t.getLooper());

        // Set up local file only
        try {
            File file = new File(context.getExternalFilesDir("stream_logs"), getLocalFilename());
            String fileName = file.getAbsolutePath();
            localWriter = new FileOutputStream(fileName);
            Log.d(TAG, "Opened: " + fileName);

            localWriter = new GZIPOutputStream(localWriter);
            localWriter.write("[".getBytes());

            // Set up a local dataStream for consistency with the original code
            dataStream = localWriter;
            firstEntry = true;

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void createLog(FirebaseUser user) {
        if (user == null) {
            createLocalOnlyLog();
            return;
        }

        // File name is UTC
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyyMMdd-HHmmss'Z'"); // Quoted "Z" to indicate UTC, no timezone offset
        df.setTimeZone(tz);
        dateName = df.format(new Date()) + suffix;

        FirebaseStorage storage = FirebaseStorage.getInstance();
        storageRef = storage.getReference().child(getFilename());

        // Create stream objects and use buffers to prevent deadlocks
        pos = new PipedOutputStream();
        pis = new PipedInputStream();

        try {
            // Connect streams
            pis.connect(pos);

            // Use in stream compression
            dataStream = new GZIPOutputStream(pos);
            dataStream.write("[".getBytes());
            firstEntry = true;
        } catch (IOException e) {
            e.printStackTrace();
            // If Firebase streaming setup fails, fall back to local only
            createLocalOnlyLog();
            return;
        }

        HandlerThread t = new HandlerThread("Logging") {};
        t.start();
        handler = new Handler(t.getLooper());

        InputStream logStream = pis;

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
        handler.post(() -> {
            try {

                if (dataStream != null) {
                    dataStream.write("]".getBytes());
                    dataStream.close();
                }

                if (localWriter != null && localWriter != dataStream) {
                    localWriter.write("]".getBytes());
                    localWriter.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
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
                // Only write to dataStream if it's different from localWriter
                // (when we're in online mode with Firebase)
                if (dataStream != null) {
                    dataStream.write(msg.getBytes());
                }

                // Always write to local file
                if (localWriter != null) {
                    localWriter.write(msg.getBytes());
                }
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
        if (user == null) {
            return "fallback";  // This should never be used when offline
        }

        if (subpath == null) {
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
        return storageRef != null ? storageRef.getPath() : "local-only";
    }

    public boolean isOnline() {
        return isOnline;
    }
}