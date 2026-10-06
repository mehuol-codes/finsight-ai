package dev.mehuol.finsight.dto;

import java.util.List;

/**
 * Portfolio metrics computed from the extracted holdings, rendered as charts in the UI.
 * Values that the file did not provide stay null (e.g. P&L without current prices).
 */
public record PortfolioAnalysis(String source, String currency, int holdingsCount, Double totalInvested,
        Double totalCurrentValue, Double totalPnl, Double totalPnlPercent, String weightBasis,
        List<HoldingRow> holdings, List<Slice> byAssetType, List<Slice> bySector, List<String> warnings) {

    public record HoldingRow(String name, String symbol, String assetType, String sector, Double quantity,
            Double averageCost, Double currentPrice, Double invested, Double currentValue, Double pnl,
            Double pnlPercent, double weightPercent) {
    }

    /** One segment of an allocation chart. */
    public record Slice(String label, double value, double percent) {
    }
}
