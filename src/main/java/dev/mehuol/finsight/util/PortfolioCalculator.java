package dev.mehuol.finsight.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.mehuol.finsight.dto.PortfolioAnalysis;
import dev.mehuol.finsight.dto.PortfolioAnalysis.HoldingRow;
import dev.mehuol.finsight.dto.PortfolioAnalysis.Slice;
import dev.mehuol.finsight.dto.PortfolioExtraction;

/** Turns extracted holdings into totals, P&L, allocation and concentration warnings. */
public final class PortfolioCalculator {

    /** Used when the file doesn't reveal its currency or the model returns an invalid code. */
    public static final String DEFAULT_CURRENCY = "USD";

    static final double MAX_SINGLE_HOLDING_PERCENT = 20;
    static final double MAX_SECTOR_PERCENT = 40;
    static final int MIN_HOLDINGS_FOR_DIVERSIFICATION = 5;

    private PortfolioCalculator() {
    }

    public static PortfolioAnalysis analyse(String source, PortfolioExtraction extraction) {
        List<String> warnings = new ArrayList<>();
        String currency = currencyCode(extraction.currency());
        if (currency == null) {
            currency = DEFAULT_CURRENCY;
            warnings.add("The file doesn't show which currency it uses, so amounts are shown in " + DEFAULT_CURRENCY + ".");
        }
        List<Valued> valued = new ArrayList<>();
        int skipped = 0;
        for (PortfolioExtraction.Holding h : extraction.holdings()) {
            Double invested = firstNonNull(h.investedValue(), multiply(h.quantity(), h.averageCost()));
            Double current = firstNonNull(h.currentValue(), multiply(h.quantity(), h.currentPrice()));
            if (invested == null && current == null) {
                skipped++;
                continue;
            }
            valued.add(new Valued(h, invested, current));
        }
        if (valued.isEmpty()) {
            throw new IllegalArgumentException("No holdings with a quantity, price or value were found in " + source);
        }
        if (skipped > 0) {
            warnings.add(skipped + " row(s) had no quantity, price or value and were left out.");
        }

        // Weights use current value when every holding has one, otherwise the invested amount.
        boolean allCurrent = valued.stream().allMatch(v -> v.current != null);
        boolean allInvested = valued.stream().allMatch(v -> v.invested != null);
        String weightBasis = allCurrent ? "current value" : allInvested ? "invested amount" : "available value";
        double total = valued.stream().mapToDouble(Valued::weightValue).sum();

        Double totalInvested = allInvested ? round(valued.stream().mapToDouble(v -> v.invested).sum()) : null;
        Double totalCurrent = allCurrent ? round(valued.stream().mapToDouble(v -> v.current).sum()) : null;
        Double totalPnl = totalInvested != null && totalCurrent != null ? round(totalCurrent - totalInvested) : null;
        Double totalPnlPercent = totalPnl != null && totalInvested > 0 ? round(totalPnl / totalInvested * 100) : null;
        if (totalPnl == null) {
            warnings.add("The file doesn't give both cost and current value for every holding, so total profit/loss "
                    + "can't be calculated.");
        }

        List<HoldingRow> rows = new ArrayList<>();
        Map<String, Double> byAssetType = new LinkedHashMap<>();
        Map<String, Double> bySector = new LinkedHashMap<>();
        for (Valued v : valued) {
            PortfolioExtraction.Holding h = v.holding;
            Double pnl = v.invested != null && v.current != null ? v.current - v.invested : null;
            Double pnlPercent = pnl != null && v.invested > 0 ? pnl / v.invested * 100 : null;
            double weight = total > 0 ? v.weightValue() / total * 100 : 0;
            rows.add(new HoldingRow(h.name(), h.symbol(), label(h.assetType()), label(h.sector()), h.quantity(),
                    h.averageCost(), h.currentPrice(), roundOrNull(v.invested), roundOrNull(v.current),
                    roundOrNull(pnl), roundOrNull(pnlPercent), round(weight)));
            byAssetType.merge(label(h.assetType()), v.weightValue(), Double::sum);
            bySector.merge(label(h.sector()), v.weightValue(), Double::sum);
        }
        rows.sort(Comparator.comparingDouble(HoldingRow::weightPercent).reversed());

        List<Slice> assetSlices = slices(byAssetType, total);
        List<Slice> sectorSlices = slices(bySector, total);
        addConcentrationWarnings(rows, sectorSlices, warnings);

        return new PortfolioAnalysis(source, currency, rows.size(), totalInvested, totalCurrent, totalPnl,
                totalPnlPercent, weightBasis, rows, assetSlices, sectorSlices, warnings);
    }

    private static void addConcentrationWarnings(List<HoldingRow> rows, List<Slice> sectors, List<String> warnings) {
        rows.stream()
                .filter(r -> r.weightPercent() > MAX_SINGLE_HOLDING_PERCENT)
                .forEach(r -> warnings.add(r.name() + " is " + r.weightPercent() + "% of the portfolio (above "
                        + (int) MAX_SINGLE_HOLDING_PERCENT + "%)."));
        sectors.stream()
                .filter(s -> !s.label().equals("Unknown") && s.percent() > MAX_SECTOR_PERCENT)
                .forEach(s -> warnings.add("The " + s.label() + " sector is " + s.percent() + "% of the portfolio "
                        + "(above " + (int) MAX_SECTOR_PERCENT + "%)."));
        if (rows.size() < MIN_HOLDINGS_FOR_DIVERSIFICATION) {
            warnings.add("Only " + rows.size() + " holding(s): the portfolio has limited diversification.");
        }
    }

    private static List<Slice> slices(Map<String, Double> values, double total) {
        return values.entrySet().stream()
                .map(e -> new Slice(e.getKey(), round(e.getValue()), round(total > 0 ? e.getValue() / total * 100 : 0)))
                .sorted(Comparator.comparingDouble(Slice::value).reversed())
                .toList();
    }

    /** A valid ISO 4217 code in upper case (e.g. "inr" -> "INR"), or null if it isn't one. */
    static String currencyCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return Currency.getInstance(code.strip().toUpperCase(Locale.ROOT)).getCurrencyCode();
        }
        catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String label(String value) {
        return value == null || value.isBlank() ? "Unknown" : value.strip();
    }

    private static Double multiply(Double a, Double b) {
        return a != null && b != null ? a * b : null;
    }

    private static Double firstNonNull(Double a, Double b) {
        return a != null ? a : b;
    }

    private static Double roundOrNull(Double value) {
        return value == null ? null : round(value);
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    private record Valued(PortfolioExtraction.Holding holding, Double invested, Double current) {

        double weightValue() {
            return current != null ? current : invested;
        }
    }
}
