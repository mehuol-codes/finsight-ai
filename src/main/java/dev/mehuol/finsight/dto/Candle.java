package dev.mehuol.finsight.dto;

/** One OHLC candle; {@code time} is epoch seconds (UTC). */
public record Candle(long time, double open, double high, double low, double close) {
}
