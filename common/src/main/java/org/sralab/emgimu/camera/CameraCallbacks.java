package org.sralab.emgimu.camera;

import android.os.Handler;

import java.io.File;

public interface CameraCallbacks {

    File createNewFile(String suffix);
    String getSimpleFilename(File currentFile);
    void showVideoStatus(String status, String color);
    void pushVideoFileToFirebase(File currentFile, long startTime, boolean depth);
    Handler getBackgroundHandler();
    boolean checkPermissions();
    int getDisplayRotation();
}
