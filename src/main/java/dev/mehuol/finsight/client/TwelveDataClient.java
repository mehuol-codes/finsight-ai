package dev.mehuol.finsight.client;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import dev.mehuol.finsight.dto.Candle;
import dev.mehuol.finsight.exception.MarketDataException;

/** HTTP client for the Twelve Data time-series API (free key: https://twelvedata.com). */
@Component
public class TwelveDataClient {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestClient http = RestClient.create("https://api.twelvedata.com");
    private final String apiKey;

    public TwelveDataClient(@Value("${twelvedata.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    /** Candles for a symbol, oldest first, timestamps in UTC. */
    public List<Candle> timeSeries(String symbol, String interval, int outputSize) {
        if (apiKey.isBlank()) {
            throw new MarketDataException(
                    "Live market data is not configured: set the TWELVE_DATA_API_KEY environment variable");
        }
        TimeSeriesResponse response;
        try {
            response = http.get()
                    .uri(uri -> uri.path("/time_series")
                            .queryParam("symbol", symbol)
                            .queryParam("interval", interval)
                            .queryParam("outputsize", outputSize)
                            .queryParam("timezone", "UTC")
                            .queryParam("apikey", apiKey)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, resp) -> {
                        // Twelve Data puts the reason in the JSON body; read it below instead of throwing here.
                    })
                    .body(TimeSeriesResponse.class);
        }
        catch (RestClientException e) {
            throw new MarketDataException("Could not reach Twelve Data: " + e.getMessage(), e);
        }

        if (response == null || !"ok".equals(response.status()) || response.values() == null) {
            String reason = response != null && response.message() != null ? response.message() : "empty response";
            throw new MarketDataException("Twelve Data error: " + reason);
        }
        List<Candle> candles = new ArrayList<>();
        try {
            for (TimeSeriesValue v : response.values()) {
                candles.add(new Candle(toEpoch(v.datetime()), Double.parseDouble(v.open()),
                        Double.parseDouble(v.high()), Double.parseDouble(v.low()), Double.parseDouble(v.close())));
            }
        }
        catch (RuntimeException e) { // unexpected number/date format in the response
            throw new MarketDataException("Unexpected Twelve Data response: " + e.getMessage(), e);
        }
        candles.sort(Comparator.comparingLong(Candle::time)); // API returns newest first
        return List.copyOf(candles);
    }

    private static long toEpoch(String datetime) {
        return datetime.length() > 10
                ? LocalDateTime.parse(datetime, DATE_TIME).toEpochSecond(ZoneOffset.UTC)
                : LocalDate.parse(datetime).atStartOfDay().toEpochSecond(ZoneOffset.UTC);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TimeSeriesResponse(String status, String message, List<TimeSeriesValue> values) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TimeSeriesValue(String datetime, String open, String high, String low, String close) {
    }
}
