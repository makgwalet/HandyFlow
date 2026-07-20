package za.co.handyflow.platform.supplychain.application.internal;

import com.lowagie.text.*;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.supplychain.domain.model.ScGoodsReceipt;
import za.co.handyflow.platform.supplychain.domain.model.ScGrLine;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates a Goods Received Note PDF — flagged in the SCM gap analysis
 * for "warehouse/delivery sign-off and dispute evidence". Same OpenPDF
 * library (not iText7 — see ScPoPdfGenerator's own Javadoc for why) and
 * the same brand colors/header/divider/footer structure as
 * ScPoPdfGenerator, with a signature block added specifically for this
 * document's actual purpose: something a warehouse worker and a delivery
 * driver can both physically sign at the point of delivery.
 * <p>
 * Money formatting uses the same hand-built DecimalFormatSymbols and
 * per-call DecimalFormat instance as ScPoPdfGenerator — see that class's
 * own comments for why: no implicit JVM-locale dependency, and
 * DecimalFormat isn't thread-safe so a shared static instance would be
 * unsafe on a Spring singleton bean under concurrent requests.
 */
@Slf4j
@Component
public class ScGrnPdfGenerator {

    private static final Color BRAND_DARK  = new Color(27, 58, 107);
    private static final Color BRAND_AMBER = new Color(217, 119, 6);
    private static final Color LIGHT_GRAY  = new Color(248, 250, 252);
    private static final Color MID_GRAY    = new Color(100, 116, 139);
    private static final Color BORDER_GRAY = new Color(226, 232, 240);
    private static final Color REJECT_RED  = new Color(220, 38, 38);

    private static final Font BRAND_FONT      = new Font(Font.HELVETICA, 20, Font.BOLD, BRAND_DARK);
    private static final Font TENANT_FONT     = new Font(Font.HELVETICA, 10, Font.NORMAL, MID_GRAY);
    private static final Font DOC_TYPE_FONT   = new Font(Font.HELVETICA, 16, Font.BOLD, BRAND_AMBER);
    private static final Font NUMBER_FONT     = new Font(Font.HELVETICA, 11, Font.NORMAL, MID_GRAY);
    private static final Font STATUS_FONT     = new Font(Font.HELVETICA, 9, Font.BOLD, BRAND_AMBER);
    private static final Font SECTION_FONT    = new Font(Font.HELVETICA, 10, Font.BOLD, MID_GRAY);
    private static final Font PARTY_NAME_FONT = new Font(Font.HELVETICA, 12, Font.BOLD, BRAND_DARK);
    private static final Font PARTY_LINE_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL, MID_GRAY);
    private static final Font LABEL_FONT      = new Font(Font.HELVETICA, 8, Font.BOLD, MID_GRAY);
    private static final Font VALUE_FONT      = new Font(Font.HELVETICA, 10, Font.NORMAL, BRAND_DARK);
    private static final Font TABLE_HEADER_FONT = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Font TABLE_CELL_FONT   = new Font(Font.HELVETICA, 9, Font.NORMAL, BRAND_DARK);
    private static final Font TABLE_CELL_MUTED  = new Font(Font.HELVETICA, 9, Font.NORMAL, MID_GRAY);
    private static final Font TABLE_CELL_REJECT = new Font(Font.HELVETICA, 9, Font.BOLD, REJECT_RED);
    private static final Font TOTAL_LABEL_FONT  = new Font(Font.HELVETICA, 10, Font.NORMAL, MID_GRAY);
    private static final Font GRAND_TOTAL_FONT  = new Font(Font.HELVETICA, 13, Font.BOLD, BRAND_DARK);
    private static final Font NOTES_HEAD_FONT   = new Font(Font.HELVETICA, 9, Font.BOLD, MID_GRAY);
    private static final Font NOTES_FONT        = new Font(Font.HELVETICA, 9, Font.NORMAL, BRAND_DARK);
    private static final Font SIG_LABEL_FONT    = new Font(Font.HELVETICA, 8, Font.BOLD, MID_GRAY);
    private static final Font FOOTER_FONT       = new Font(Font.HELVETICA, 8, Font.NORMAL, MID_GRAY);

    private static final DateTimeFormatter D = DateTimeFormatter
            .ofPattern("dd MMM yyyy")
            .withZone(ZoneId.of("Africa/Johannesburg"));

    private static final DecimalFormatSymbols ZA_SYMBOLS;
    static {
        ZA_SYMBOLS = new DecimalFormatSymbols();
        ZA_SYMBOLS.setGroupingSeparator(' ');
        ZA_SYMBOLS.setDecimalSeparator(',');
    }

    public byte[] generate(ScGoodsReceipt gr, List<ScGrLine> lines,
                           String tenantName, String tenantVat,
                           String poNumber, String locationName, String locationAddress,
                           String supplierName, String supplierAddress,
                           String supplierContactName, String supplierContactPhone) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 50, 50, 60, 60);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(new FooterHandler(gr.getReceiptNumber()));

            doc.open();
            addHeader(doc, gr, tenantName, tenantVat);
            addDivider(doc, BRAND_DARK);
            addPartiesSection(doc, locationName, locationAddress, supplierName, supplierAddress,
                    supplierContactName, supplierContactPhone);
            addReceiptMeta(doc, gr, poNumber);
            addLinesTable(doc, lines);
            addValueSummary(doc, lines);
            if (gr.getNotes() != null && !gr.getNotes().isBlank()) addNotesBlock(doc, gr.getNotes());
            addSignatureBlock(doc);
            doc.close();

            log.info("[SCM] Generated GRN PDF for {}", gr.getReceiptNumber());
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("[SCM] GRN PDF generation failed for {}: {}", gr.getReceiptNumber(), e.getMessage());
            throw new RuntimeException("Failed to generate goods received note PDF", e);
        }
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private void addHeader(Document doc, ScGoodsReceipt gr,
                           String tenantName, String tenantVat) throws DocumentException {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{1, 1});

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.setPadding(0);
        left.addElement(new Paragraph("HandyFlow", BRAND_FONT));
        Paragraph tenantP = new Paragraph(tenantName, TENANT_FONT);
        tenantP.setSpacingBefore(2);
        left.addElement(tenantP);
        if (tenantVat != null && !tenantVat.isBlank()) {
            left.addElement(new Paragraph("VAT: " + tenantVat, TENANT_FONT));
        }
        header.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setPadding(0);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Paragraph typeP = new Paragraph("GOODS RECEIVED NOTE", DOC_TYPE_FONT);
        typeP.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(typeP);

        Paragraph numP = new Paragraph(gr.getReceiptNumber(), NUMBER_FONT);
        numP.setAlignment(Element.ALIGN_RIGHT);
        numP.setSpacingBefore(2);
        right.addElement(numP);

        Paragraph statusP = new Paragraph(gr.getStatus(), STATUS_FONT);
        statusP.setAlignment(Element.ALIGN_RIGHT);
        statusP.setSpacingBefore(4);
        right.addElement(statusP);

        header.addCell(right);
        doc.add(header);
    }

    // ── Delivered-to / Supplier parties ─────────────────────────────────────

    private void addPartiesSection(Document doc, String locationName, String locationAddress,
                                   String supplierName, String supplierAddress,
                                   String supplierContactName, String supplierContactPhone) throws DocumentException {
        PdfPTable parties = new PdfPTable(2);
        parties.setWidthPercentage(100);
        parties.setWidths(new float[]{1, 1});
        parties.setSpacingBefore(14);
        parties.setSpacingAfter(14);

        parties.addCell(partyCell("DELIVERED TO", locationName, locationAddress, null));
        parties.addCell(partyCell("SUPPLIER", supplierName, supplierAddress,
                joinNonBlank(" \u00b7 ", supplierContactName, supplierContactPhone)));

        doc.add(parties);
    }

    private PdfPCell partyCell(String label, String name, String address, String contactLine) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBackgroundColor(LIGHT_GRAY);
        cell.setPadding(12);
        cell.addElement(new Paragraph(label, SECTION_FONT));
        Paragraph nameP = new Paragraph(name != null ? name : "\u2014", PARTY_NAME_FONT);
        nameP.setSpacingBefore(4);
        cell.addElement(nameP);
        if (address != null && !address.isBlank()) {
            Paragraph addrP = new Paragraph(address, PARTY_LINE_FONT);
            addrP.setSpacingBefore(3);
            cell.addElement(addrP);
        }
        if (contactLine != null && !contactLine.isBlank()) {
            Paragraph contactP = new Paragraph(contactLine, PARTY_LINE_FONT);
            contactP.setSpacingBefore(3);
            cell.addElement(contactP);
        }
        return cell;
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

    // ── Receipt meta grid ─────────────────────────────────────────────────────

    private void addReceiptMeta(Document doc, ScGoodsReceipt gr, String poNumber) throws DocumentException {
        PdfPTable meta = new PdfPTable(4);
        meta.setWidthPercentage(100);
        meta.setSpacingAfter(16);

        addMetaCell(meta, "PO REFERENCE", poNumber != null ? poNumber : "\u2014");
        addMetaCell(meta, "DELIVERY NOTE REF", gr.getDeliveryNoteRef() != null ? gr.getDeliveryNoteRef() : "\u2014");
        addMetaCell(meta, "RECEIVED DATE", gr.getReceivedDate() != null ? D.format(gr.getReceivedDate()) : "\u2014");
        addMetaCell(meta, "RECEIVED BY", gr.getReceivedByName() != null ? gr.getReceivedByName() : "\u2014");

        doc.add(meta);
    }

    private void addMetaCell(PdfPTable table, String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(4);
        cell.addElement(new Paragraph(label, LABEL_FONT));
        Paragraph valP = new Paragraph(value, VALUE_FONT);
        valP.setSpacingBefore(2);
        cell.addElement(valP);
        table.addCell(cell);
    }

    // ── Line items ────────────────────────────────────────────────────────────

    private void addLinesTable(Document doc, List<ScGrLine> lines) throws DocumentException {
        // NEW: confirmed via a real generated GRN (GR-00004) that a
        // goods receipt with no ScGrLine rows renders a technically-
        // complete-looking table header and a confident "R 0,00" total,
        // with nothing telling the reader that's wrong rather than a
        // genuine zero-value delivery. Most likely cause is data that
        // predates line-level recording (bypassed postGoodsReceipt()
        // entirely — same root cause already confirmed once this session
        // for the PO-number sequence collision) rather than a rendering
        // bug, but this document should never silently look complete
        // when it isn't, regardless of why the data is missing.
        if (lines.isEmpty()) {
            Paragraph warn = new Paragraph(
                    "No line items are recorded against this goods receipt. This may indicate the receipt "
                            + "predates line-level detail tracking, or that line data was not captured at receiving time.",
                    NOTES_FONT);
            PdfPTable warnTable = new PdfPTable(1);
            warnTable.setWidthPercentage(100);
            warnTable.setSpacingAfter(10);
            PdfPCell warnCell = new PdfPCell();
            warnCell.setBackgroundColor(new Color(254, 242, 242));
            warnCell.setBorderColor(new Color(252, 165, 165));
            warnCell.setPadding(10);
            warnCell.addElement(warn);
            warnTable.addCell(warnCell);
            doc.add(warnTable);
            return;
        }

        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2.6f, 1, 1, 1, 1.1f, 1.3f});
        table.setSpacingAfter(10);

        for (String h : new String[]{"Item", "Qty Ordered", "Qty Received", "Qty Rejected", "Condition", "Lot / Expiry"}) {
            PdfPCell headerCell = new PdfPCell(new Phrase(h, TABLE_HEADER_FONT));
            headerCell.setBackgroundColor(BRAND_AMBER);
            headerCell.setBorder(Rectangle.NO_BORDER);
            headerCell.setPadding(7);
            table.addCell(headerCell);
        }

        int i = 0;
        for (ScGrLine line : lines) {
            Color rowBg = (i++ % 2 == 0) ? Color.WHITE : LIGHT_GRAY;
            boolean hasRejects = line.getQtyRejected() != null && line.getQtyRejected().compareTo(BigDecimal.ZERO) > 0;

            addLineCell(table, line.getItemName(), TABLE_CELL_FONT, Element.ALIGN_LEFT, rowBg);
            addLineCell(table, formatQty(line.getQtyOrdered()), TABLE_CELL_FONT, Element.ALIGN_RIGHT, rowBg);
            addLineCell(table, formatQty(line.getQtyReceived()), TABLE_CELL_FONT, Element.ALIGN_RIGHT, rowBg);
            addLineCell(table, formatQty(line.getQtyRejected()), hasRejects ? TABLE_CELL_REJECT : TABLE_CELL_MUTED, Element.ALIGN_RIGHT, rowBg);
            addLineCell(table, line.getCondition() != null ? line.getCondition() : "\u2014", TABLE_CELL_MUTED, Element.ALIGN_LEFT, rowBg);

            String lotExpiry = joinNonBlank(" \u00b7 ",
                    line.getLotNumber(), line.getExpiryDate() != null ? D.format(line.getExpiryDate()) : null);
            addLineCell(table, lotExpiry.isBlank() ? "\u2014" : lotExpiry, TABLE_CELL_MUTED, Element.ALIGN_LEFT, rowBg);
        }

        doc.add(table);

        boolean anyRejections = lines.stream().anyMatch(l ->
                l.getRejectionReason() != null && !l.getRejectionReason().isBlank());
        if (anyRejections) {
            Paragraph rejHead = new Paragraph("Rejection Notes", NOTES_HEAD_FONT);
            rejHead.setSpacingBefore(4);
            doc.add(rejHead);
            for (ScGrLine line : lines) {
                if (line.getRejectionReason() != null && !line.getRejectionReason().isBlank()) {
                    Paragraph p = new Paragraph(line.getItemName() + ": " + line.getRejectionReason(), NOTES_FONT);
                    p.setSpacingBefore(2);
                    doc.add(p);
                }
            }
        }
    }

    private void addLineCell(PdfPTable table, String text, Font font, int align, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(BORDER_GRAY);
        cell.setBackgroundColor(bg);
        cell.setPadding(7);
        cell.setHorizontalAlignment(align);
        table.addCell(cell);
    }

    // ── Value summary ─────────────────────────────────────────────────────────
    // Deliberately simpler than the PO PDF's totals block — a GRN is
    // evidence of physical delivery, not a billing document, so this is
    // "value of goods received" for reference, not a VAT-broken-out total.

    private void addValueSummary(Document doc, List<ScGrLine> lines) throws DocumentException {
        if (lines.isEmpty()) return; // warning box in addLinesTable() already covers this case
        BigDecimal totalValue = lines.stream()
                .map(l -> {
                    BigDecimal qty = l.getQtyReceived() != null ? l.getQtyReceived() : BigDecimal.ZERO;
                    BigDecimal cost = l.getUnitCost() != null ? l.getUnitCost() : BigDecimal.ZERO;
                    return qty.multiply(cost);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        PdfPTable wrapper = new PdfPTable(2);
        wrapper.setWidthPercentage(100);
        wrapper.setWidths(new float[]{1, 1});
        wrapper.setSpacingAfter(16);

        PdfPCell blank = new PdfPCell();
        blank.setBorder(Rectangle.NO_BORDER);
        wrapper.addCell(blank);

        PdfPCell totalsCell = new PdfPCell();
        totalsCell.setBorder(Rectangle.NO_BORDER);
        totalsCell.setBackgroundColor(LIGHT_GRAY);
        totalsCell.setPadding(14);

        PdfPTable row = new PdfPTable(2);
        row.setWidthPercentage(100);
        row.setWidths(new float[]{1.3f, 1f});
        PdfPCell l = new PdfPCell(new Phrase("Total Value Received", TOTAL_LABEL_FONT));
        l.setBorder(Rectangle.NO_BORDER);
        row.addCell(l);
        PdfPCell v = new PdfPCell(new Phrase("R " + formatMoney(totalValue), GRAND_TOTAL_FONT));
        v.setBorder(Rectangle.NO_BORDER);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        row.addCell(v);
        totalsCell.addElement(row);

        wrapper.addCell(totalsCell);
        doc.add(wrapper);
    }

    // ── Notes ─────────────────────────────────────────────────────────────────

    private void addNotesBlock(Document doc, String text) throws DocumentException {
        Paragraph head = new Paragraph("Notes", NOTES_HEAD_FONT);
        head.setSpacingBefore(4);
        doc.add(head);
        Paragraph body = new Paragraph(text, NOTES_FONT);
        body.setSpacingBefore(3);
        body.setSpacingAfter(10);
        doc.add(body);
    }

    // ── Signature block ───────────────────────────────────────────────────────
    // NEW: the actual point of a GRN per the gap analysis — "for
    // warehouse/delivery sign-off and dispute evidence". Two blank-line
    // boxes a warehouse worker and a delivery driver/supplier rep can
    // both physically sign at the point of delivery. Nothing before this
    // point in the document had any sign-off affordance at all.

    private void addSignatureBlock(Document doc) throws DocumentException {
        PdfPTable sig = new PdfPTable(2);
        sig.setWidthPercentage(100);
        sig.setWidths(new float[]{1, 1});
        sig.setSpacingBefore(20);

        sig.addCell(signatureCell("RECEIVED BY (WAREHOUSE)"));
        sig.addCell(signatureCell("DELIVERED BY (SUPPLIER / DRIVER)"));

        doc.add(sig);
    }

    private PdfPCell signatureCell(String label) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(8);

        PdfPTable line = new PdfPTable(1);
        line.setWidthPercentage(100);
        PdfPCell lineCell = new PdfPCell();
        lineCell.setFixedHeight(28);
        lineCell.setBorder(Rectangle.BOTTOM);
        lineCell.setBorderColor(MID_GRAY);
        line.addCell(lineCell);
        cell.addElement(line);

        Paragraph labelP = new Paragraph(label, SIG_LABEL_FONT);
        labelP.setSpacingBefore(4);
        cell.addElement(labelP);

        Paragraph dateP = new Paragraph("Signature & Date", PARTY_LINE_FONT);
        dateP.setSpacingBefore(2);
        cell.addElement(dateP);

        return cell;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String formatMoney(BigDecimal amount) {
        DecimalFormat fmt = new DecimalFormat("#,##0.00", ZA_SYMBOLS);
        return fmt.format(amount != null ? amount : BigDecimal.ZERO);
    }

    private String formatQty(BigDecimal qty) {
        if (qty == null) return "0";
        return qty.stripTrailingZeros().toPlainString();
    }

    private void addDivider(Document doc, Color color) throws DocumentException {
        PdfPTable line = new PdfPTable(1);
        line.setWidthPercentage(100);
        line.setSpacingBefore(8);
        line.setSpacingAfter(8);
        PdfPCell cell = new PdfPCell();
        cell.setFixedHeight(1);
        cell.setBackgroundColor(color);
        cell.setBorder(Rectangle.NO_BORDER);
        line.addCell(cell);
        doc.add(line);
    }

    // ── Page footer handler ───────────────────────────────────────────────────
    // Same ColumnText.showTextAligned(...) pattern as ScPoPdfGenerator's
    // FooterHandler — see that class's own Javadoc for why the naive
    // begin/end pairing is unsafe across multiple pages.

    private static class FooterHandler extends PdfPageEventHelper {
        private final String receiptNumber;
        FooterHandler(String receiptNumber) { this.receiptNumber = receiptNumber; }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                com.lowagie.text.pdf.PdfContentByte cb = writer.getDirectContent();
                Phrase footer = new Phrase(
                        "HandyFlow \u00b7 " + receiptNumber + " \u00b7 Page " + writer.getPageNumber(),
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