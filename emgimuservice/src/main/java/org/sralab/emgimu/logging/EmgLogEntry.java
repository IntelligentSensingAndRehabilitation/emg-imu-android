package org.sralab.emgimu.logging;

import com.google.firebase.firestore.FieldValue;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

public class EmgLogEntry {

    private static int MAX_LOG_SIZE_MS = (60 * 60 * 1000); // ms in an hour

    private Date T0;
    //public FieldValue serverTimestamp;

    private ArrayList<Double> timestamps;
    private ArrayList<Double> emgPower;

    public EmgLogEntry() {
        this.timestamps = new ArrayList<Double>();
        this.emgPower = new ArrayList<Double>();

        //serverTimestamp = FieldValue.serverTimestamp();
    }

    /*public EmgLogEntry(double [] timestamps, double [] emgPower) {
        this.timestamps = timestamps.clone();
        this.emgPower = emgPower.clone();
    }*/

    /**
     * Add new sample to the log array. Note that this is not very efficient and
     * is mostly for testing purposes.
     * @param timestamp new sample timestamp
     * @param emgPower new sample power
     */
    public void addSample(long timestamp, double emgPower) throws LogFull {

        if (timestamps.isEmpty()) {

            // Coerce the first timestamp of each block to the even hour for this
            // timestamp and use this as the "T0" for block. This should make the
            // document name and T0 internally consistent.
            Calendar calendar = new GregorianCalendar();
            calendar.setTimeInMillis(timestamp);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            T0 = calendar.getTime();
        }

        double delta = timestamp - T0.getTime();

        // Indicate if this value should not be added to this document
        if (delta > EmgLogEntry.MAX_LOG_SIZE_MS || delta < 0) {
            throw new LogFull("Sample out of log window");
        }

        this.timestamps.add(delta);
        this.emgPower.add(emgPower);
    }

    /**
     * Documents are named based on the hour of the first sample.
     */
    public String DocumentName() {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyyMMdd-HH'Z'"); // Quoted "Z" to indicate UTC, no timezone offset
        df.setTimeZone(tz);
        return df.format(T0);
    }


    public class LogFull extends Exception {
        public LogFull(String m) {
            super(m);
        }
    }

    /******** DO NOT DELETE *********/
    // These "unused" getters are picked up by the Firestore set API and result
    // in the entries in the database.

    /*public FieldValue getServerTimestamp() {
        return FieldValue.serverTimestamp();
    }*/

    //! The "0"th sample timestamp in ms since 1970
    public Date getT0() {
        return T0;
    }

    //! Return the array of timestamps for individual samples
    public ArrayList<Double> getTimestamps() {
        return timestamps;
    }

    //!
    public ArrayList<Double> getEmgPower() {
        return emgPower;
    }

    public double getMean() {
        double sum = 0;
        for (int i = 0; i < emgPower.size(); i++)
            sum += emgPower.get(i);
        return sum / emgPower.size();
    }

    public double getVar() {
        double m = getMean();

        double ss = 0;
        for (int i = 0; i < emgPower.size(); i++)
            ss += (emgPower.get(i) - m) * (emgPower.get(i) - m);
        return ss / emgPower.size();
    }

    public int getSamples() {
        return emgPower.size();
    }

}
