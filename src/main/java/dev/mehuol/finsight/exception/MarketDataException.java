package dev.mehuol.finsight.exception;

/** Live market data could not be fetched: provider error, network failure or missing API key (HTTP 502). */
public class MarketDataException extends RuntimeException {

    public MarketDataException(String message) {
        super(message);
    }

    public MarketDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
