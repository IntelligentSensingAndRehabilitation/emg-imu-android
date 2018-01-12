package org.sralab.emgimu.service;

/**
 * Created by jcotton81 on 1/11/18.
 */

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

public class EmgLogRecord implements Parcelable{

    /** The base time of the measurement (start time + sequenceNumber of minutes). */
    public long timestamp;

    /** The glucose concentration in mg/dL. */
    public List<Integer> emgPwr;

    protected EmgLogRecord(long timestamp, List<Integer> emgPwr) {
        this.timestamp = timestamp;
        this.emgPwr = emgPwr;
    }

    protected EmgLogRecord(Parcel in) {
        this.timestamp = in.readLong();
        this.emgPwr = new ArrayList<Integer>();
        in.readList(this.emgPwr, null);
    }

    public static final Creator<EmgLogRecord> CREATOR = new Creator<EmgLogRecord>() {
        @Override
        public EmgLogRecord createFromParcel(Parcel in) {
            return new EmgLogRecord(in);
        }

        @Override
        public EmgLogRecord[] newArray(int size) {
            return new EmgLogRecord[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeLong(timestamp);
        parcel.writeList(emgPwr);
    }
}