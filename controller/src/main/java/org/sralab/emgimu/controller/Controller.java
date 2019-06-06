package org.sralab.emgimu.controller;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import org.sralab.emgimu.visualization.LineGraphView;

import java.util.Arrays;

public class Controller extends AppCompatActivity {

    private static final String TAG = Controller.class.getSimpleName();

    private EmgDecoder emgDecoder = null;

    private LineGraphView inputGraph;
    private LineGraphView outputGraph;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_controller);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        ViewGroup decoder_inputs = findViewById(R.id.decoder_inputs);
        inputGraph = new LineGraphView(decoder_inputs.getContext(), decoder_inputs);
        inputGraph.setWindowSize(250);
        inputGraph.setRange(10);
        inputGraph.enableFiltering(true);

        ViewGroup decoder_outputs = findViewById(R.id.decoder_outputs);
        outputGraph = new LineGraphView(decoder_outputs.getContext(), decoder_outputs);
        outputGraph.setWindowSize(250);
        outputGraph.setRange(10);
        outputGraph.enableFiltering(true);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Log.d(TAG, "Processing");
                float coordinates[] = new float[emgDecoder.EMBEDDINGS_SIZE];

                for (int i = 0; i < 100; i++) {
                    float data[] = {1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f};


                    emgDecoder.decode(data, coordinates);

                    inputGraph.addValue(data[0]);
                    outputGraph.addValue(coordinates[0]);
                }

                inputGraph.repaint();
                outputGraph.repaint();

                Log.d(TAG, "Output: " + Arrays.toString(coordinates));
            }
        });

        try {
            emgDecoder = new EmgDecoder(this);
        } catch (Exception e) {
            Log.e(TAG, "Error: ", e);
        }
    }

}
