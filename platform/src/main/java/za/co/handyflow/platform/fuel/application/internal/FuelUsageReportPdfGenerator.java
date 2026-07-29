package za.co.handyflow.platform.fuel.application.internal;

import com.lowagie.text.*;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.fuel.domain.model.FuelDispatch;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates a monthly fuel usage report PDF the "no monthly fuel-usage
 * report PDF" gap: dispatch data (litres out, by recipient/vehicle) was
 * dashboard-only, with no exportable report for cost allocation across
 * vehicles/machines/cost-centers despite assetId/vehicleId already being
 * captured per dispatch. Groups by recipientName for the same reason the
 * frontend's "By Vehicle / Cost Center" rollup view does: it's the one
 * field guaranteed to be a readable label on every dispatch (vehicleId and
 * assetId are foreign keys into Fleet/Earthmoving this module has no names
 * for).
 * <p>
 * Same OpenPDF (com.lowagie.text.*) library, brand colors, and
 * header/divider/footer structure as this codebase's other PDF generators
 * — deliberately NOT iText7, see ScPoPdfGenerator's own Javadoc for why
 * (AGPL licensing without a purchased commercial license for this project).
 */
@Slf4j
@Component
public class FuelUsageReportPdfGenerator {

    private static final Color BRAND_DARK  = new Color(27, 58, 107);
    private static final Color BRAND_TEAL  = new Color(13, 148, 136);
    private static final Color LIGHT_GRAY  = new Color(248, 250, 252);
    private static final Color MID_GRAY    = new Color(100, 116, 139);
    private static final Color BORDER_GRAY = new Color(226, 232, 240);
    private static final Color UNPRICED_BG = new Color(255, 251, 235);

    private static final Font BRAND_FONT         = new Font(Font.HELVETICA, 20, Font.BOLD, BRAND_DARK);
    private static final Font TENANT_FONT        = new Font(Font.HELVETICA, 10, Font.NORMAL, MID_GRAY);
    private static final Font DOC_TYPE_FONT      = new Font(Font.HELVETICA, 16, Font.BOLD, BRAND_TEAL);
    private static final Font PERIOD_FONT        = new Font(Font.HELVETICA, 11, Font.NORMAL, MID_GRAY);
    private static final Font GENERATED_FONT     = new Font(Font.HELVETICA, 9, Font.NORMAL, MID_GRAY);
    private static final Font SUMMARY_LABEL_FONT = new Font(Font.HELVETICA, 8, Font.BOLD, MID_GRAY);
    private static final Font TABLE_HEADER_FONT  = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
    private static final Font TABLE_CELL_FONT    = new Font(Font.HELVETICA, 9, Font.NORMAL, BRAND_DARK);
    private static final Font TABLE_CELL_MUTED   = new Font(Font.HELVETICA, 9, Font.NORMAL, MID_GRAY);
    private static final Font TOTAL_ROW_FONT     = new Font(Font.HELVETICA, 10, Font.BOLD, BRAND_DARK);
    private static final Font FOOTER_FONT        = new Font(Font.HELVETICA, 8, Font.NORMAL, MID_GRAY);
    private static final Font EMPTY_FONT         = new Font(Font.HELVETICA, 10, Font.ITALIC, MID_GRAY);
    private static final Font UNPRICED_NOTE_FONT = new Font(Font.HELVETICA, 7, Font.ITALIC, new Color(180, 83, 9));

    private static final DateTimeFormatter D = DateTimeFormatter
            .ofPattern("dd MMM yyyy")
            .withZone(ZoneId.of("Africa/Johannesburg"));
    private static final DateTimeFormatter DT = DateTimeFormatter
            .ofPattern("dd MMM yyyy, HH:mm")
            .withZone(ZoneId.of("Africa/Johannesburg"));

    private static final DecimalFormatSymbols LITRES_SYMBOLS;
    static {
        LITRES_SYMBOLS = new DecimalFormatSymbols();
        LITRES_SYMBOLS.setGroupingSeparator(' ');
        LITRES_SYMBOLS.setDecimalSeparator('.');
    }
    private static final DecimalFormatSymbols MONEY_SYMBOLS;
    static {
        MONEY_SYMBOLS = new DecimalFormatSymbols();
        MONEY_SYMBOLS.setGroupingSeparator(' ');
        MONEY_SYMBOLS.setDecimalSeparator('.');
    }

    public byte[] generate(List<FuelDispatch> dispatches, Instant from, Instant to, String tenantName) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 50, 50, 60, 60);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(new FooterHandler(D.format(from) + " - " + D.format(to)));

            doc.open();
            addHeader(doc, from, to, tenantName);
            addDivider(doc);

            Map<String, Row> rollup = buildRollup(dispatches);
            addSummary(doc, dispatches, rollup);
            addRollupTable(doc, rollup);

            doc.close();
            log.info("[FUEL] Generated monthly usage report PDF for period {} - {}", from, to);
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("[FUEL] Usage report PDF generation failed: {}", e.getMessage());
            throw new RuntimeException("Failed to generate fuel usage report PDF", e);
        }
    }

    // Rollup - same grouping logic as the frontend's "By Vehicle / Cost Center"
    // view in DispatchesTab.tsx, kept intentionally identical so the PDF is a
    // faithful export of what the person already sees on screen.

    private static class Row {
        String label;
        int count;
        BigDecimal litres = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        boolean hasUnpriced;
        Instant lastDispatchedAt;
    }

    private Map<String, Row> buildRollup(List<FuelDispatch> dispatches) {
        Map<String, Row> rollup = new LinkedHashMap<>();
        for (FuelDispatch d : dispatches) {
            String key = d.getRecipientName() != null && !d.getRecipientName().isBlank()
                    ? d.getRecipientName().trim() : "Unassigned";
            Row row = rollup.computeIfAbsent(key, k -> { Row r = new Row(); r.label = k; return r; });
            row.count++;
            row.litres = row.litres.add(d.getLitresDispensed() != null ? d.getLitresDispensed() : BigDecimal.ZERO);
            if (d.getPricePerLitre() != null) {
                row.cost = row.cost.add(d.getLitresDispensed().multiply(d.getPricePerLitre()));
            } else {
                row.hasUnpriced = true;
            }
            if (row.lastDispatchedAt == null || d.getDispatchedAt().isAfter(row.lastDispatchedAt)) {
                row.lastDispatchedAt = d.getDispatchedAt();
            }
        }
        return rollup;
    }

    // Header

    private void addHeader(Document doc, Instant from, Instant to, String tenantName) throws DocumentException {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{1, 1});

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.setPadding(0);
        left.addElement(new Paragraph("HandyFlow", BRAND_FONT));
        if (tenantName != null && !tenantName.isBlank()) {
            Paragraph tenantP = new Paragraph(tenantName, TENANT_FONT);
            tenantP.setSpacingBefore(2);
            left.addElement(tenantP);
        }
        header.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setPadding(0);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Paragraph typeP = new Paragraph("FUEL USAGE REPORT", DOC_TYPE_FONT);
        typeP.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(typeP);

        Paragraph periodP = new Paragraph(D.format(from) + " - " + D.format(to), PERIOD_FONT);
        periodP.setAlignment(Element.ALIGN_RIGHT);
        periodP.setSpacingBefore(3);
        right.addElement(periodP);

        Paragraph genP = new Paragraph("Generated " + DT.format(Instant.now()), GENERATED_FONT);
        genP.setAlignment(Element.ALIGN_RIGHT);
        genP.setSpacingBefore(3);
        right.addElement(genP);

        header.addCell(right);
        doc.add(header);
    }

    // Summary strip

    private void addSummary(Document doc, List<FuelDispatch> dispatches, Map<String, Row> rollup) throws DocumentException {
        BigDecimal totalLitres = dispatches.stream()
                .map(FuelDispatch::getLitresDispensed).filter(l -> l != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCost = rollup.values().stream().map(r -> r.cost).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean anyUnpriced = rollup.values().stream().anyMatch(r -> r.hasUnpriced);

        PdfPTable summary = new PdfPTable(4);
        summary.setWidthPercentage(100);
        summary.setSpacingBefore(16);
        summary.setSpacingAfter(18);

        addSummaryCell(summary, "TOTAL DISPATCHES", String.valueOf(dispatches.size()));
        addSummaryCell(summary, "TOTAL LITRES OUT", formatLitres(totalLitres) + " L");
        addSummaryCell(summary, "TOTAL COST" + (anyUnpriced ? "*" : ""), "R " + formatMoney(totalCost));
        addSummaryCell(summary, "VEHICLES / RECIPIENTS", String.valueOf(rollup.size()));

        doc.add(summary);

        if (anyUnpriced) {
            Paragraph note = new Paragraph(
                    "* Total cost is a partial figure - one or more dispatches in this period have no recorded price per litre and are excluded from the cost total (litres are still counted). Rows affected are marked below.",
                    UNPRICED_NOTE_FONT);
            note.setSpacingAfter(10);
            doc.add(note);
        }
    }

    private void addSummaryCell(PdfPTable table, String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBackgroundColor(LIGHT_GRAY);
        cell.setPadding(12);
        cell.addElement(new Paragraph(label, SUMMARY_LABEL_FONT));
        Paragraph valP = new Paragraph(value, new Font(Font.HELVETICA, 15, Font.BOLD, BRAND_DARK));
        valP.setSpacingBefore(4);
        cell.addElement(valP);
        table.addCell(cell);
    }

    // Rollup table

    private void addRollupTable(Document doc, Map<String, Row> rollup) throws DocumentException {
        if (rollup.isEmpty()) {
            doc.add(new Paragraph("No dispatches recorded in this period.", EMPTY_FONT));
            return;
        }

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2.6f, 1f, 1.2f, 1.3f, 1.3f});
        table.setSpacingAfter(10);

        for (String h : new String[]{"Vehicle / Recipient", "Dispatches", "Total Litres", "Total Cost", "Last Dispatch"}) {
            PdfPCell headerCell = new PdfPCell(new Phrase(h, TABLE_HEADER_FONT));
            headerCell.setBackgroundColor(BRAND_DARK);
            headerCell.setBorder(Rectangle.NO_BORDER);
            headerCell.setPadding(6);
            table.addCell(headerCell);
        }

        List<Row> rows = rollup.values().stream()
                .sorted(Comparator.comparing((Row r) -> r.litres).reversed())
                .toList();

        int i = 0;
        BigDecimal grandLitres = BigDecimal.ZERO;
        BigDecimal grandCost = BigDecimal.ZERO;
        int grandCount = 0;

        for (Row r : rows) {
            Color rowBg = r.hasUnpriced ? UNPRICED_BG : (i++ % 2 == 0 ? Color.WHITE : LIGHT_GRAY);

            addCell(table, r.label, TABLE_CELL_FONT, Element.ALIGN_LEFT, rowBg);
            addCell(table, String.valueOf(r.count), TABLE_CELL_MUTED, Element.ALIGN_RIGHT, rowBg);
            addCell(table, formatLitres(r.litres) + " L", TABLE_CELL_FONT, Element.ALIGN_RIGHT, rowBg);

            String costText = "R " + formatMoney(r.cost) + (r.hasUnpriced ? "*" : "");
            addCell(table, costText, TABLE_CELL_FONT, Element.ALIGN_RIGHT, rowBg);
            addCell(table, r.lastDispatchedAt != null ? D.format(r.lastDispatchedAt) : "-", TABLE_CELL_MUTED, Element.ALIGN_LEFT, rowBg);

            grandLitres = grandLitres.add(r.litres);
            grandCost = grandCost.add(r.cost);
            grandCount += r.count;
        }

        PdfPCell totalLabel = new PdfPCell(new Phrase("Total", TOTAL_ROW_FONT));
        totalLabel.setBorder(Rectangle.TOP);
        totalLabel.setBorderColor(BORDER_GRAY);
        totalLabel.setBackgroundColor(LIGHT_GRAY);
        totalLabel.setPadding(7);
        table.addCell(totalLabel);

        addTotalCell(table, String.valueOf(grandCount), Element.ALIGN_RIGHT);
        addTotalCell(table, formatLitres(grandLitres) + " L", Element.ALIGN_RIGHT);
        addTotalCell(table, "R " + formatMoney(grandCost), Element.ALIGN_RIGHT);
        addTotalCell(table, "", Element.ALIGN_LEFT);

        doc.add(table);
    }

    private void addTotalCell(PdfPTable table, String text, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, TOTAL_ROW_FONT));
        cell.setBorder(Rectangle.TOP);
        cell.setBorderColor(BORDER_GRAY);
        cell.setBackgroundColor(LIGHT_GRAY);
        cell.setPadding(7);
        cell.setHorizontalAlignment(align);
        table.addCell(cell);
    }

    private void addCell(PdfPTable table, String text, Font font, int align, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(BORDER_GRAY);
        cell.setBackgroundColor(bg);
        cell.setPadding(6);
        cell.setHorizontalAlignment(align);
        table.addCell(cell);
    }

    // Helpers

    private String formatLitres(BigDecimal litres) {
        DecimalFormat fmt = new DecimalFormat("#,##0.0", LITRES_SYMBOLS);
        return fmt.format(litres != null ? litres : BigDecimal.ZERO);
    }

    private String formatMoney(BigDecimal amount) {
        DecimalFormat fmt = new DecimalFormat("#,##0.00", MONEY_SYMBOLS);
        return fmt.format(amount != null ? amount : BigDecimal.ZERO);
    }

    private void addDivider(Document doc) throws DocumentException {
        PdfPTable line = new PdfPTable(1);
        line.setWidthPercentage(100);
        line.setSpacingBefore(8);
        line.setSpacingAfter(8);
        PdfPCell cell = new PdfPCell();
        cell.setFixedHeight(1);
        cell.setBackgroundColor(BRAND_DARK);
        cell.setBorder(Rectangle.NO_BORDER);
        line.addCell(cell);
        doc.add(line);
    }

    private static class FooterHandler extends PdfPageEventHelper {
        private final String period;
        FooterHandler(String period) { this.period = period; }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                com.lowagie.text.pdf.PdfContentByte cb = writer.getDirectContent();
                Phrase footer = new Phrase(
                        "HandyFlow \u00b7 Fuel Usage Report \u00b7 " + period + " \u00b7 Page " + writer.getPageNumber(),
                        FOOTER_FONT);
                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, footer,
                        document.leftMargin(), document.bottomMargin() - 20, 0);
            } catch (Exception ignored) {
                // A broken footer must never take down PDF generation for a
                // document that otherwise generated correctly.
            }
        }
    }
}