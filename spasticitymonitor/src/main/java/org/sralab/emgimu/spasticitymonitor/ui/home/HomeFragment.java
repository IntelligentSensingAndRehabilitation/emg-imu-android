package org.sralab.emgimu.spasticitymonitor.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.sralab.emgimu.spasticitymonitor.R;
import org.sralab.emgimu.spasticitymonitor.ui.stream_visualization.DeviceViewModel;
import org.sralab.emgimu.spasticitymonitor.ui.stream_visualization.StreamingAdapter;

import no.nordicsemi.android.nrftoolbox.widget.DividerItemDecoration;

public class HomeFragment extends Fragment {

    private DeviceViewModel dvm;
    private StreamingAdapter streamingAdapter;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_home, container, false);

        final RecyclerView recyclerView = root.findViewById(R.id.emg_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(root.getContext()));
        recyclerView.addItemDecoration(new DividerItemDecoration(root.getContext(), DividerItemDecoration.VERTICAL_LIST));

        dvm = new ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(getActivity().getApplication())).get(DeviceViewModel.class);
        dvm.getDevicesLiveData().observe(getViewLifecycleOwner(), devices -> streamingAdapter.notifyDataSetChanged());
        recyclerView.setAdapter(streamingAdapter = new StreamingAdapter(this, dvm));

        return root;
    }
}
