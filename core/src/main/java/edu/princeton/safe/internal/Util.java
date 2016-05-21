package edu.princeton.safe.internal;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;

import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.apache.commons.math3.stat.descriptive.rank.Percentile.EstimationType;
import org.apache.commons.math3.util.CentralPivotingStrategy;
import org.apache.commons.math3.util.KthSelector;

public class Util {

    static Percentile defaultPercentile = new Percentile().withEstimationType(EstimationType.R_5)
                                                          .withKthSelector(new KthSelector(new CentralPivotingStrategy()));

    public static double[] nanArray(int size) {
        double[] array = new double[size];
        for (int i = 0; i < size; i++) {
            array[i] = Double.NaN;
        }
        return array;
    }

    public static BufferedReader getReader(String path) throws IOException {
        if (path.endsWith(".gz")) {
            return new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(path))));
        }
        return new BufferedReader(new FileReader(path));
    }

    public static double percentile(double[] values,
                                    double threshold) {
        return defaultPercentile.evaluate(values, threshold);
    }

    public static int[] hslToRgb(double h,
                                 double s,
                                 double l) {
        // Adapted from http://stackoverflow.com/a/9493060
        double r;
        double g;
        double b;

        if (s == 0) {
            r = l;
            g = l;
            b = l;
        } else {
            double q = l < 0.5 ? l * (1 + s) : l + s - l * s;
            double p = 2 * l - q;
            r = hue2Rgb(p, q, h + 1.0 / 3);
            g = hue2Rgb(p, q, h);
            b = hue2Rgb(p, q, h - 1.0 / 3);
        }
        int[] result = { (int) Math.round(r * 255), (int) Math.round(g * 255), (int) Math.round(b * 255) };
        return result;
    }

    private static double hue2Rgb(double p,
                                  double q,
                                  double t) {
        if (t < 0)
            t += 1;
        if (t > 1)
            t -= 1;
        if (t < 1.0 / 6) {
            return p + (q - p) * 6 * t;
        }
        if (t < 1.0 / 2) {
            return q;
        }
        if (t < 2.0 / 3) {
            return p + (q - p) * (2.0 / 3 - t) * 6;
        }
        return p;
    }

    public static int[] interpolateLinear(int[] v1,
                                          int[] v2,
                                          double t) {
        int[] result = new int[v1.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = (int) Math.round(v2[i] * t + v1[i] * (1 - t));
        }
        return result;
    }
}