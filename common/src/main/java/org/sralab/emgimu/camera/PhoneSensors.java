package org.sralab.emgimu.camera;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;

import org.sralab.emgimu.logging.FirebaseWriter;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PhoneSensors implements SensorEventListener {
    private static final String TAG = PhoneSensors.class.getSimpleName();

    private SensorManager sensorManager;
    private FirebaseWriter firebaseWriter;
    Gson gson;
    List<Sensor> sensors = new ArrayList();

    private final int [] sensorsIds = {
            Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_POSE_6DOF
    };

    Context context;
    
    public PhoneSensors(Context context, CameraCallbacks callbacks) {
        this.context = context;
        gson = new Gson();
    }

    public void startRecording() {
        firebaseWriter = new FirebaseWriter(context, "_phone_sensors", "phone_streams");
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        sensors = new ArrayList();

        for (int sensor : sensorsIds) {
            Sensor s = sensorManager.getDefaultSensor(sensor);
            sensors.add(s);

            // update at 60fps to match faster cameras
            sensorManager.registerListener(this, s, 16667);
        }
    }

    public void stopRecording() {
        sensorManager.unregisterListener(this);
        firebaseWriter.close();
        firebaseWriter = null;
    }

    public String getFirebasePath() {
        return firebaseWriter.getReference();
    }

    class SensorUpdate {
        String sensor_type;
        long sensor_timestamp;
        long timestamp;
        String values;

        public SensorUpdate(String sensor_type, long sensor_timestamp, long timestamp, float [] values) {
            this.sensor_type = sensor_type;
            this.sensor_timestamp = sensor_timestamp;
            this.timestamp = timestamp;

            ByteBuffer buf = ByteBuffer.allocate(Float.SIZE / Byte.SIZE * values.length);
            buf.asFloatBuffer().put(values);
            this.values = Base64.encodeToString(buf.array(), Base64.NO_WRAP);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

        /*switch(sensorEvent.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                Log.d(TAG, "Accelerometer: " + sensorEvent.timestamp + " " + Arrays.toString(sensorEvent.values));
                break;
            case Sensor.TYPE_GYROSCOPE:
                Log.d(TAG, "Gyroscope: " + sensorEvent.timestamp + " " + Arrays.toString(sensorEvent.values));
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                Log.d(TAG, "Magnetic: " + sensorEvent.timestamp + " " + Arrays.toString(sensorEvent.values));
                break;
            case Sensor.TYPE_ROTATION_VECTOR:
                Log.d(TAG, "Rotation vector: " + sensorEvent.timestamp + " " + Arrays.toString(sensorEvent.values));
                break;
            default:
                Log.d(TAG, sensorEvent.sensor.getStringType() + " " + sensorEvent.sensor.getName() + " " +
                        sensorEvent.timestamp + " " + Arrays.toString(sensorEvent.values));
                break;
        }
        Log.d(TAG, gson.toJson(update));
        */

        SensorUpdate update = new SensorUpdate(sensorEvent.sensor.getStringType(),
                sensorEvent.timestamp, new Date().getTime(), sensorEvent.values);
        firebaseWriter.addJson(gson.toJson(update));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}
