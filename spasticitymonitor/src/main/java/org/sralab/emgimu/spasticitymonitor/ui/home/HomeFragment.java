package org.sralab.emgimu.spasticitymonitor.ui.home;

import android.os.Bundle;
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

public class HomeFragment extends Fragment {

    private SpasticityViewModel spasticityViewModel;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        spasticityViewModel = new ViewModelProvider.AndroidViewModelFactory(getActivity().getApplication()).create(SpasticityViewModel.class);

        View root = inflater.inflate(R.layout.fragment_home, container, false);
        final TextView textView = root.findViewById(R.id.text_home);
        spasticityViewModel.getEmgPwr().observe(getViewLifecycleOwner(), new Observer<Integer>() {
            @Override
            public void onChanged(@Nullable Integer s) {
                textView.setText("Power: " + Integer.toString(s));
            }
        });
        return root;
    }
}
