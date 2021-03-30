package org.sralab.emgimu.logging;

import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class GamePlayRecord  {
    String name;
    List<String> logReference;
    Timestamp startTime;
    Timestamp stopTime;
    double performance;
    String details;

    public String getName()
    {
        return name;
    }
    public void setName(String name) { this.name = name; }

    public Date getStartTime()
    {
        return startTime.toDate();
    }
    public void setStartTime(long time) { startTime = new Timestamp(new Date(time)); }

    public Date getStopTime()
    {
        if (stopTime == null)
            return getStartTime();

        return stopTime.toDate();
    }
    public void setStopTime(long time) { stopTime = new Timestamp(new Date(time)); }

    public long getDuration() {
        if (stopTime == null)
            return 0;

        return stopTime.toDate().getTime() - startTime.toDate().getTime();
    }

    public List<String> getLogReference() {
        if (logReference == null) {
            return new ArrayList<String>();
        }

        return logReference;
    }

    public void setLogReference(List<String> logReference) { this.logReference = logReference; }

    public double getPerformance() {
        return performance;
    }

    public void setPerformance(double performance) {
        this.performance = performance;
    }

    public String getDetails() {
        if (details == null) {
            return "{}";
        }

        return details;
    }
    public void setDetails(String details) { this.details = details; }

}