package org.sralab.emgimu.streaming.messages;

public class TrackingXYCoordinate {

    public final String MSG = "TrackingXYCoordinate";
    public long timestamp;
    public float x;
    public float y;

    public TrackingXYCoordinate(float x, float y) {
        this.x = x;
        this.y = y;
    }
}
