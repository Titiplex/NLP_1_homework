package udem.tj.taln;

import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;

/**
 * The Graph class is responsible for generating and displaying a graphical representation
 * of vocabulary growth based on given input data. It supports plotting the growth of unique
 * word types against the number of processed examples, and offers additional features
 * such as polynomial fitting, Heaps' law approximation, and saving the output graph as an image.
 */
public class Graph {
    /**
     * Generates and displays a graph representing vocabulary growth based on the provided data.
     * The graph plots the number of examples processed (X-axis) against the number of unique word types (Y-axis).
     * It also optionally calculates and displays a polynomial fit and Heaps' law approximation for the data.
     * The graph can be saved as an image file.
     *
     * @param typeOnEx a map containing the data points where keys represent the number of examples processed
     *                 and values represent the number of unique word types
     * @param subtitle the subtitle to be displayed on the chart
     * @param display  a boolean indicating whether to display the created graph in a UI frame or not
     * @throws IOException if an error occurs during saving the generated graph as a PNG file
     */
    public static void graph(Map<Integer, Integer> typeOnEx, String subtitle, boolean display) throws IOException {
        // Sort the data by number of examples (X-axis) to ensure proper ordering
        TreeMap<Integer, Integer> sortedData = new TreeMap<>(typeOnEx);

        XYSeries series = new XYSeries("Vocabulary Growth");

        for (Map.Entry<Integer, Integer> entry : sortedData.entrySet()) {
            series.add(entry.getKey().doubleValue(), entry.getValue().doubleValue());
        }

        XYSeriesCollection dataset = new XYSeriesCollection(series);

        double[] coefficients = null;
        if (sortedData.size() >= 3) {
            WeightedObservedPoints obs = new WeightedObservedPoints();
            for (Map.Entry<Integer, Integer> entry : sortedData.entrySet()) {
                obs.add(entry.getKey(), entry.getValue());
            }
            PolynomialCurveFitter fitter = PolynomialCurveFitter.create(2);
            coefficients = fitter.fit(obs.toList());

            XYSeries fitSeries = new XYSeries("Fitted Curve (deg 2)");
            for (int x = sortedData.firstKey(); x <= sortedData.lastKey(); x++) {
                double y = coefficients[0] + coefficients[1] * x + coefficients[2] * x * x;
                fitSeries.add(x, y);
            }
            dataset.addSeries(fitSeries);
        }

        // Create XY line chart (proper for numerical data)
        JFreeChart chart = ChartFactory.createXYLineChart(
                "Vocabulary Growth",
                "Number of Examples Processed",
                "Number of Unique Word Types",
                dataset);
        chart.getTitle().setFont(new Font("Dialog", Font.BOLD, 48));
        chart.getXYPlot().getDomainAxis().setLabelFont(new Font("Dialog", Font.BOLD, 54));
        chart.getXYPlot().getRangeAxis().setLabelFont(new Font("Dialog", Font.BOLD, 54));
        chart.getXYPlot().getDomainAxis().setTickLabelFont(new Font("Dialog", Font.PLAIN, 48));
        chart.getXYPlot().getRangeAxis().setTickLabelFont(new Font("Dialog", Font.PLAIN, 48));
        chart.addSubtitle(new TextTitle(subtitle, new Font("Dialog", Font.BOLD, 54)));
        chart.addSubtitle(new TextTitle("Data source: Huggingface Wikipedia CHARS", new Font("Dialog", Font.PLAIN, 32)));

        // Improve chart appearance
        XYPlot plot = chart.getXYPlot();

        NumberAxis domain = (NumberAxis) plot.getDomainAxis();
        domain.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        domain.setAutoRangeIncludesZero(false);
        domain.setNumberFormatOverride(new DecimalFormat("###,###"));

        NumberAxis range = (NumberAxis) plot.getRangeAxis();
        range.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        range.setNumberFormatOverride(new DecimalFormat("###,###"));

        if (coefficients != null) {
            String eq = polyToString(coefficients);
            double r2 = rSquared(coefficients, sortedData);
            String eqText = eq + "    (R² = " + new DecimalFormat("0.0000").format(r2) + ")";

            chart.addSubtitle(new TextTitle(eqText, new Font("Dialog", Font.PLAIN, 28)));

            double xAnn = sortedData.firstKey() + 0.05 * (sortedData.lastKey() - sortedData.firstKey());
            int yMin = sortedData.values().stream().min(Integer::compareTo).orElse(0);
            int yMax = sortedData.values().stream().max(Integer::compareTo).orElse(0);
            double yAnn = yMax - 0.05 * (yMax - yMin + 1);
            XYTextAnnotation ann = new XYTextAnnotation(eqText, xAnn, yAnn);
            ann.setFont(new Font("Dialog", Font.PLAIN, 28));
            plot.addAnnotation(ann);
        }
        HeapsFit hf = heapsFit(sortedData);
        String heapsEq = "Heaps: V(N) = " + fmt(hf.k) + " · N^" + fmt(hf.beta) + "    (R² = " + new DecimalFormat("0.0000").format(hf.r2) + ")";
        chart.addSubtitle(new TextTitle(heapsEq, new Font("Dialog", Font.PLAIN, 28)));

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
        chartPanel.setPreferredSize(new Dimension(2160, 1440));

        if (display) {
            JFrame frame = new JFrame("Vocabulary Growth Analysis");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(chartPanel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            frame.setResizable(true);
        }
        File outputFile = new File("output/charts/word_count_"
                + subtitle.toLowerCase().replaceAll(" ", "_")
                + ".png");
        if (outputFile.getParentFile() != null && !outputFile.getParentFile().exists()) {
            outputFile.getParentFile().mkdirs();
        }
        ChartUtils.saveChartAsPNG(outputFile, chartPanel.getChart(), 2160, 1440);

        System.out.println("Graph displayed with " + sortedData.size() + " data points");
        System.out.println("X-axis range: " + sortedData.firstKey() + " to " + sortedData.lastKey());
        System.out.println("Y-axis range: " + sortedData.values().stream().min(Integer::compareTo).orElse(0) +
                " to " + sortedData.values().stream().max(Integer::compareTo).orElse(0));
    }

    private static String fmt(double v) {
        double a = Math.abs(v);
        String pattern = (a != 0 && (a < 1e-3 || a >= 1e5)) ? "0.###E0" : "###,###.####";
        return new DecimalFormat(pattern).format(v);
    }

    private static String polyToString(double[] c) {
        // c[0] + c[1] x + c[2] x^2 + ...
        StringBuilder sb = new StringBuilder("y = ");
        boolean firstTerm = true;
        for (int i = 0; i < c.length; i++) {
            double coef = c[i];
            if (Math.abs(coef) < 1e-12) continue; // ignore ~0
            String term;
            if (i == 0) {
                term = fmt(coef);
            } else if (i == 1) {
                term = fmt(Math.abs(coef)) + "·x";
            } else {
                term = fmt(Math.abs(coef)) + "·x^" + i;
            }
            if (firstTerm) {
                sb.append(coef < 0 ? "-" : "").append(term);
                firstTerm = false;
            } else {
                sb.append(coef < 0 ? " - " : " + ").append(term);
            }
        }
        if (firstTerm) sb.append("0");
        return sb.toString();
    }

    private static double rSquared(double[] c, NavigableMap<Integer, Integer> data) {
        // R² = 1 - SSres/SStot
        int n = data.size();
        if (n <= 1) return Double.NaN;
        double mean = 0.0;
        for (var e : data.entrySet()) mean += e.getValue();
        mean /= n;

        double ssTot = 0.0, ssRes = 0.0;
        for (var e : data.entrySet()) {
            double x = e.getKey();
            double y = e.getValue();
            double yhat = 0.0;
            double xp = 1.0;
            for (double v : c) {
                yhat += v * xp;
                xp *= x;
            }
            ssTot += (y - mean) * (y - mean);
            ssRes += (y - yhat) * (y - yhat);
        }
        if (ssTot == 0.0) return 1.0;
        return 1.0 - (ssRes / ssTot);
    }

    private static class HeapsFit {
        double k;
        double beta;
        double r2;
    }

    private static HeapsFit heapsFit(NavigableMap<Integer, Integer> data) {
        SimpleRegression reg = new SimpleRegression(true); // intercept
        for (var e : data.entrySet()) {
            double N = e.getKey();
            double V = e.getValue();
            if (N > 0 && V > 0) reg.addData(Math.log(N), Math.log(V));
        }
        HeapsFit hf = new HeapsFit();
        hf.beta = reg.getSlope();
        double intercept = reg.getIntercept();
        hf.k = Math.exp(intercept);
        hf.r2 = reg.getRSquare();
        return hf;
    }
}
