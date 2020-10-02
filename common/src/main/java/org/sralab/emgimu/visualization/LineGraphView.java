/*
 * Copyright (c) 2015, Nordic Semiconductor
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
package org.sralab.emgimu.visualization;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint.Align;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import java.util.Collections;
import java.util.List;

/**
 * This class uses external library AChartEngine to show dynamic real time line graph for HR values
 */
public class LineGraphView extends LinearLayout {

	private TimeSeries series = new TimeSeries("");
	private XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();
	private XYMultipleSeriesRenderer renderer = new XYMultipleSeriesRenderer();
	private GraphicalView graphicalView;

	public LineGraphView(Context context) {
		this(context, null, 0);
	}

	public LineGraphView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	/**
	 * This constructor will set some properties of single chart and some properties of whole graph
	 */
	public LineGraphView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context);
	}

	public void init(Context context) {

		dataset.addSeries(series);

		final XYMultipleSeriesRenderer renderer = this.renderer;
		//set whole graph background color to transparent color
		renderer.setBackgroundColor(Color.TRANSPARENT);
		renderer.setMargins(new int[] { 5, 5, 5, 5 }); // top, left, bottom, right
		renderer.setMarginsColor(Color.argb(0x00, 0x01, 0x01, 0x01));
		renderer.setAxesColor(Color.BLACK);
		renderer.setAxisTitleTextSize(24);
		renderer.setShowGrid(true);
		renderer.setGridColor(Color.LTGRAY);
		renderer.setLabelsColor(Color.BLACK);
		renderer.setYLabelsColor(0, Color.DKGRAY);
		renderer.setYLabelsAlign(Align.RIGHT);
		renderer.setYLabelsPadding(4.0f);
		renderer.setXLabelsColor(Color.DKGRAY);
		renderer.setLabelsTextSize(20);
		renderer.setLegendTextSize(20);

		//Disable zoom
		renderer.setPanEnabled(false, false);
		renderer.setZoomEnabled(false, false);

		renderer.setShowLabels(false);
		renderer.setShowAxes(false);
		renderer.addSeriesRenderer(getRenderer(0));

		// defaults to auto-ranging
		// setRange(5e6);

		graphicalView = ChartFactory.getLineChartView(context, dataset, this.renderer);
		addView(graphicalView);
	}

	public void updateSeries(TimeSeries series) {
		dataset.clear();
		dataset.addSeries(series);
		graphicalView.repaint();
	}

	private XYSeriesRenderer getRenderer(int i) {
		XYSeriesRenderer render = new XYSeriesRenderer();
		int[] colors = {Color.BLACK, Color.BLUE, Color.RED, Color.YELLOW};

		render.setColor(colors[i]);
		render.setPointStyle(PointStyle.CIRCLE);
		render.setFillPoints(true);
		render.setShowLegendItem(true);

		return render;
	}

	public void updateSeries(List<TimeSeries> series) {
		dataset.clear();
		dataset.addAllSeries(Collections.unmodifiableList(series));

		if (renderer.getSeriesRendererCount() != series.size()) {
			renderer.removeAllRenderers();
			for (int i = 0; i < series.size(); i++) {
				renderer.addSeriesRenderer(getRenderer(i));
			}
		}

		graphicalView.repaint();
	}

	public void setRange(double range) {
		renderer.setYAxisMax(range);
		renderer.setYAxisMin(-range);
	}
}
