package dev.mehuol.finsight.dto;

import java.util.List;

/** Candles plus moving-average overlays, used by the frontend chart. */
public record ChartData(String symbol, String interval, List<Candle> candles, List<Point> sma20, List<Point> sma50) {

    /** A single value of an overlay line. */
    public record Point(long time, double value) {
    }
}
