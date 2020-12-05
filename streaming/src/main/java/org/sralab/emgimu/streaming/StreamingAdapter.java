/*
 * Copyright (c) 2016, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.sralab.emgimu.streaming;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import org.sralab.emgimu.visualization.GraphView;

import java.util.List;

public class StreamingAdapter extends RecyclerView.Adapter<StreamingAdapter.ViewHolder> {

    private final String TAG = StreamingAdapter.class.getSimpleName();

    private final Integer DISPLAY_CHANNELS = 4; // TODO: this should be adjustable and per device

    private LifecycleOwner context;
    private final LiveData<List<Device>> devices;
    private DeviceViewModel dvm;

    public StreamingAdapter(LifecycleOwner context, DeviceViewModel dvm) {
        devices = dvm.getDevicesLiveData();
        this.context = context;
        this.dvm = dvm;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {

        final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.streaming_item, parent, false);
        int height = parent.getMeasuredHeight() / DISPLAY_CHANNELS;

        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.height = height;
        view.setLayoutParams(params);

        return new ViewHolder(view);
    }

    public class DefaultItemAnimatorNoChange extends DefaultItemAnimator {
        public DefaultItemAnimatorNoChange() { setSupportsChangeAnimations(false); }
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        // This is important to make sure there are not two overlapping views for the
        // same element that the RecycleView tries to use for smoother updates. It
        // breaks the animation coming from the graph element
        recyclerView.setItemAnimator(new DefaultItemAnimatorNoChange());
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        holder.bind(devices.getValue().get(position));
    }

    @Override
    public int getItemCount() { return devices.getValue().size(); }

	class ViewHolder extends RecyclerView.ViewHolder {

        private GraphView graphView;

        ViewHolder(final View itemView) {
			super(itemView);
            graphView = itemView.findViewById(R.id.graph_pwr);
        }

		private void bind(final Device device) {
            device.getEmg().observe(context, graphData -> graphView.updateGraphData(graphData) );
            dvm.getRange().observe(context, range -> device.setRange(range) );
		}
	}
}
