package dev.mehuol.finsight.util;

import java.util.Arrays;

/**
 * Technical indicators calculated in Java so the numbers given to the LLM are exact.
 * All arrays are oldest-first; values that cannot be computed yet are {@code NaN}.
 */
public final class Indicators {

    private Indicators() {
    }

    public static double[] sma(double[] values, int period) {
        double[] out = nanArray(values.length);
        double sum = 0;
        for (int i = 0; i < values.length; i++) {
            sum += values[i];
            if (i >= period) {
                sum -= values[i - period];
            }
            if (i >= period - 1) {
                out[i] = sum / period;
            }
        }
        return out;
    }

    public static double[] ema(double[] values, int period) {
        double[] out = nanArray(values.length);
        if (values.length < period) {
            return out;
        }
        double k = 2.0 / (period + 1);
        double prev = Arrays.stream(values, 0, period).average().orElse(Double.NaN);
        out[period - 1] = prev;
        for (int i = period; i < values.length; i++) {
            prev = values[i] * k + prev * (1 - k);
            out[i] = prev;
        }
        return out;
    }

    /** Wilder's RSI. */
    public static double[] rsi(double[] closes, int period) {
        double[] out = nanArray(closes.length);
        if (closes.length <= period) {
            return out;
        }
        double gain = 0, loss = 0;
        for (int i = 1; i <= period; i++) {
            double change = closes[i] - closes[i - 1];
            gain += Math.max(change, 0);
            loss += Math.max(-change, 0);
        }
        gain /= period;
        loss /= period;
        out[period] = toRsi(gain, loss);
        for (int i = period + 1; i < closes.length; i++) {
            double change = closes[i] - closes[i - 1];
            gain = (gain * (period - 1) + Math.max(change, 0)) / period;
            loss = (loss * (period - 1) + Math.max(-change, 0)) / period;
            out[i] = toRsi(gain, loss);
        }
        return out;
    }

    /** Returns {macdLine, signalLine, histogram} for MACD(12, 26, 9). */
    public static double[][] macd(double[] closes) {
        double[] fast = ema(closes, 12);
        double[] slow = ema(closes, 26);
        double[] line = nanArray(closes.length);
        for (int i = 0; i < closes.length; i++) {
            line[i] = fast[i] - slow[i];
        }
        int start = Math.min(25, closes.length);
        double[] signalPart = ema(Arrays.copyOfRange(line, start, closes.length), 9);
        double[] signal = nanArray(closes.length);
        double[] hist = nanArray(closes.length);
        for (int i = 0; i < signalPart.length; i++) {
            signal[start + i] = signalPart[i];
            hist[start + i] = line[start + i] - signalPart[i];
        }
        return new double[][] { line, signal, hist };
    }

    /** Wilder's Average True Range. */
    public static double[] atr(double[] highs, double[] lows, double[] closes, int period) {
        double[] out = nanArray(closes.length);
        if (closes.length <= period) {
            return out;
        }
        double[] tr = new double[closes.length];
        tr[0] = highs[0] - lows[0];
        for (int i = 1; i < closes.length; i++) {
            tr[i] = Math.max(highs[i] - lows[i],
                    Math.max(Math.abs(highs[i] - closes[i - 1]), Math.abs(lows[i] - closes[i - 1])));
        }
        double prev = Arrays.stream(tr, 1, period + 1).average().orElse(Double.NaN);
        out[period] = prev;
        for (int i = period + 1; i < closes.length; i++) {
            prev = (prev * (period - 1) + tr[i]) / period;
            out[i] = prev;
        }
        return out;
    }

    public static double last(double[] values) {
        return values.length == 0 ? Double.NaN : values[values.length - 1];
    }

    private static double toRsi(double avgGain, double avgLoss) {
        return avgLoss == 0 ? 100 : 100 - 100 / (1 + avgGain / avgLoss);
    }

    private static double[] nanArray(int size) {
        double[] out = new double[size];
        Arrays.fill(out, Double.NaN);
        return out;
    }
}
