package udem.tj.taln;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

public class Graph {
    public static void graph(Map<Integer, Integer> typeOnEx, boolean display) throws IOException {
        // Sort the data by number of examples (X-axis) to ensure proper ordering
        TreeMap<Integer, Integer> sortedData = new TreeMap<>(typeOnEx);

        XYSeries series = new XYSeries("Vocabulary Growth");

        for (Map.Entry<Integer, Integer> entry : sortedData.entrySet()) {
            series.add(entry.getKey().doubleValue(), entry.getValue().doubleValue());
        }

        XYSeriesCollection dataset = new XYSeriesCollection(series);

        // Create XY line chart (proper for numerical data)
        JFreeChart chart = ChartFactory.createXYLineChart(
                "Vocabulary Growth",
                "Number of Examples Processed",
                "Number of Unique Word Types",
                dataset);
        chart.addSubtitle(new TextTitle("Number of Word Types vs Number of Examples Processed"));
        chart.addSubtitle(new TextTitle("Data source: UDEM TALN 2019-2020"));

        // Improve chart appearance
        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesShapesVisible(0, true);  // Show data points
        renderer.setSeriesLinesVisible(0, true);   // Show lines
        plot.setRenderer(renderer);

        // Set background colors
        chart.setBackgroundPaint(Color.WHITE);
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        // Create and display the chart
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(1000, 700));

        if (display) {
            JFrame frame = new JFrame("Vocabulary Growth Analysis");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(chartPanel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            frame.setResizable(true);
        }

        ChartUtils.saveChartAsPNG(new File("output/charts/word_count_chart_" + new Random().nextInt(1000000) + ".png"), chartPanel.getChart(), 1080, 720);

        System.out.println("Graph displayed with " + sortedData.size() + " data points");
        System.out.println("X-axis range: " + sortedData.firstKey() + " to " + sortedData.lastKey());
        System.out.println("Y-axis range: " + sortedData.values().stream().min(Integer::compareTo).orElse(0) +
                " to " + sortedData.values().stream().max(Integer::compareTo).orElse(0));
    }
}
