package org.sralab.emgimu.unity_bindings;

//import needed packages / classes.
import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

public class Bridge extends Application
{
    // our native method, which will be called from Unity3D
    public void PrintString( final Context ctx, final String message )
    {
        //create / show an android toast, with message and short duration.
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}