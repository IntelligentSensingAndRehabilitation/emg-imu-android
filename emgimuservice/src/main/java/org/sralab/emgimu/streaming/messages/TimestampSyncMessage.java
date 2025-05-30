package org.sralab.emgimu.streaming.messages;

public class TimestampSyncMessage {

    public final String MSG = "TimestampSync";
    public String bluetoothMac;
    public long android_elapsed_nanos;
    public long timestamp_sync_milliseconds;

    public TimestampSyncMessage(String bluetoothMac,
                                long android_elapsed_nanos,
                                long timestamp_sync_milliseconds) {
        this.bluetoothMac = bluetoothMac;
        this.android_elapsed_nanos = android_elapsed_nanos;
        this.timestamp_sync_milliseconds = timestamp_sync_milliseconds;
    }
}
