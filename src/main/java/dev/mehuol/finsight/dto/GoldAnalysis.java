package dev.mehuol.finsight.dto;

import java.util.List;

/** Snapshot of the gold market handed to the LLM by the market data tool. */
public record GoldAnalysis(String symbol, String interval, String lastCandleTimeUtc, double lastClose,
        double changePercentOverPeriod, double periodHigh, double periodLow, double recentHigh50,
        double recentLow50, double sma20, double sma50, double ema200, double rsi14, double macd,
        double macdSignal, double macdHistogram, double atr14, String trend, List<Candle> last10Candles,
        String dataSource) {
}
