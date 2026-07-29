package za.co.handyflow.platform.fuel.application.internal;

import com.lowagie.text.*;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.fuel.domain.model.DipReading;
import za.co.handyflow.platform.fuel.domain.model.FuelTank;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates a dip-reading reconciliation report PDF — the "PDF gaps" item
 * the audit called out as missing despite variance-detection being this
 * module's single most valuable feature: "no exportable document a depot
 * manager could hand to ops/security when investigating a suspected theft,
 * only the in-app dip history list."
 * <p>
 * Same OpenPDF (com.lowagie.text.*) library, brand colors, and
 * header/divider/footer structure as this codebase's other PDF generators
 * (ScPoPdfGenerator, AccFeeNotePdfGenerator, TasksBoardPdfGenerator) —
 * deliberately NOT iText7, see ScPoPdfGenerator's own Javadoc for why
 * (AGPL licensing without a purchased commercial license for this project).
 */
@Slf4j
@Component
public class FuelReconciliationPdfGenerator {

    private static final Color BRAND_DARK   = new Color(27, 58, 107);
    private static final Color BRAND_TEAL   = new Color(13, 148, 136);   // Fuel module accent — matches FuelPage's header icon color
    private static final Color LIGHT_GRAY   = new Color(248, 250, 252);
    private static final Color MID_GRAY     = new Color(100, 116, 139);
    private static final Color BORDER_GRAY  = new Color(226, 232, 240);
    private static final Color NEGATIVE_RED = new Color(220, 38, 38);
    private static final Color NEGATIVE_BG  = new Color(254, 242, 242);
    private static final Color POSITIVE_GREEN = new Color(22, 101, 52);

    private static final Font BRAND_FONT        = new Font(Font.HELVETICA, 20, Font.BOLD, BRAND_DARK);
    private static final Font TENANT_FONT       = new Font(Font.HELVETICA, 10, Font.NORMAL, MID_GRAY);
    private static final Font DOC_TYPE_FONT     = new Font(Font.HELVETICA, 16, Font.BOLD, BRAND_TEAL);
    private static final Font TANK_NAME_FONT    = new Font(Font.HELVETICA, 11, Font.NORMAL, MID_GRAY);
    private static final Font PERIOD_FONT       = new Font(Font.HELVETICA, 9, Font.NORMAL, MID_GRAY);
    private static final Font SUMMARY_LABEL_FONT = new Font(Font.HELVETICA, 8, Font.BOLD, MID_GRAY);
    private static final Font TABLE_HEADER_FONT = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
    private static final Font TABLE_CELL_FONT   = new Font(Font.HELVETICA, 9, Font.NORMAL, BRAND_DARK);
    private static final Font TABLE_CELL_MUTED  = new Font(Font.HELVETICA, 9, Font.NORMAL, MID_GRAY);
    private static final Font FOOTER_FONT       = new Font(Font.HELVETICA, 8, Font.NORMAL, MID_GRAY);
    private static final Font EMPTY_FONT        = new Font(Font.HELVETICA, 10, Font.ITALIC, MID_GRAY);
    private static final Font NOTICE_FONT       = new Font(Font.HELVETICA, 9, Font.NORMAL, NEGATIVE_RED);

    private static final DateTimeFormatter D = DateTimeFormatter
            .ofPattern("dd MMM yyyy")
            .withZone(ZoneId.of("Africa/Johannesburg"));
    private static final DateTimeFormatter DT = DateTimeFormatter
            .ofPattern("dd MMM yyyy, HH:mm")
            .withZone(ZoneId.of("Africa/Johannesburg"));

    // Same thread-safety rationale as every other generator in this codebase:
    // this bean is a Spring singleton serving concurrent requests, and
    // DecimalFormat isn't thread-safe — build fresh per call from shared symbols.
    private static final DecimalFormatSymbols LITRES_SYMBOLS;
    static {
        LITRES_SYMBOLS = new DecimalFormatSymbols();
        LITRES_SYMBOLS.setGroupingSeparator(' ');
        LITRES_SYMBOLS.setDecimalSeparator('.');
    }

    public byte[] generate(FuelTank tank, List<DipReading> readings, Instant from, Instant to, String tenantName) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 50, 50, 60, 60);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(new FooterHandler(tank.getName()));

            doc.open();
            addHeader(doc, tank, from, to, tenantName);
            addDivider(doc);
            addSummary(doc, readings);
            addReadingsTable(doc, readings);
            doc.close();

            log.info("[FUEL] Generated reconciliation report PDF for tank={}", tank.getId());
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("[FUEL] Reconciliation report PDF generation failed for tank={}: {}", tank.getId(), e.getMessage());
            throw new RuntimeException("Failed to generate reconciliation report PDF", e);
        }
    }

    // Header

    private void addHeader(Document doc, FuelTank tank, Instant from, Instant to, String tenantName) throws DocumentException {
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

        Paragraph typeP = new Paragraph("TANK RECONCILIATION REPORT", DOC_TYPE_FONT);
        typeP.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(typeP);

        Paragraph tankP = new Paragraph(tank.getName() + " (" + tank.getFuelType() + ")", TANK_NAME_FONT);
        tankP.setAlignment(Element.ALIGN_RIGHT);
        tankP.setSpacingBefore(3);
        right.addElement(tankP);

        Paragraph periodP = new Paragraph("Period: " + D.format(from) + " \u2013 " + D.format(to), PERIOD_FONT);
        periodP.setAlignment(Element.ALIGN_RIGHT);
        periodP.setSpacingBefore(3);
        right.addElement(periodP);

        Paragraph genP = new Paragraph("Generated " + DT.format(Instant.now()), PERIOD_FONT);
        genP.setAlignment(Element.ALIGN_RIGHT);
        genP.setSpacingBefore(2);
        right.addElement(genP);

        header.addCell(right);
        doc.add(header);
    }

    // Summary strip

    private void addSummary(Document doc, List<DipReading> readings) throws DocumentException {
        long negativeCount = readings.stream().filter(DipReading::hasNegativeVariance).count();
        BigDecimal totalShrinkage = readings.stream()
                .filter(DipReading::hasNegativeVariance)
                .map(DipReading::getVarianceLitres)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .abs();
        BigDecimal netVariance = readings.stream()
                .map(DipReading::getVarianceLitres)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        PdfPTable summary = new PdfPTable(4);
        summary.setWidthPercentage(100);
        summary.setSpacingBefore(16);
        summary.setSpacingAfter(18);

        addSummaryCell(summary, "DIP READINGS", String.valueOf(readings.size()), BRAND_DARK);
        addSummaryCell(summary, "NEGATIVE-VARIANCE EVENTS", String.valueOf(negativeCount), negativeCount > 0 ? NEGATIVE_RED : BRAND_DARK);
        addSummaryCell(summary, "TOTAL SHRINKAGE", formatLitres(totalShrinkage) + " L", totalShrinkage.compareTo(BigDecimal.ZERO) > 0 ? NEGATIVE_RED : BRAND_DARK);
        addSummaryCell(summary, "NET VARIANCE (PERIOD)", (netVariance.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + formatLitres(netVariance) + " L",
                netVariance.compareTo(BigDecimal.ZERO) < 0 ? NEGATIVE_RED : POSITIVE_GREEN);

        doc.add(summary);

        if (negativeCount > 0) {
            Paragraph notice = new Paragraph(negativeCount + " reading" + (negativeCount == 1 ? "" : "s") + " in this period showed fuel missing versus "
                    + "the calculated book level — possible theft or leak. Rows are highlighted below.", NOTICE_FONT);
            notice.setSpacingAfter(12);
            doc.add(notice);
        }
    }

    private void addSummaryCell(PdfPTable table, String label, String value, Color valueColor) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBackgroundColor(LIGHT_GRAY);
        cell.setPadding(12);
        cell.addElement(new Paragraph(label, SUMMARY_LABEL_FONT));
        Paragraph valP = new Paragraph(value, new Font(Font.HELVETICA, 15, Font.BOLD, valueColor));
        valP.setSpacingBefore(4);
        cell.addElement(valP);
        table.addCell(cell);
    }

    // Readings table

    private void addReadingsTable(Document doc, List<DipReading> readings) throws DocumentException {
        if (readings.isEmpty()) {
            Paragraph empty = new Paragraph("No dip readings recorded in this period.", EMPTY_FONT);
            doc.add(empty);
            return;
        }

        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.3f, 1.1f, 1.1f, 1f, 1.2f, 1.6f});
        table.setSpacingAfter(10);

        for (String h : new String[]{"Date", "Actual (dip)", "System level", "Variance", "Read By", "Notes"}) {
            PdfPCell headerCell = new PdfPCell(new Phrase(h, TABLE_HEADER_FONT));
            headerCell.setBackgroundColor(BRAND_DARK);
            headerCell.setBorder(Rectangle.NO_BORDER);
            headerCell.setPadding(6);
            table.addCell(headerCell);
        }

        int i = 0;
        for (DipReading d : readings) {
            boolean negative = d.hasNegativeVariance();
            Color rowBg = negative ? NEGATIVE_BG : (i++ % 2 == 0 ? Color.WHITE : LIGHT_GRAY);

            addCell(table, DT.format(d.getReadAt()), TABLE_CELL_FONT, Element.ALIGN_LEFT, rowBg);
            addCell(table, formatLitres(d.getActualLitres()) + " L", TABLE_CELL_FONT, Element.ALIGN_RIGHT, rowBg);
            addCell(table, d.getCalculatedLitres() != null ? formatLitres(d.getCalculatedLitres()) + " L" : "\u2014", TABLE_CELL_MUTED, Element.ALIGN_RIGHT, rowBg);

            String varianceText = d.getVarianceLitres() != null
                    ? (d.getVarianceLitres().compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + formatLitres(d.getVarianceLitres()) + " L"
                    : "\u2014";
            PdfPCell varCell = new PdfPCell(new Phrase(varianceText,
                    new Font(Font.HELVETICA, 9, Font.BOLD, negative ? NEGATIVE_RED : POSITIVE_GREEN)));
            varCell.setBorder(Rectangle.BOTTOM);
            varCell.setBorderColor(BORDER_GRAY);
            varCell.setBackgroundColor(rowBg);
            varCell.setPadding(6);
            varCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(varCell);

            addCell(table, d.getReadBy() != null ? d.getReadBy() : "\u2014", TABLE_CELL_MUTED, Element.ALIGN_LEFT, rowBg);
            addCell(table, d.getNotes() != null ? d.getNotes() : "\u2014", TABLE_CELL_MUTED, Element.ALIGN_LEFT, rowBg);
        }

        doc.add(table);
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

    private String formatLitres(BigDecimal litres) {
        DecimalFormat fmt = new DecimalFormat("#,##0.0", LITRES_SYMBOLS);
        return fmt.format(litres != null ? litres : BigDecimal.ZERO);
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

    // Page footer handler — same ColumnText.showTextAligned(...) pattern as
    // ScPoPdfGenerator's FooterHandler; see that class's Javadoc for why the
    // naive beginText()/showText()/endText() pairing is unsafe across pages.

    private static class FooterHandler extends PdfPageEventHelper {
        private final String tankName;
        FooterHandler(String tankName) { this.tankName = tankName; }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                com.lowagie.text.pdf.PdfContentByte cb = writer.getDirectContent();
                Phrase footer = new Phrase(
                        "HandyFlow \u00b7 " + tankName + " \u00b7 Page " + writer.getPageNumber(),
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