package org.sralab.emgimu.streaming.messages;

import java.util.Date;

public class TrackingXYCoordinate {

    public final String MSG = "TrackingXYCoordinate";
    public long timestamp;
    public float [] data;

    public TrackingXYCoordinate(float goal_x, float goal_y, float decoded_x, float decoded_y) {
        data = new float[4];
        timestamp = new Date().getTime();
        data[0] = goal_x;
        data[1] = goal_y;
        data[2] = decoded_x;
        data[3] = decoded_y;
    }
}
