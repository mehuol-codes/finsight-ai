package dev.mehuol.finsight.tool;

import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import dev.mehuol.finsight.service.GoldMarketService;

/** Tools the LLM can call on its own when the user asks about gold / XAU/USD. */
@Component
public class GoldMarketTools {

    private final GoldMarketService market;

    public GoldMarketTools(GoldMarketService market) {
        this.market = market;
    }

    @Tool(description = """
            Get live XAU/USD (spot gold vs US dollar) market data for one timeframe: last price,
            recent high/low, SMA20, SMA50, EMA200, RSI14, MACD, ATR14, a trend label and the last
            10 candles. Use it whenever the user asks about gold price, trend, levels or a gold chart.""")
    public Object getGoldMarketData(
            @ToolParam(description = "Candle timeframe: 1min, 5min, 15min, 30min, 1h, 2h, 4h, 1day, 1week or 1month") String interval) {
        try {
            return market.analyse(interval);
        }
        catch (RuntimeException e) {
            // Returned to the model so it can explain the problem instead of failing the chat.
            return Map.of("error", String.valueOf(e.getMessage()));
        }
    }
}
