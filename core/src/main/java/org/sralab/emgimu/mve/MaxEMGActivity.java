package org.sralab.emgimu.mve;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.sralab.emgimu.config.R;

import no.nordicsemi.android.nrftoolbox.widget.DividerItemDecoration;

public class MaxEMGActivity extends AppCompatActivity {

    private final static String TAG = MaxEMGActivity.class.getSimpleName();

    RecyclerView recyclerView;
    DeviceAdapter deviceAdapter;
    DeviceViewModel dvm;
    
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_max_emg);

        recyclerView = findViewById(R.id.recycler_view);

        dvm = new ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication())).get(DeviceViewModel.class);

        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));

        recyclerView.setAdapter(deviceAdapter = new DeviceAdapter(this, dvm));
    }
}
