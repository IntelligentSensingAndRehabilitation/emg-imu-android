package org.sralab.emgimu.service;

import android.os.Parcel;
import android.os.Parcelable;

public class DataParcel implements Parcelable {

    int val;

    public DataParcel() {

    }

    public void writeVal(int val) {
        this.val = val;
    }

    public int readVal() {
        return val;
    }


    protected DataParcel(Parcel in) {
        val = in.readInt();
    }

    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(val);
    }

    public static final Creator<DataParcel> CREATOR = new Creator<DataParcel>() {
        @Override
        public DataParcel createFromParcel(Parcel in) {
            return new DataParcel(in);
        }

        @Override
        public DataParcel[] newArray(int size) {
            return new DataParcel[size];
        }
    };
}
