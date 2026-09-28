package dev.mehuol.finsight.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import dev.mehuol.finsight.client.TwelveDataClient;
import dev.mehuol.finsight.dto.Candle;
import dev.mehuol.finsight.dto.ChartData;
import dev.mehuol.finsight.dto.GoldAnalysis;
import dev.mehuol.finsight.util.Indicators;

/**
 * XAU/USD candles and indicators. Responses are cached briefly because the Twelve Data free
 * plan allows only a few requests per minute.
 */
@Service
public class GoldMarketService {

    public static final String SYMBOL = "XAU/USD";
    public static final Set<String> INTERVALS = Set.of(
            "1min", "5min", "15min", "30min", "1h", "2h", "4h", "1day", "1week", "1month");

    private static final int OUTPUT_SIZE = 300;
    private static final long CACHE_MILLIS = 60_000;

    private final TwelveDataClient twelveData;
    private final Map<String, CachedCandles> cache = new ConcurrentHashMap<>();

    public GoldMarketService(TwelveDataClient twelveData) {
        this.twelveData = twelveData;
    }

    public ChartData chart(String interval) {
        List<Candle> candles = candles(interval);
        double[] closes = candles.stream().mapToDouble(Candle::close).toArray();
        return new ChartData(SYMBOL, interval, candles, series(candles, Indicators.sma(closes, 20)),
                series(candles, Indicators.sma(closes, 50)));
    }

    public GoldAnalysis analyse(String interval) {
        List<Candle> candles = candles(interval);
        double[] closes = candles.stream().mapToDouble(Candle::close).toArray();
        double[] highs = candles.stream().mapToDouble(Candle::high).toArray();
        double[] lows = candles.stream().mapToDouble(Candle::low).toArray();
        double[][] macd = Indicators.macd(closes);

        double last = Indicators.last(closes);
        double sma20 = Indicators.last(Indicators.sma(closes, 20));
        double sma50 = Indicators.last(Indicators.sma(closes, 50));
        List<Candle> recent50 = candles.subList(Math.max(0, candles.size() - 50), candles.size());
        Candle lastCandle = candles.get(candles.size() - 1);

        return new GoldAnalysis(SYMBOL, interval,
                LocalDateTime.ofEpochSecond(lastCandle.time(), 0, ZoneOffset.UTC).toString(),
                round(last),
                round((last - closes[0]) / closes[0] * 100),
                round(candles.stream().mapToDouble(Candle::high).max().orElse(Double.NaN)),
                round(candles.stream().mapToDouble(Candle::low).min().orElse(Double.NaN)),
                round(recent50.stream().mapToDouble(Candle::high).max().orElse(Double.NaN)),
                round(recent50.stream().mapToDouble(Candle::low).min().orElse(Double.NaN)),
                round(sma20), round(sma50),
                round(Indicators.last(Indicators.ema(closes, 200))),
                round(Indicators.last(Indicators.rsi(closes, 14))),
                round(Indicators.last(macd[0])), round(Indicators.last(macd[1])), round(Indicators.last(macd[2])),
                round(Indicators.last(Indicators.atr(highs, lows, closes, 14))),
                trend(last, sma20, sma50),
                candles.subList(Math.max(0, candles.size() - 10), candles.size()),
                "Twelve Data, " + candles.size() + " candles");
    }

    private List<Candle> candles(String interval) {
        if (!INTERVALS.contains(interval)) {
            throw new IllegalArgumentException("Unsupported interval " + interval + ". Use one of " + INTERVALS);
        }
        CachedCandles cached = cache.get(interval);
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt() < CACHE_MILLIS) {
            return cached.candles();
        }
        List<Candle> candles = twelveData.timeSeries(SYMBOL, interval, OUTPUT_SIZE);
        cache.put(interval, new CachedCandles(candles, System.currentTimeMillis()));
        return candles;
    }

    private static String trend(double last, double sma20, double sma50) {
        if (Double.isNaN(sma50)) {
            return "not enough data";
        }
        if (last > sma20 && sma20 > sma50) {
            return "uptrend (price > SMA20 > SMA50)";
        }
        if (last < sma20 && sma20 < sma50) {
            return "downtrend (price < SMA20 < SMA50)";
        }
        return "sideways / mixed";
    }

    private static List<ChartData.Point> series(List<Candle> candles, double[] values) {
        List<ChartData.Point> points = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            if (!Double.isNaN(values[i])) {
                points.add(new ChartData.Point(candles.get(i).time(), round(values[i])));
            }
        }
        return points;
    }

    private static double round(double value) {
        return Double.isNaN(value) ? value : Math.round(value * 100) / 100.0;
    }

    private record CachedCandles(List<Candle> candles, long fetchedAt) {
    }
}
