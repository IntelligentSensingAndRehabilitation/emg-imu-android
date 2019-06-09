package org.sralab.emgimu.streaming.messages;

import java.util.Date;

public class TrackingXYCoordinate {

    public final String MSG = "TrackingXYCoordinate";
    public long timestamp;
    public float [] data;

    public TrackingXYCoordinate(float x, float y) {
        data = new float[2];
        timestamp = new Date().getTime();
        data[0] = x;
        data[1] = y;
    }
}
