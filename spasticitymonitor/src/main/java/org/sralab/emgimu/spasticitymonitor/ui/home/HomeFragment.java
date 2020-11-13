package org.sralab.emgimu.spasticitymonitor.ui.home;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import org.sralab.emgimu.spasticitymonitor.R;
import org.sralab.emgimu.spasticitymonitor.SpasticityViewModel;

import java.util.List;

public class HomeFragment extends Fragment {

    private SpasticityViewModel spasticityViewModel;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        spasticityViewModel = new ViewModelProvider.AndroidViewModelFactory(getActivity().getApplication()).create(SpasticityViewModel.class);

        View root = inflater.inflate(R.layout.fragment_home, container, false);
        final TextView pwrView = root.findViewById(R.id.text_pwr);
        spasticityViewModel.getEmgPwr().observe(getViewLifecycleOwner(), new Observer<Integer>() {
            @Override
            public void onChanged(@Nullable Integer s) {
                pwrView.setText("Power: " + Integer.toString(s));
            }
        });

        final TextView quatView = root.findViewById(R.id.text_quat);
        spasticityViewModel.getImuQuat().observe(getViewLifecycleOwner(), new Observer<List<Float>>() {
            @Override
            public void onChanged(@Nullable List<Float> l) {
                quatView.setText("Quat: " + l.get(0).toString() + " " + l.get(1).toString() + " " + l.get(2).toString() + " " + l.get(3).toString());
                Log.d("IMU", l.toString());
            }
        });
        return root;
    }
}
