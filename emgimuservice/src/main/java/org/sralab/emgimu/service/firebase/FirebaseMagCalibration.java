package org.sralab.emgimu.service.firebase;

import com.google.firebase.firestore.Blob;

import java.util.List;

public class FirebaseMagCalibration {

    public FirebaseMagCalibration() {

    }

    //@ServerTimestamp Timestamp date;
    public String path;
    public String sensor;
    public String uuid;
    public List<Float> Ainv;
    public List<Float> b;
    public float len_var;
    public Blob calibration_image;

}
