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
import org.sralab.emgimu.visualization.VectorGraphView;

import java.util.Arrays;

public class Controller extends AppCompatActivity {

    private static final String TAG = Controller.class.getSimpleName();

    private EmgDecoder emgDecoder = null;

    private VectorGraphView inputGraph;
    private VectorGraphView outputGraph;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_controller);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        ViewGroup decoder_inputs = findViewById(R.id.decoder_inputs);
        inputGraph = new VectorGraphView(decoder_inputs.getContext(), decoder_inputs, EmgDecoder.CHANNELS);
        inputGraph.setWindowSize(250);
        inputGraph.setRange(10);

        ViewGroup decoder_outputs = findViewById(R.id.decoder_outputs);
        outputGraph = new VectorGraphView(decoder_outputs.getContext(), decoder_outputs, EmgDecoder.EMBEDDINGS_SIZE);
        outputGraph.setWindowSize(250);
        outputGraph.setRange(10);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Log.d(TAG, "Processing");
                float coordinates[] = new float[EmgDecoder.EMBEDDINGS_SIZE];

                for (int i = 0; i < 100; i++) {
                    float data[] = {1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f};


                    emgDecoder.decode(data, coordinates);

                    inputGraph.addValue(data);
                    outputGraph.addValue(coordinates);
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
