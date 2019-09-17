package org.sralab.emgimu.streaming.messages;

import java.util.Date;

public class TrackingXYCoordinate {

    public final String MSG = "TrackingXYCoordinate";
    public String mode;
    public long timestamp;
    public float [] data;

    public TrackingXYCoordinate(float goal_x, float goal_y, float decoded_x, float decoded_y, float position_x, float position_y, String mode) {
        data = new float[6];
        timestamp = new Date().getTime();
        data[0] = goal_x;
        data[1] = goal_y;
        data[2] = decoded_x;
        data[3] = decoded_y;
        data[4] = position_x;
        data[5] = position_y;
        this.mode = mode;
    }
}
