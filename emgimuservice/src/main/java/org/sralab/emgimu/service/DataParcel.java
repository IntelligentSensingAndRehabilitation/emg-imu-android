package org.sralab.emgimu.service;

import android.os.Parcel;
import android.os.Parcelable;

public class DataParcel implements Parcelable {

    protected DataParcel(Parcel in) {
    }

    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
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
