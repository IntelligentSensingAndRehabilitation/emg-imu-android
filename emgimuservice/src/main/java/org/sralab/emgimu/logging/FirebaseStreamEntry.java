package org.sralab.emgimu.logging;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.reflect.Array;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

public class FirebaseStreamEntry {

    private static String TAG = FirebaseStreamEntry.class.getName();

    private long T0 = 0;
    protected ArrayList<Double> raw_timestamps;
    protected ArrayList<Double> raw_samples;
    protected ArrayList<Double> pwr_timestamps;
    protected ArrayList<Double> pwr_samples;

    public FirebaseStreamEntry() {
        this.raw_timestamps = new ArrayList<Double>();
        this.raw_samples = new ArrayList<Double>();
        this.pwr_timestamps = new ArrayList<Double>();
        this.pwr_samples = new ArrayList<Double>();
    }

    // Copy constructor
    public FirebaseStreamEntry(FirebaseStreamEntry base) {
        this.T0 = base.getT0().getTime();
        this.raw_timestamps = new ArrayList<>(base.raw_timestamps);
        this.raw_samples = new ArrayList<>(base.raw_samples);
        this.pwr_timestamps = new ArrayList<>(base.pwr_timestamps);
        this.pwr_samples = new ArrayList<>(base.pwr_samples);
    }

    public static Date DateFromTimestamp(long timestamp) {
        // Coerce the first timestamp of each block to the even hour for this
        // timestamp and use this as the "T0" for block. This should make the
        // document name and T0 internally consistent.
        Calendar calendar = new GregorianCalendar();
        calendar.setTimeInMillis(timestamp);
        calendar.set(Calendar.MILLISECOND, 0);

        return calendar.getTime();
    }

    public static String FilenameFromTimestamp(long timestamp)
    {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyyMMdd-HHmmss'Z'"); // Quoted "Z" to indicate UTC, no timezone offset
        df.setTimeZone(tz);

        Date D = DateFromTimestamp(timestamp);
        return df.format(D);
    }

    /**
     * Add new sample to the raw array.
     * @param timestamp new sample timestamp
     * @param sample new sample power
     */
    public boolean addRawSample(long timestamp, double sample) {
        if (T0 == 0) {
            T0 = DateFromTimestamp(timestamp).getTime();
        }

        double delta = timestamp - T0;

        this.raw_timestamps.add(delta);
        this.raw_samples.add(sample);

        return logFull();
    }

    /**
     * Add new sample to the pwr array.
     * @param timestamp new sample timestamp
     * @param sample new sample power
     */
    public boolean addPwrSample(long timestamp, double sample) {
        if (T0 == 0) {
            T0 = DateFromTimestamp(timestamp).getTime();
        }

        double delta = timestamp - T0;

        this.pwr_timestamps.add(delta);
        this.pwr_samples.add(sample);

        return logFull();
    }

    public int logSize() {
        return (this.pwr_samples.size() + this.raw_samples.size());
    }

    public boolean logFull() {
        return logSize() > 10000;
    }

    /**
     * Documents are named based on the hour of the first sample.
     */
    public String DocumentName() {
        return FilenameFromTimestamp(T0);
    }


    /******** DO NOT DELETE *********/
    // These "unused" getters are picked up by the Firestore set API and result
    // in the entries in the database.

    //! Accessor for the T0 of this log entry
    public Date getT0()
    {
        // Note in DB this is stored as a Date object
        Calendar calendar = new GregorianCalendar();
        calendar.setTimeInMillis(T0);
        return calendar.getTime();
    }

    //! Set the T0 for this log entry (when retrieving from the DB)
    public void setT0(Date T0_date)
    {
        Log.d(TAG, "Date retrieved from DB: " + T0_date);
        T0 = T0_date.getTime();
    }

    private String exportArray(ArrayList<Double> a) {
        GsonBuilder gsonBuilder = new GsonBuilder();
        Gson gson = gsonBuilder.create();
        String s = gson.toJson(a);
        return s;
    }

    //! Return the array of timestamps for raw samples
    public String getRawTimestamps() {
        return exportArray(raw_timestamps);
    }

    //! Return the array of individual raw samples
    public String getRawSamples() { return exportArray(raw_samples); }

    //! Return the array of timestamps for raw samples
    public String getPwrTimestamps() { return exportArray(pwr_timestamps); }

    //! Return the array of individual raw samples
    public String getPwrSamples() { return exportArray(pwr_samples); }

    //! Set the timestamps (used when restoring from DB)
    public void setRawTimestamps(ArrayList<Double> raw_timestamps) { this.raw_timestamps = raw_timestamps; }

    //! Set the raw samples (used when restoring from DB)
    public void setRawSamples(ArrayList<Double> raw_samples) {
        this.raw_samples = raw_samples;
    }

    //! Set the timestamps (used when restoring from DB)
    public void setPwrTimestamps(ArrayList<Double> pwr_timestamps) { this.pwr_timestamps = pwr_timestamps; }

    //! Set the raw samples (used when restoring from DB)
    public void setPwrSamples(ArrayList<Double> pwr_samples) {
        this.pwr_samples = pwr_samples;
    }


}
