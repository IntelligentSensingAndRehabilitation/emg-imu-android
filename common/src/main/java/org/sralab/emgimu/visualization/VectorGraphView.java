package org.sralab.emgimu.visualization;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.ViewGroup;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import java.util.ArrayList;
import java.util.List;

public class VectorGraphView {

    //TimeSeries will hold the data in x,y format for single chart
    private List<XYSeries> mSeries;
    //XYMultipleSeriesDataset will contain all the TimeSeries
    private XYMultipleSeriesDataset mDataset = new XYMultipleSeriesDataset();
    //XYMultipleSeriesRenderer will contain all XYSeriesRenderer and it can be used to set the properties of whole Graph
    private XYMultipleSeriesRenderer mMultiRenderer = new XYMultipleSeriesRenderer();
    private GraphicalView mGraphView;

    int mChannels;

    public void setRange(double range) {
        mMultiRenderer.setYAxisMax(range);
        mMultiRenderer.setYAxisMin(-range);
    }
    /**
     * This constructor will set some properties of single chart and some properties of whole graph
     */
    public VectorGraphView(Context context, ViewGroup layout, int channels) {

        //XYSeriesRenderer is used to set the properties like chart color, style of each point, etc. of single chart
        final XYSeriesRenderer seriesRenderer = new XYSeriesRenderer();
        //set line chart color to Black
        seriesRenderer.setColor(Color.BLACK);
        //set line chart style to square points
        seriesRenderer.setPointStyle(PointStyle.CIRCLE);
        seriesRenderer.setFillPoints(true);
        seriesRenderer.setShowLegendItem(false);

        final XYMultipleSeriesRenderer renderer = mMultiRenderer;
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
        renderer.setYLabelsAlign(Paint.Align.RIGHT);
        renderer.setYLabelsPadding(4.0f);
        renderer.setXLabelsColor(Color.DKGRAY);
        renderer.setLabelsTextSize(20);
        renderer.setLegendTextSize(20);

        //Disable zoom
        renderer.setPanEnabled(false, false);
        renderer.setZoomEnabled(false, false);
        //set title to x-axis and y-axis
        renderer.setXTitle("    Time (seconds)");
        renderer.setYTitle("               PWR");
        renderer.setShowLabels(false);
        renderer.setShowAxes(false);


        mChannels = channels;
        mSeries = new ArrayList<>();
        for (int i =0; i < channels; i++) {
            TimeSeries series = new TimeSeries("Ch: " + i);
            mSeries.add(series);
            renderer.addSeriesRenderer(i, seriesRenderer);
        }
        mDataset.addAllSeries(mSeries);

        // defaults to auto-ranging
        // setRange(5e6);

        mGraphView = ChartFactory.getLineChartView(context, mDataset, mMultiRenderer);
        layout.addView(mGraphView);
    }

    public void repaint() {
        mGraphView.repaint();
    }

    private int mWindowSize = 100;
    public void setWindowSize(int newWindow) {
        mWindowSize = newWindow;
    }


    /**
     * add new x,y value to chart
     */
    private int mCounter;
    public void addValue(float [] val) {
        mCounter++;

        for (int i = 0; i < mChannels; i++) {
            TimeSeries series = (TimeSeries) mSeries.get(i);
            series.add(mCounter, val[i]);

            if (series.getItemCount() > mWindowSize) {
                series.remove(0);
            }
        }
    }

    /**
     * clear all previous values of chart
     */
    public void clearGraph() {
        mSeries.clear();
    }

}
