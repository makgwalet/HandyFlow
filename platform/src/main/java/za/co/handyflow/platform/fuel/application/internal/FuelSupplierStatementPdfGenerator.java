package za.co.handyflow.platform.fuel.application.internal;

import com.lowagie.text.*;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.fuel.domain.model.FuelReceipt;
import za.co.handyflow.platform.fuel.domain.model.FuelSupplier;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Generates a supplier statement / receiving report PDF — the "no supplier
 * statement/receiving report PDF" gap: "receipts capture supplier, litres,
 * cost per delivery, but there's no rolled-up 'everything received from
 * Supplier X this month' document."
 * <p>
 * Same OpenPDF (com.lowagie.text.*) library, brand colors, and
 * header/divider/footer structure as this codebase's other PDF generators
 * — deliberately NOT iText7, see ScPoPdfGenerator's own Javadoc for why
 * (AGPL licensing without a purchased commercial license for this project).
 * Supplier info block modeled on ScPoPdfGenerator's parties section, since
 * this document is genuinely addressed to a supplier the same way a PO is.
 * <p>
 * Deliberately shows only tankName (resolved by the caller) alongside each
 * receipt, not internal notes or invoice-approval detail — the tank column
 * is standard receiving-report content; nothing here would be inappropriate
 * if this PDF were ever shared with the supplier directly to reconcile a
 * statement of account, which is the whole point of the document.
 */
@Slf4j
@Component
public class FuelSupplierStatementPdfGenerator {

    private static final Color BRAND_DARK  = new Color(27, 58, 107);
    private static final Color BRAND_TEAL  = new Color(13, 148, 136);
    private static final Color LIGHT_GRAY  = new Color(248, 250, 252);
    private static final Color MID_GRAY    = new Color(100, 116, 139);
    private static final Color BORDER_GRAY = new Color(226, 232, 240);

    private static final Font BRAND_FONT         = new Font(Font.HELVETICA, 20, Font.BOLD, BRAND_DARK);
    private static final Font TENANT_FONT        = new Font(Font.HELVETICA, 10, Font.NORMAL, MID_GRAY);
    private static final Font DOC_TYPE_FONT      = new Font(Font.HELVETICA, 16, Font.BOLD, BRAND_TEAL);
    private static final Font PERIOD_FONT        = new Font(Font.HELVETICA, 11, Font.NORMAL, MID_GRAY);
    private static final Font GENERATED_FONT     = new Font(Font.HELVETICA, 9, Font.NORMAL, MID_GRAY);
    private static final Font SECTION_FONT       = new Font(Font.HELVETICA, 10, Font.BOLD, MID_GRAY);
    private static final Font SUPPLIER_NAME_FONT = new Font(Font.HELVETICA, 13, Font.BOLD, BRAND_DARK);
    private static final Font SUPPLIER_LINE_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL, MID_GRAY);
    private static final Font SUMMARY_LABEL_FONT = new Font(Font.HELVETICA, 8, Font.BOLD, MID_GRAY);
    private static final Font TABLE_HEADER_FONT  = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
    private static final Font TABLE_CELL_FONT    = new Font(Font.HELVETICA, 9, Font.NORMAL, BRAND_DARK);
    private static final Font TABLE_CELL_MUTED   = new Font(Font.HELVETICA, 9, Font.NORMAL, MID_GRAY);
    private static final Font TOTAL_ROW_FONT     = new Font(Font.HELVETICA, 10, Font.BOLD, BRAND_DARK);
    private static final Font FOOTER_FONT        = new Font(Font.HELVETICA, 8, Font.NORMAL, MID_GRAY);
    private static final Font EMPTY_FONT         = new Font(Font.HELVETICA, 10, Font.ITALIC, MID_GRAY);

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

    public byte[] generate(FuelSupplier supplier, List<FuelReceipt> receipts, Map<java.util.UUID, String> tankNames,
                           Instant from, Instant to, String tenantName) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 50, 50, 60, 60);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(new FooterHandler(supplier.getName()));

            doc.open();
            addHeader(doc, from, to, tenantName);
            addDivider(doc);
            addSupplierBlock(doc, supplier);
            addSummary(doc, receipts);
            addReceiptsTable(doc, receipts, tankNames);
            doc.close();

            log.info("[FUEL] Generated supplier statement PDF for supplier={}", supplier.getId());
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("[FUEL] Supplier statement PDF generation failed for supplier={}: {}", supplier.getId(), e.getMessage());
            throw new RuntimeException("Failed to generate supplier statement PDF", e);
        }
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

        Paragraph typeP = new Paragraph("SUPPLIER STATEMENT", DOC_TYPE_FONT);
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

    // Supplier block

    private void addSupplierBlock(Document doc, FuelSupplier supplier) throws DocumentException {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBackgroundColor(LIGHT_GRAY);
        cell.setPadding(14);

        cell.addElement(new Paragraph("SUPPLIER", SECTION_FONT));
        Paragraph nameP = new Paragraph(supplier.getName(), SUPPLIER_NAME_FONT);
        nameP.setSpacingBefore(4);
        cell.addElement(nameP);

        String contactLine = joinNonBlank(" \u00b7 ", supplier.getContactName(), supplier.getContactPhone(), supplier.getContactEmail());
        if (!contactLine.isBlank()) {
            Paragraph contactP = new Paragraph(contactLine, SUPPLIER_LINE_FONT);
            contactP.setSpacingBefore(3);
            cell.addElement(contactP);
        }
        if (supplier.getAccountNumber() != null && !supplier.getAccountNumber().isBlank()) {
            Paragraph accP = new Paragraph("Account: " + supplier.getAccountNumber(), SUPPLIER_LINE_FONT);
            accP.setSpacingBefore(3);
            cell.addElement(accP);
        }

        PdfPTable wrapper = new PdfPTable(1);
        wrapper.setWidthPercentage(100);
        wrapper.setSpacingBefore(14);
        wrapper.setSpacingAfter(18);
        wrapper.addCell(cell);
        doc.add(wrapper);
    }

    private String joinNonBlank(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(p);
        }
        return sb.toString();
    }

    // Summary strip

    private void addSummary(Document doc, List<FuelReceipt> receipts) throws DocumentException {
        BigDecimal totalLitres = receipts.stream()
                .map(FuelReceipt::getLitresReceived).filter(l -> l != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCost = receipts.stream()
                .map(FuelReceipt::getTotalCost).filter(c -> c != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        PdfPTable summary = new PdfPTable(3);
        summary.setWidthPercentage(100);
        summary.setSpacingAfter(18);

        addSummaryCell(summary, "DELIVERIES / RECEIPTS", String.valueOf(receipts.size()));
        addSummaryCell(summary, "TOTAL LITRES RECEIVED", formatLitres(totalLitres) + " L");
        addSummaryCell(summary, "TOTAL COST", "R " + formatMoney(totalCost));

        doc.add(summary);
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

    // Receipts table

    private void addReceiptsTable(Document doc, List<FuelReceipt> receipts, Map<java.util.UUID, String> tankNames) throws DocumentException {
        if (receipts.isEmpty()) {
            doc.add(new Paragraph("No receipts from this supplier in this period.", EMPTY_FONT));
            return;
        }

        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.2f, 1.6f, 1f, 1f, 1.2f, 1.4f});
        table.setSpacingAfter(10);

        for (String h : new String[]{"Date", "Tank", "Litres", "Price/L", "Total Cost", "Delivery Note / Invoice"}) {
            PdfPCell headerCell = new PdfPCell(new Phrase(h, TABLE_HEADER_FONT));
            headerCell.setBackgroundColor(BRAND_DARK);
            headerCell.setBorder(Rectangle.NO_BORDER);
            headerCell.setPadding(6);
            table.addCell(headerCell);
        }

        int i = 0;
        BigDecimal grandLitres = BigDecimal.ZERO;
        BigDecimal grandCost = BigDecimal.ZERO;

        for (FuelReceipt r : receipts) {
            Color rowBg = i++ % 2 == 0 ? Color.WHITE : LIGHT_GRAY;

            addCell(table, D.format(r.getReceivedAt()), TABLE_CELL_FONT, Element.ALIGN_LEFT, rowBg);
            addCell(table, tankNames.getOrDefault(r.getTankId(), "\u2014"), TABLE_CELL_MUTED, Element.ALIGN_LEFT, rowBg);
            addCell(table, formatLitres(r.getLitresReceived()) + " L", TABLE_CELL_FONT, Element.ALIGN_RIGHT, rowBg);
            addCell(table, "R " + formatMoney(r.getPricePerLitre()), TABLE_CELL_MUTED, Element.ALIGN_RIGHT, rowBg);
            addCell(table, "R " + formatMoney(r.getTotalCost()), TABLE_CELL_FONT, Element.ALIGN_RIGHT, rowBg);

            String ref = joinNonBlank(" / ", r.getDeliveryNote(), r.getInvoiceRef());
            addCell(table, ref.isBlank() ? "\u2014" : ref, TABLE_CELL_MUTED, Element.ALIGN_LEFT, rowBg);

            grandLitres = grandLitres.add(r.getLitresReceived() != null ? r.getLitresReceived() : BigDecimal.ZERO);
            grandCost = grandCost.add(r.getTotalCost() != null ? r.getTotalCost() : BigDecimal.ZERO);
        }

        PdfPCell totalLabel = new PdfPCell(new Phrase("Total", TOTAL_ROW_FONT));
        totalLabel.setBorder(Rectangle.TOP);
        totalLabel.setBorderColor(BORDER_GRAY);
        totalLabel.setBackgroundColor(LIGHT_GRAY);
        totalLabel.setPadding(7);
        totalLabel.setColspan(2);
        table.addCell(totalLabel);

        addTotalCell(table, formatLitres(grandLitres) + " L", Element.ALIGN_RIGHT);
        addTotalCell(table, "", Element.ALIGN_RIGHT);
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
        private final String supplierName;
        FooterHandler(String supplierName) { this.supplierName = supplierName; }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                com.lowagie.text.pdf.PdfContentByte cb = writer.getDirectContent();
                Phrase footer = new Phrase(
                        "HandyFlow \u00b7 Supplier Statement \u00b7 " + supplierName + " \u00b7 Page " + writer.getPageNumber(),
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