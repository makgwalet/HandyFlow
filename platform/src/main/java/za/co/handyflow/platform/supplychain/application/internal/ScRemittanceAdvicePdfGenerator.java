package za.co.handyflow.platform.supplychain.application.internal;

import com.lowagie.text.*;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplierInvoice;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Generates a remittance advice PDF — confirmation of payment sent to a
 * supplier once their invoice is marked PAID. Flagged in the SCM gap
 * analysis ("remittance advice to the supplier when an invoice is marked
 * paid") as a missing notification entirely — this is the document half
 * of that gap; wiring it into the actual paid-notification email is a
 * separate step from generating it.
 * <p>
 * Scoped to a single invoice, matching what this codebase's data model
 * actually supports — ScmService.markPaid() marks one invoice paid at a
 * time; there's no payment-batch/payment-run entity covering multiple
 * invoices in one payment, so this doesn't invent one.
 * <p>
 * Same OpenPDF library, brand colors, and header/divider/footer structure
 * as ScPoPdfGenerator/ScGrnPdfGenerator — see ScPoPdfGenerator's own
 * Javadoc for why OpenPDF over iText7, and the same hand-built
 * DecimalFormatSymbols / per-call DecimalFormat pattern for the same
 * locale-independence and thread-safety reasons.
 */
@Slf4j
@Component
public class ScRemittanceAdvicePdfGenerator {

    private static final Color BRAND_DARK  = new Color(27, 58, 107);
    private static final Color BRAND_AMBER = new Color(217, 119, 6);
    private static final Color LIGHT_GRAY  = new Color(248, 250, 252);
    private static final Color MID_GRAY    = new Color(100, 116, 139);
    private static final Color BORDER_GRAY = new Color(226, 232, 240);
    private static final Color PAID_GREEN  = new Color(22, 101, 52);
    private static final Color PAID_GREEN_BG = new Color(220, 252, 231);

    private static final Font BRAND_FONT      = new Font(Font.HELVETICA, 20, Font.BOLD, BRAND_DARK);
    private static final Font TENANT_FONT     = new Font(Font.HELVETICA, 10, Font.NORMAL, MID_GRAY);
    private static final Font DOC_TYPE_FONT   = new Font(Font.HELVETICA, 16, Font.BOLD, BRAND_AMBER);
    private static final Font NUMBER_FONT     = new Font(Font.HELVETICA, 11, Font.NORMAL, MID_GRAY);
    private static final Font PAID_FONT       = new Font(Font.HELVETICA, 9, Font.BOLD, PAID_GREEN);
    private static final Font SECTION_FONT    = new Font(Font.HELVETICA, 10, Font.BOLD, MID_GRAY);
    private static final Font PARTY_NAME_FONT = new Font(Font.HELVETICA, 12, Font.BOLD, BRAND_DARK);
    private static final Font PARTY_LINE_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL, MID_GRAY);
    private static final Font LABEL_FONT      = new Font(Font.HELVETICA, 8, Font.BOLD, MID_GRAY);
    private static final Font VALUE_FONT      = new Font(Font.HELVETICA, 10, Font.NORMAL, BRAND_DARK);
    private static final Font TOTAL_LABEL_FONT  = new Font(Font.HELVETICA, 10, Font.NORMAL, MID_GRAY);
    private static final Font TOTAL_VALUE_FONT  = new Font(Font.HELVETICA, 10, Font.NORMAL, BRAND_DARK);
    private static final Font GRAND_TOTAL_FONT  = new Font(Font.HELVETICA, 13, Font.BOLD, BRAND_DARK);
    private static final Font THANKS_FONT       = new Font(Font.HELVETICA, 9, Font.NORMAL, MID_GRAY);
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

    public byte[] generate(ScSupplierInvoice invoice, String tenantName, String tenantVat,
                           String supplierName, String supplierAddress,
                           String supplierContactName, String supplierContactPhone) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 50, 50, 60, 60);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(new FooterHandler(invoice.getInvoiceNumber()));

            doc.open();
            addHeader(doc, invoice, tenantName, tenantVat);
            addDivider(doc, BRAND_DARK);
            addPartiesSection(doc, supplierName, supplierAddress, supplierContactName, supplierContactPhone);
            addPaymentMeta(doc, invoice);
            addAmountSummary(doc, invoice);
            addThanksNote(doc, tenantName);
            doc.close();

            log.info("[SCM] Generated remittance advice PDF for {}", invoice.getInvoiceNumber());
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("[SCM] Remittance advice PDF generation failed for {}: {}",
                    invoice.getInvoiceNumber(), e.getMessage());
            throw new RuntimeException("Failed to generate remittance advice PDF", e);
        }
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private void addHeader(Document doc, ScSupplierInvoice invoice,
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

        Paragraph typeP = new Paragraph("REMITTANCE ADVICE", DOC_TYPE_FONT);
        typeP.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(typeP);

        Paragraph numP = new Paragraph("Ref: " + invoice.getInvoiceNumber(), NUMBER_FONT);
        numP.setAlignment(Element.ALIGN_RIGHT);
        numP.setSpacingBefore(2);
        right.addElement(numP);

        Paragraph paidP = new Paragraph("PAID", PAID_FONT);
        paidP.setAlignment(Element.ALIGN_RIGHT);
        paidP.setSpacingBefore(4);
        right.addElement(paidP);

        header.addCell(right);
        doc.add(header);
    }

    // ── Paid-to party ─────────────────────────────────────────────────────────

    private void addPartiesSection(Document doc, String supplierName, String supplierAddress,
                                   String supplierContactName, String supplierContactPhone) throws DocumentException {
        PdfPTable parties = new PdfPTable(1);
        parties.setWidthPercentage(100);
        parties.setSpacingBefore(14);
        parties.setSpacingAfter(14);

        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBackgroundColor(LIGHT_GRAY);
        cell.setPadding(12);
        cell.addElement(new Paragraph("PAID TO", SECTION_FONT));
        Paragraph nameP = new Paragraph(supplierName != null ? supplierName : "\u2014", PARTY_NAME_FONT);
        nameP.setSpacingBefore(4);
        cell.addElement(nameP);
        if (supplierAddress != null && !supplierAddress.isBlank()) {
            Paragraph addrP = new Paragraph(supplierAddress, PARTY_LINE_FONT);
            addrP.setSpacingBefore(3);
            cell.addElement(addrP);
        }
        String contactLine = joinNonBlank(" \u00b7 ", supplierContactName, supplierContactPhone);
        if (!contactLine.isBlank()) {
            Paragraph contactP = new Paragraph(contactLine, PARTY_LINE_FONT);
            contactP.setSpacingBefore(3);
            cell.addElement(contactP);
        }
        parties.addCell(cell);

        doc.add(parties);
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

    // ── Payment meta grid ─────────────────────────────────────────────────────

    private void addPaymentMeta(Document doc, ScSupplierInvoice invoice) throws DocumentException {
        PdfPTable meta = new PdfPTable(4);
        meta.setWidthPercentage(100);
        meta.setSpacingAfter(16);

        addMetaCell(meta, "SUPPLIER'S INVOICE REF",
                invoice.getSupplierInvoiceRef() != null ? invoice.getSupplierInvoiceRef() : "\u2014");
        addMetaCell(meta, "INVOICE DATE",
                invoice.getInvoiceDate() != null ? D.format(invoice.getInvoiceDate()) : "\u2014");
        addMetaCell(meta, "PAYMENT DATE",
                invoice.getPaidAt() != null ? formatInstant(invoice.getPaidAt()) : "\u2014");
        addMetaCell(meta, "PAYMENT REFERENCE",
                invoice.getPaymentReference() != null ? invoice.getPaymentReference() : "\u2014");

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

    // ── Amount summary ────────────────────────────────────────────────────────

    private void addAmountSummary(Document doc, ScSupplierInvoice invoice) throws DocumentException {
        PdfPTable wrapper = new PdfPTable(2);
        wrapper.setWidthPercentage(100);
        wrapper.setWidths(new float[]{1, 1});
        wrapper.setSpacingAfter(16);

        PdfPCell blank = new PdfPCell();
        blank.setBorder(Rectangle.NO_BORDER);
        wrapper.addCell(blank);

        PdfPCell totalsCell = new PdfPCell();
        totalsCell.setBorder(Rectangle.NO_BORDER);
        totalsCell.setBackgroundColor(PAID_GREEN_BG);
        totalsCell.setPadding(14);

        totalsCell.addElement(totalRow("Subtotal (excl. VAT)", invoice.getSubtotal(), TOTAL_LABEL_FONT, TOTAL_VALUE_FONT));
        totalsCell.addElement(totalRow("VAT", invoice.getVatAmount(), TOTAL_LABEL_FONT, TOTAL_VALUE_FONT));

        PdfPTable grandRow = new PdfPTable(2);
        grandRow.setWidthPercentage(100);
        grandRow.setWidths(new float[]{1.3f, 1f});
        grandRow.setSpacingBefore(6);
        PdfPCell gLabel = new PdfPCell(new Phrase("Amount Paid", TOTAL_LABEL_FONT));
        gLabel.setBorder(Rectangle.TOP);
        gLabel.setBorderColor(BORDER_GRAY);
        gLabel.setPaddingTop(6);
        grandRow.addCell(gLabel);
        PdfPCell gValue = new PdfPCell(new Phrase("R " + formatMoney(invoice.getTotalAmount()), GRAND_TOTAL_FONT));
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

    // ── Thank-you note ────────────────────────────────────────────────────────

    private void addThanksNote(Document doc, String tenantName) throws DocumentException {
        Paragraph p = new Paragraph(
                "This confirms payment of the above invoice by " + tenantName
                        + ". Please retain this remittance advice for your records.",
                THANKS_FONT);
        doc.add(p);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String formatMoney(BigDecimal amount) {
        DecimalFormat fmt = new DecimalFormat("#,##0.00", ZA_SYMBOLS);
        return fmt.format(amount != null ? amount : BigDecimal.ZERO);
    }

    private String formatInstant(Instant instant) {
        return D.format(instant);
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
    // FooterHandler — see that class's own Javadoc for the full reasoning.

    private static class FooterHandler extends PdfPageEventHelper {
        private final String invoiceNumber;
        FooterHandler(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                com.lowagie.text.pdf.PdfContentByte cb = writer.getDirectContent();
                Phrase footer = new Phrase(
                        "HandyFlow \u00b7 " + invoiceNumber + " \u00b7 Page " + writer.getPageNumber(),
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