package za.co.handyflow.platform.supplychain.application.internal;

import com.lowagie.text.*;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.supplychain.domain.model.ScPoLine;
import za.co.handyflow.platform.supplychain.domain.model.ScPurchaseOrder;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates a formal Purchase Order PDF — flagged in the SCM module gap
 * analysis as "the single most common missing artifact" for a procurement
 * system. "Mark Sent" previously just flipped a status flag with nothing
 * to actually send to the supplier; this is the document that goes with
 * ScmNotificationService.notifyPoSentToSupplier().
 * <p>
 * Uses OpenPDF (com.lowagie.text.*), not iText7 (com.itextpdf.*) — iText7
 * is AGPL-licensed without a commercial license purchased for this
 * project; OpenPDF is LGPL and carries no such obligation. Same library,
 * same brand colors, and the same header/divider/footer structure as
 * every other PDF generator in this codebase (ContractPdfGenerator,
 * FleetLogbookService, CreativePdfGenerator) — this file follows that
 * established pattern rather than inventing a second one.
 * <p>
 * Deliberately uses ScPurchaseOrder.notes only, never internalNotes —
 * internal notes leaking onto a document sent to a supplier would be a
 * real, meaningful bug, not a formatting detail.
 * <p>
 * Footer uses ColumnText.showTextAligned(...) rather than manually paired
 * cb.beginText()/showText()/endText() calls — see ContractPdfGenerator's
 * FooterHandler for the exact documented reason: the naive pairing left
 * PdfContentByte's internal text-object state unbalanced across page
 * boundaries on multi-page documents, throwing
 * IllegalPdfSyntaxException("Unbalanced begin/end text operators").
 */
@Slf4j
@Component
public class ScPoPdfGenerator {

    private static final Color BRAND_DARK  = new Color(27, 58, 107);
    private static final Color BRAND_AMBER = new Color(217, 119, 6);   // SCM accent (#D97706)
    private static final Color LIGHT_GRAY  = new Color(248, 250, 252);
    private static final Color MID_GRAY    = new Color(100, 116, 139);
    private static final Color BORDER_GRAY = new Color(226, 232, 240);

    private static final Font BRAND_FONT     = new Font(Font.HELVETICA, 20, Font.BOLD, BRAND_DARK);
    private static final Font TENANT_FONT    = new Font(Font.HELVETICA, 10, Font.NORMAL, MID_GRAY);
    private static final Font DOC_TYPE_FONT  = new Font(Font.HELVETICA, 16, Font.BOLD, BRAND_AMBER);
    private static final Font NUMBER_FONT    = new Font(Font.HELVETICA, 11, Font.NORMAL, MID_GRAY);
    private static final Font STATUS_FONT    = new Font(Font.HELVETICA, 9, Font.BOLD, BRAND_AMBER);
    private static final Font SECTION_FONT   = new Font(Font.HELVETICA, 10, Font.BOLD, MID_GRAY);
    private static final Font PARTY_NAME_FONT= new Font(Font.HELVETICA, 12, Font.BOLD, BRAND_DARK);
    private static final Font PARTY_LINE_FONT= new Font(Font.HELVETICA, 9, Font.NORMAL, MID_GRAY);
    private static final Font LABEL_FONT     = new Font(Font.HELVETICA, 8, Font.BOLD, MID_GRAY);
    private static final Font VALUE_FONT     = new Font(Font.HELVETICA, 10, Font.NORMAL, BRAND_DARK);
    private static final Font TABLE_HEADER_FONT = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Font TABLE_CELL_FONT   = new Font(Font.HELVETICA, 9, Font.NORMAL, BRAND_DARK);
    private static final Font TABLE_CELL_MUTED  = new Font(Font.HELVETICA, 9, Font.NORMAL, MID_GRAY);
    private static final Font TOTAL_LABEL_FONT  = new Font(Font.HELVETICA, 10, Font.NORMAL, MID_GRAY);
    private static final Font TOTAL_VALUE_FONT  = new Font(Font.HELVETICA, 10, Font.NORMAL, BRAND_DARK);
    private static final Font GRAND_TOTAL_FONT  = new Font(Font.HELVETICA, 13, Font.BOLD, BRAND_DARK);
    private static final Font TERMS_HEAD_FONT   = new Font(Font.HELVETICA, 9, Font.BOLD, MID_GRAY);
    private static final Font TERMS_FONT        = new Font(Font.HELVETICA, 9, Font.NORMAL, BRAND_DARK);
    private static final Font FOOTER_FONT       = new Font(Font.HELVETICA, 8, Font.NORMAL, MID_GRAY);

    private static final DateTimeFormatter D = DateTimeFormatter
            .ofPattern("dd MMM yyyy")
            .withZone(ZoneId.of("Africa/Johannesburg"));

    // FIX: formatMoney() previously used String.format("%,.2f", amount)
    // with no explicit locale — the space-thousands/comma-decimal output
    // ("R 1 320,00") only looked correct by accident of the server's
    // current default JVM locale. Confirmed via a real generated PDF
    // (PO-00012) that it happened to render right today, but that's not
    // guaranteed across environments — a redeploy to a container with a
    // different default locale would silently reformat every amount on
    // every PO PDF with no code change and no warning. Hand-built symbols
    // instead of a named Locale (e.g. "en-ZA") since CLDR's definition of
    // that locale isn't guaranteed consistent across JVM versions either
    // — this pins the exact separators already proven correct, rather
    // than trusting any locale table to agree with it.
    //
    // ZA_SYMBOLS itself is safe to share (DecimalFormatSymbols is
    // effectively immutable once configured and never mutated again),
    // but DecimalFormat itself is documented as NOT thread-safe — and
    // this class is a Spring singleton bean that can receive concurrent
    // PO PDF requests from different users. formatMoney() below builds a
    // fresh DecimalFormat per call from these shared symbols rather than
    // reusing one static instance, which is the standard safe pattern.
    private static final DecimalFormatSymbols ZA_SYMBOLS;
    static {
        ZA_SYMBOLS = new DecimalFormatSymbols();
        ZA_SYMBOLS.setGroupingSeparator(' ');
        ZA_SYMBOLS.setDecimalSeparator(',');
    }

    public byte[] generate(ScPurchaseOrder po, List<ScPoLine> lines,
                           String tenantName, String tenantVat,
                           String supplierAddress, String supplierContactName, String supplierContactPhone) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 50, 50, 60, 60);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(new FooterHandler(po.getOrderNumber()));

            doc.open();
            addHeader(doc, po, tenantName, tenantVat);
            addDivider(doc, BRAND_DARK);
            addPartiesSection(doc, po, supplierAddress, supplierContactName, supplierContactPhone);
            addOrderMeta(doc, po);
            addLinesTable(doc, lines);
            addTotals(doc, po);
            if (po.getTerms() != null && !po.getTerms().isBlank()) addTermsBlock(doc, "Terms", po.getTerms());
            if (po.getNotes() != null && !po.getNotes().isBlank()) addTermsBlock(doc, "Notes", po.getNotes());
            doc.close();

            log.info("[SCM] Generated PO PDF for {}", po.getOrderNumber());
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("[SCM] PO PDF generation failed for {}: {}", po.getOrderNumber(), e.getMessage());
            throw new RuntimeException("Failed to generate purchase order PDF", e);
        }
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private void addHeader(Document doc, ScPurchaseOrder po,
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

        Paragraph typeP = new Paragraph("PURCHASE ORDER", DOC_TYPE_FONT);
        typeP.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(typeP);

        Paragraph numP = new Paragraph(po.getOrderNumber(), NUMBER_FONT);
        numP.setAlignment(Element.ALIGN_RIGHT);
        numP.setSpacingBefore(2);
        right.addElement(numP);

        Paragraph statusP = new Paragraph(po.getStatus().name().replace("_", " "), STATUS_FONT);
        statusP.setAlignment(Element.ALIGN_RIGHT);
        statusP.setSpacingBefore(4);
        right.addElement(statusP);

        header.addCell(right);
        doc.add(header);
    }

    // ── From / To parties ───────────────────────────────────────────────────

    private void addPartiesSection(Document doc, ScPurchaseOrder po, String supplierAddress,
                                   String supplierContactName, String supplierContactPhone) throws DocumentException {
        PdfPTable parties = new PdfPTable(2);
        parties.setWidthPercentage(100);
        parties.setWidths(new float[]{1, 1});
        parties.setSpacingBefore(14);
        parties.setSpacingAfter(14);

        PdfPCell left = partyCell("DELIVER TO",
                po.getDeliverToAddress() != null && !po.getDeliverToAddress().isBlank()
                        ? po.getDeliverToAddress() : "Address to be confirmed",
                null, null);
        parties.addCell(left);

        PdfPCell right = partyCell("SUPPLIER", po.getSupplierName(),
                supplierAddress, joinNonBlank(" \u00b7 ", supplierContactName, supplierContactPhone));
        parties.addCell(right);

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

    // ── Order meta grid ──────────────────────────────────────────────────────

    private void addOrderMeta(Document doc, ScPurchaseOrder po) throws DocumentException {
        PdfPTable meta = new PdfPTable(4);
        meta.setWidthPercentage(100);
        meta.setSpacingAfter(16);

        addMetaCell(meta, "ORDER DATE", po.getOrderDate() != null ? D.format(po.getOrderDate()) : "\u2014");
        addMetaCell(meta, "REQUIRED BY", po.getRequiredByDate() != null ? D.format(po.getRequiredByDate()) : "\u2014");
        addMetaCell(meta, "PROJECT REF", po.getProjectRef() != null ? po.getProjectRef() : "\u2014");
        addMetaCell(meta, "PAYMENT TERMS", "As agreed");

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

    private void addLinesTable(Document doc, List<ScPoLine> lines) throws DocumentException {
        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{3, 1.3f, 1, 1.2f, 0.8f, 1.4f});
        table.setSpacingAfter(10);

        for (String h : new String[]{"Item", "SKU", "Qty", "Unit Cost", "VAT %", "Line Total (excl.)"}) {
            PdfPCell headerCell = new PdfPCell(new Phrase(h, TABLE_HEADER_FONT));
            headerCell.setBackgroundColor(BRAND_AMBER);
            headerCell.setBorder(Rectangle.NO_BORDER);
            headerCell.setPadding(7);
            table.addCell(headerCell);
        }

        int i = 0;
        for (ScPoLine line : lines) {
            Color rowBg = (i++ % 2 == 0) ? Color.WHITE : LIGHT_GRAY;

            addLineCell(table, line.getItemName(), TABLE_CELL_FONT, Element.ALIGN_LEFT, rowBg);
            addLineCell(table, line.getSupplierSku() != null ? line.getSupplierSku() : "\u2014", TABLE_CELL_MUTED, Element.ALIGN_LEFT, rowBg);
            addLineCell(table, formatQty(line.getQtyOrdered()), TABLE_CELL_FONT, Element.ALIGN_RIGHT, rowBg);
            addLineCell(table, "R " + formatMoney(line.getUnitCost()), TABLE_CELL_FONT, Element.ALIGN_RIGHT, rowBg);
            addLineCell(table, line.getVatRate() + "%", TABLE_CELL_MUTED, Element.ALIGN_RIGHT, rowBg);
            addLineCell(table, "R " + formatMoney(line.getLineTotal()), TABLE_CELL_FONT, Element.ALIGN_RIGHT, rowBg);
        }

        doc.add(table);
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

    // ── Totals ────────────────────────────────────────────────────────────────

    private void addTotals(Document doc, ScPurchaseOrder po) throws DocumentException {
        PdfPTable wrapper = new PdfPTable(2);
        wrapper.setWidthPercentage(100);
        // FIX: was {2, 1} — gave the totals block only ~1/3 of the page
        // width. At 137pt of usable space (after the cell's own 14pt
        // padding on each side) split 50/50 by the nested grandRow table
        // below, "Total (incl. VAT)" at 10pt (~85-90pt) and the bold 13pt
        // total figure (~85pt+) each needed more room than their ~68pt
        // column had — confirmed via a real generated PDF (PO-00012)
        // where the total amount visibly wrapped across two lines.
        // {1, 1} roughly doubles the totals block's width.
        wrapper.setWidths(new float[]{1, 1});
        wrapper.setSpacingAfter(16);

        PdfPCell blank = new PdfPCell();
        blank.setBorder(Rectangle.NO_BORDER);
        wrapper.addCell(blank);

        PdfPCell totalsCell = new PdfPCell();
        totalsCell.setBorder(Rectangle.NO_BORDER);
        totalsCell.setBackgroundColor(LIGHT_GRAY);
        totalsCell.setPadding(14);

        totalsCell.addElement(totalRow("Subtotal (excl. VAT)", po.getSubtotal(), TOTAL_LABEL_FONT, TOTAL_VALUE_FONT));
        totalsCell.addElement(totalRow("VAT", po.getVatAmount(), TOTAL_LABEL_FONT, TOTAL_VALUE_FONT));

        PdfPTable grandRow = new PdfPTable(2);
        grandRow.setWidthPercentage(100);
        // FIX: explicit asymmetric split rather than the default even
        // 50/50 — "Total (incl. VAT)" is longer text than the bold total
        // figure needs to balance against; this gives the label enough
        // room without starving the value.
        grandRow.setWidths(new float[]{1.3f, 1f});
        grandRow.setSpacingBefore(6);
        PdfPCell gLabel = new PdfPCell(new Phrase("Total (incl. VAT)", TOTAL_LABEL_FONT));
        gLabel.setBorder(Rectangle.TOP);
        gLabel.setBorderColor(BORDER_GRAY);
        gLabel.setPaddingTop(6);
        grandRow.addCell(gLabel);
        PdfPCell gValue = new PdfPCell(new Phrase("R " + formatMoney(po.getTotalAmount()), GRAND_TOTAL_FONT));
        gValue.setBorder(Rectangle.TOP);
        gValue.setBorderColor(BORDER_GRAY);
        gValue.setPaddingTop(6);
        gValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
        grandRow.addCell(gValue);
        totalsCell.addElement(grandRow);

        wrapper.addCell(totalsCell);
        doc.add(wrapper);
    }

    private PdfPTable totalRow(String label, BigDecimal amount, Font labelFont, Font valueFont) {
        PdfPTable row = new PdfPTable(2);
        row.setWidthPercentage(100);
        // FIX: same unset-width issue as grandRow originally had — no
        // explicit split meant an even 50/50 default, and "Subtotal
        // (excl. VAT)" is an even longer label than "Total (incl. VAT)".
        // Applying the same ratio here for consistency rather than
        // leaving the same class of bug in a sibling method just because
        // it hadn't been caught in a real generated PDF yet.
        row.setWidths(new float[]{1.3f, 1f});
        PdfPCell l = new PdfPCell(new Phrase(label, labelFont));
        l.setBorder(Rectangle.NO_BORDER);
        row.addCell(l);
        PdfPCell v = new PdfPCell(new Phrase("R " + formatMoney(amount), valueFont));
        v.setBorder(Rectangle.NO_BORDER);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        row.addCell(v);
        return row;
    }

    // ── Terms / Notes ─────────────────────────────────────────────────────────

    private void addTermsBlock(Document doc, String heading, String text) throws DocumentException {
        Paragraph head = new Paragraph(heading, TERMS_HEAD_FONT);
        head.setSpacingBefore(4);
        doc.add(head);
        Paragraph body = new Paragraph(text, TERMS_FONT);
        body.setSpacingBefore(3);
        body.setSpacingAfter(10);
        doc.add(body);
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
    // Same ColumnText.showTextAligned(...) pattern as ContractPdfGenerator's
    // FooterHandler — see this file's own class Javadoc for why the naive
    // begin/end pairing is unsafe across multiple pages.

    private static class FooterHandler extends PdfPageEventHelper {
        private final String orderNumber;
        FooterHandler(String orderNumber) { this.orderNumber = orderNumber; }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                com.lowagie.text.pdf.PdfContentByte cb = writer.getDirectContent();
                Phrase footer = new Phrase(
                        "HandyFlow \u00b7 " + orderNumber + " \u00b7 Page " + writer.getPageNumber(),
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