package dev.mehuol.finsight.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.mehuol.finsight.dto.PortfolioAnalysis;
import dev.mehuol.finsight.dto.PortfolioExtraction;
import dev.mehuol.finsight.dto.PortfolioExtraction.Holding;

class PortfolioCalculatorTest {

    private static Holding holding(String name, String type, String sector, Double qty, Double avg, Double price) {
        return new Holding(name, null, type, sector, qty, avg, price, null, null);
    }

    @Test
    void computesTotalsPnlAndWeightsFromQuantityAndPrices() {
        PortfolioExtraction extraction = new PortfolioExtraction("USD", List.of(
                holding("Alpha Bank", "Equity", "Banking", 10.0, 100.0, 150.0),   // 1000 -> 1500
                holding("Beta Tech", "Equity", "IT", 5.0, 200.0, 100.0)));        // 1000 -> 500

        PortfolioAnalysis analysis = PortfolioCalculator.analyse("holdings.xlsx", extraction);

        assertEquals("USD", analysis.currency());
        assertEquals(2000.0, analysis.totalInvested());
        assertEquals(2000.0, analysis.totalCurrentValue());
        assertEquals(0.0, analysis.totalPnl());
        assertEquals("current value", analysis.weightBasis());
        PortfolioAnalysis.HoldingRow top = analysis.holdings().get(0);
        assertEquals("Alpha Bank", top.name()); // sorted by weight
        assertEquals(75.0, top.weightPercent());
        assertEquals(500.0, top.pnl());
        assertEquals(50.0, top.pnlPercent());
        assertEquals(-50.0, analysis.holdings().get(1).pnlPercent());
    }

    @Test
    void prefersExplicitValuesOverQuantityTimesPrice() {
        Holding h = new Holding("Fund", null, "Mutual Fund", null, 10.0, 1.0, 1.0, 5000.0, 6000.0);
        PortfolioAnalysis analysis = PortfolioCalculator.analyse("f.xlsx", new PortfolioExtraction("USD", List.of(h)));
        assertEquals(5000.0, analysis.totalInvested());
        assertEquals(6000.0, analysis.totalCurrentValue());
        assertEquals(20.0, analysis.totalPnlPercent());
    }

    @Test
    void groupsAllocationByAssetTypeAndSector() {
        PortfolioExtraction extraction = new PortfolioExtraction("USD", List.of(
                holding("A", "Equity", "Banking", 1.0, 100.0, 100.0),
                holding("B", "Equity", "Banking", 1.0, 100.0, 100.0),
                holding("C", "Gold", null, 1.0, 200.0, 200.0)));

        PortfolioAnalysis analysis = PortfolioCalculator.analyse("p.csv", extraction);

        assertEquals("Equity", analysis.byAssetType().get(0).label());
        assertEquals(50.0, analysis.byAssetType().get(0).percent());
        assertTrue(analysis.bySector().stream().anyMatch(s -> s.label().equals("Unknown") && s.percent() == 50.0));
    }

    @Test
    void warnsAboutConcentrationAndFewHoldings() {
        PortfolioExtraction extraction = new PortfolioExtraction("USD", List.of(
                holding("Big Co", "Equity", "Energy", 9.0, 10.0, 10.0),
                holding("Small Co", "Equity", "Retail", 1.0, 10.0, 10.0)));

        List<String> warnings = PortfolioCalculator.analyse("p.xlsx", extraction).warnings();

        assertTrue(warnings.stream().anyMatch(w -> w.startsWith("Big Co is 90.0%")));
        assertTrue(warnings.stream().anyMatch(w -> w.startsWith("The Energy sector is 90.0%")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("limited diversification")));
    }

    @Test
    void leavesPnlEmptyWhenCurrentPricesAreMissing() {
        PortfolioExtraction extraction = new PortfolioExtraction("USD", List.of(
                holding("A", "Equity", null, 2.0, 50.0, null),
                holding("B", "Equity", null, 1.0, 100.0, null)));

        PortfolioAnalysis analysis = PortfolioCalculator.analyse("p.docx", extraction);

        assertEquals(200.0, analysis.totalInvested());
        assertNull(analysis.totalCurrentValue());
        assertNull(analysis.totalPnl());
        assertEquals("invested amount", analysis.weightBasis());
        assertEquals(50.0, analysis.holdings().get(0).weightPercent());
    }

    @Test
    void skipsRowsWithoutValuesAndRejectsFilesWithNone() {
        PortfolioExtraction mixed = new PortfolioExtraction("USD", List.of(
                holding("A", "Equity", null, 1.0, 10.0, 10.0),
                holding("Total", null, null, null, null, null)));
        PortfolioAnalysis analysis = PortfolioCalculator.analyse("p.xlsx", mixed);
        assertEquals(1, analysis.holdingsCount());
        assertTrue(analysis.warnings().get(0).startsWith("1 row(s)"));

        PortfolioExtraction empty = new PortfolioExtraction("USD", List.of(holding("X", null, null, null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> PortfolioCalculator.analyse("p.xlsx", empty));
    }

    @Test
    void keepsTheDocumentCurrency() {
        List<Holding> one = List.of(holding("A", "Equity", "IT", 1.0, 100.0, 120.0));
        PortfolioAnalysis inr = PortfolioCalculator.analyse("p.xlsx", new PortfolioExtraction(" inr ", one));
        assertEquals("INR", inr.currency());
        assertTrue(inr.warnings().stream().noneMatch(w -> w.contains("currency")));

        assertEquals("EUR", PortfolioCalculator.analyse("p.xlsx", new PortfolioExtraction("EUR", one)).currency());
        assertEquals("PKR", PortfolioCalculator.analyse("p.xlsx", new PortfolioExtraction("pkr", one)).currency());
    }

    @Test
    void fallsBackToUsdWhenCurrencyIsMissingOrInvalid() {
        List<Holding> one = List.of(holding("A", "Equity", "IT", 1.0, 100.0, 120.0));
        for (String code : new String[] { null, "", "Rs", "dollars" }) {
            PortfolioAnalysis analysis = PortfolioCalculator.analyse("p.xlsx", new PortfolioExtraction(code, one));
            assertEquals("USD", analysis.currency(), "for " + code);
            assertTrue(analysis.warnings().get(0).contains("doesn't show which currency"), "for " + code);
        }
    }
}
