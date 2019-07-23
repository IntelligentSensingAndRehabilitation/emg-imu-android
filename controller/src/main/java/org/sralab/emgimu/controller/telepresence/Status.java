package org.sralab.emgimu.controller.telepresence;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Status {

    @SerializedName("status")
    @Expose
    private String status;
    @SerializedName("motor1")
    @Expose
    private Float motor1;
    @SerializedName("motor2")
    @Expose
    private Float motor2;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Float getMotor1() {
        return motor1;
    }

    public void setMotor1(Float motor1) {
        this.motor1 = motor1;
    }

    public Float getMotor2() {
        return motor2;
    }

    public void setMotor2(Float motor2) {
        this.motor2 = motor2;
    }

    @Override
    public String toString() {
        return "Status{" +
                "status='" + status + '\'' +
                ", motor1=" + motor1 +
                ", motor2=" + motor2 +
                '}';
    }
}