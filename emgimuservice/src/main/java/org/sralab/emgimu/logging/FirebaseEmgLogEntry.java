package org.sralab.emgimu.logging;

import android.util.Log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

public class FirebaseEmgLogEntry {

    private static String TAG = FirebaseEmgLogEntry.class.getName();

    private static int MAX_LOG_SIZE_MS = (60 * 60 * 1000); // ms in an hour

    private long T0;

    private ArrayList<Double> timestamps;
    private ArrayList<Double> emgPower;

    public FirebaseEmgLogEntry() {
        this.timestamps = new ArrayList<Double>();
        this.emgPower = new ArrayList<Double>();

    }

    public static Date DateFromTimestamp(long timestamp) {
        // Coerce the first timestamp of each block to the even hour for this
        // timestamp and use this as the "T0" for block. This should make the
        // document name and T0 internally consistent.
        Calendar calendar = new GregorianCalendar();
        calendar.setTimeInMillis(timestamp);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        return calendar.getTime();
    }

    public static String FilenameFromTimestamp(long timestamp)
    {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyyMMdd-HH'Z'"); // Quoted "Z" to indicate UTC, no timezone offset
        df.setTimeZone(tz);

        Date D = DateFromTimestamp(timestamp);
        return df.format(D);
    }

    /**
     * Add new sample to the log array. Note that this is not very efficient and
     * is mostly for testing purposes.
     * @param timestamp new sample timestamp
     * @param emgPower new sample power
     */
    public void addSample(long timestamp, double emgPower) throws LogFull {

        if (timestamps.isEmpty()) {
            T0 = DateFromTimestamp(timestamp).getTime();
        }

        double delta = timestamp - T0;

        // Indicate if this value should not be added to this document
        if (delta > FirebaseEmgLogEntry.MAX_LOG_SIZE_MS || delta < 0) {
            throw new LogFull("Sample out of log window");
        }

        this.timestamps.add(delta);
        this.emgPower.add(emgPower);
    }

    /**
     * Documents are named based on the hour of the first sample.
     */
    public String DocumentName() {
        return FilenameFromTimestamp(T0);
    }


    public class LogFull extends Exception {
        public LogFull(String m) {
            super(m);
        }
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

    //! Return the array of timestamps for individual samples
    public ArrayList<Double> getTimestamps() {
        return timestamps;
    }

    //! Set the timestamps (used when restoring from DB)
    public void setTimestamps(ArrayList<Double> timestamps) {
        this.timestamps = timestamps;
    }

    //!Get the array of EMG power
    public ArrayList<Double> getEmgPower() {
        return emgPower;
    }

    //! Set the EMG power (used when restoring from DB)
    public void setEmgPower(ArrayList<Double> emgPower) {
        this.emgPower = emgPower;
    }

    public double getMean() {
        double sum = 0;
        for (int i = 0; i < emgPower.size(); i++)
            sum += emgPower.get(i);
        return sum / emgPower.size();
    }

    public void setMean(double v) { /* Do nothing */ }

    public double getVar() {
        double m = getMean();

        double ss = 0;
        for (int i = 0; i < emgPower.size(); i++)
            ss += (emgPower.get(i) - m) * (emgPower.get(i) - m);
        return ss / emgPower.size();
    }

    public void setVar(double v) { /* Do nothing */ }

    public int getSamples() {
        return emgPower.size();
    }
    public void setSamples(int v) { /* Do nothing */ }

}
