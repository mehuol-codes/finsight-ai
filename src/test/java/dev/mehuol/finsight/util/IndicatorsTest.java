package dev.mehuol.finsight.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class IndicatorsTest {

    @Test
    void smaAveragesTheLastNValues() {
        double[] sma = Indicators.sma(new double[] { 1, 2, 3, 4, 5 }, 3);
        assertTrue(Double.isNaN(sma[1]));
        assertEquals(2.0, sma[2], 1e-9);
        assertEquals(4.0, sma[4], 1e-9);
    }

    @Test
    void emaOfConstantSeriesIsTheConstant() {
        double[] values = IntStream.range(0, 50).mapToDouble(i -> 2000).toArray();
        assertEquals(2000, Indicators.last(Indicators.ema(values, 20)), 1e-9);
    }

    @Test
    void rsiIsHundredWhenPriceOnlyRises() {
        double[] rising = IntStream.range(0, 30).mapToDouble(i -> 2000 + i).toArray();
        assertEquals(100, Indicators.last(Indicators.rsi(rising, 14)), 1e-9);
    }

    @Test
    void rsiMatchesWilderReferenceValue() {
        // StockCharts' RSI example. They round the averages (0.24 / 0.10) and show 70.53; unrounded,
        // avg gain = 3.34 / 14 and avg loss = 1.40 / 14, giving 70.46.
        double[] closes = { 44.34, 44.09, 44.15, 43.61, 44.33, 44.83, 45.10, 45.42, 45.84, 46.08, 45.89, 46.03,
                45.61, 46.28, 46.28 };
        assertEquals(70.46, Indicators.rsi(closes, 14)[14], 0.01);
    }

    @Test
    void atrOfConstantRangeCandlesIsTheRange() {
        int n = 30;
        double[] closes = IntStream.range(0, n).mapToDouble(i -> 2000).toArray();
        double[] highs = IntStream.range(0, n).mapToDouble(i -> 2005).toArray();
        double[] lows = IntStream.range(0, n).mapToDouble(i -> 1995).toArray();
        assertEquals(10, Indicators.last(Indicators.atr(highs, lows, closes, 14)), 1e-9);
    }

    @Test
    void macdIsZeroForFlatPrices() {
        double[] flat = IntStream.range(0, 60).mapToDouble(i -> 2000).toArray();
        double[][] macd = Indicators.macd(flat);
        assertEquals(0, Indicators.last(macd[0]), 1e-9);
        assertEquals(0, Indicators.last(macd[1]), 1e-9);
        assertEquals(0, Indicators.last(macd[2]), 1e-9);
    }
}
