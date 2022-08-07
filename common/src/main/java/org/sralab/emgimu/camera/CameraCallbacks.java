package org.sralab.emgimu.camera;

import android.os.Handler;

import java.io.File;
import java.util.ArrayList;

public interface CameraCallbacks {

    File createNewFile(String suffix);
    String getSimpleFilename(File currentFile);
    void showVideoStatus(String status, String color);
    void pushVideoFileToFirebase(File currentFile, long startTime, boolean depth, ArrayList<Long> timestamps);
    Handler getBackgroundHandler();
    boolean checkPermissions();
    int getDisplayRotation();
}
