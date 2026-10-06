package dev.mehuol.finsight.dto;

import java.util.List;

/**
 * Holdings as read from an uploaded file by the model (structured output). {@code currency} is
 * the ISO 4217 code the amounts are in, or null if the file doesn't show it. Any value missing
 * from the file is null; all calculations happen afterwards in Java.
 */
public record PortfolioExtraction(String currency, List<Holding> holdings) {

    public record Holding(String name, String symbol, String assetType, String sector, Double quantity,
            Double averageCost, Double currentPrice, Double investedValue, Double currentValue) {
    }
}
