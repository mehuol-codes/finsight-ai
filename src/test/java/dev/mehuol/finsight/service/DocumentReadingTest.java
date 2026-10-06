package dev.mehuol.finsight.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class DocumentReadingTest {

    private static String text(String fileName, byte[] bytes) {
        return DocumentService.read(fileName, new ByteArrayResource(bytes)).stream()
                .map(d -> d.getText())
                .reduce("", String::concat);
    }

    @Test
    void readsExcelHoldings() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("Holdings");
            String[][] rows = { { "Instrument", "Qty", "Avg cost", "LTP" }, { "RELIANCE", "10", "2450.5", "2890" },
                    { "HDFCBANK", "25", "1520", "1675.25" } };
            for (int r = 0; r < rows.length; r++) {
                var row = sheet.createRow(r);
                for (int c = 0; c < rows[r].length; c++) {
                    row.createCell(c).setCellValue(rows[r][c]);
                }
            }
            workbook.write(out);

            String text = text("holdings.xlsx", out.toByteArray());
            assertTrue(text.contains("RELIANCE") && text.contains("2450.5"), text);
            assertTrue(text.contains("HDFCBANK") && text.contains("1675.25"), text);
        }
    }

    @Test
    void readsWordTables() throws IOException {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("Portfolio statement March 2026");
            XWPFTable table = doc.createTable(2, 3);
            table.getRow(0).getCell(0).setText("Fund");
            table.getRow(0).getCell(1).setText("Units");
            table.getRow(0).getCell(2).setText("Value");
            table.getRow(1).getCell(0).setText("Nifty Index Fund");
            table.getRow(1).getCell(1).setText("120.5");
            table.getRow(1).getCell(2).setText("25300");
            doc.write(out);

            String text = text("statement.docx", out.toByteArray());
            assertTrue(text.contains("Portfolio statement") && text.contains("Nifty Index Fund"), text);
            assertTrue(text.contains("25300"), text);
        }
    }

    @Test
    void readsCsvAsPlainText() {
        String csv = "symbol,qty,price\nAAPL,3,190.1\n";
        String text = text("p.csv", csv.getBytes(StandardCharsets.UTF_8));
        assertTrue(text.contains("AAPL,3,190.1"), text);
    }

    @Test
    void rejectsUnknownTypes() {
        assertThrows(IllegalArgumentException.class, () -> text("x.exe", new byte[] { 1 }));
    }
}
