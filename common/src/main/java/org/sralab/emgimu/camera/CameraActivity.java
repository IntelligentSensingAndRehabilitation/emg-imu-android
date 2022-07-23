package org.sralab.emgimu.camera;

import android.os.Handler;

import java.io.File;

public interface CameraActivity {

    File createNewFile(String suffix);
    String getSimpleFilename(File currentFile);
    void showVideoStatus(String status, String color);
    void pushVideoFileToFirebase(File currentFile, Long exposureOfFirstFrameTimestamp, Long startRecordingTimestamp);
    Handler getBackgroundHandler();
    boolean checkPermissions();
    void startBackgroundThread();
}
