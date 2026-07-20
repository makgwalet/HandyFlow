package za.co.handyflow.platform.accountant.application.internal;

import com.lowagie.text.*;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.accountant.domain.model.AccClient;
import za.co.handyflow.platform.accountant.domain.model.AccountantProfile;
import za.co.handyflow.platform.accountant.domain.model.FeeNote;
import za.co.handyflow.platform.accountant.domain.model.FeeNoteLine;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Generates a Fee Note PDF — flagged in the accountant module audit as a
 * quick win ("data model already has everything needed"). Same OpenPDF
 * library, brand colors, and header/divider/footer structure as this
 * codebase's other PDF generators (ScPoPdfGenerator/ScGrnPdfGenerator in
 * the Supply Chain module) — not iText7, see ScPoPdfGenerator's own
 * Javadoc for why (AGPL licensing).
 * <p>
 * DELIBERATELY NOT LABELLED "SARS-compliant tax invoice": a compliant VAT
 * tax invoice requires both the supplier's and the recipient's physical
 * address (SARS VAT Act requirements). Neither AccountantProfile nor
 * AccClient has any address field at all — confirmed by reading both
 * entities in full, not assumed. Rather than fabricate placeholder
 * addresses to make this look compliant, or silently omit a requirement
 * and call it a tax invoice anyway, this generates a "FEE NOTE" — an
 * honest name for what the data actually supports. A firm using this for
 * real SARS-compliant invoicing needs to add address fields to both
 * entities first; that's a real, separate piece of work, not something
 * this generator can paper over.
 * <p>
 * Also has no bank/EFT details section — AccountantProfile has no
 * banking fields either. The document tells the client to quote the
 * invoice number as their payment reference and leaves it there, rather
 * than inventing bank details that don't exist in the system.
 */
@Slf4j
@Component
public class AccFeeNotePdfGenerator {

    private static final Color BRAND_DARK  = new Color(27, 58, 107);
    private static final Color BRAND_TEAL  = new Color(13, 148, 136);
    private static final Color LIGHT_GRAY  = new Color(248, 250, 252);
    private static final Color MID_GRAY    = new Color(100, 116, 139);
    private static final Color BORDER_GRAY = new Color(226, 232, 240);
    private static final Color PAID_GREEN  = new Color(22, 101, 52);
    private static final Color PAID_GREEN_BG = new Color(220, 252, 231);
    private static final Color OVERDUE_RED = new Color(220, 38, 38);

    private static final Font BRAND_FONT      = new Font(Font.HELVETICA, 20, Font.BOLD, BRAND_DARK);
    private static final Font FIRM_FONT       = new Font(Font.HELVETICA, 10, Font.NORMAL, MID_GRAY);
    private static final Font DOC_TYPE_FONT   = new Font(Font.HELVETICA, 16, Font.BOLD, BRAND_TEAL);
    private static final Font NUMBER_FONT     = new Font(Font.HELVETICA, 11, Font.NORMAL, MID_GRAY);
    private static final Font STATUS_FONT     = new Font(Font.HELVETICA, 9, Font.BOLD, BRAND_TEAL);
    private static final Font STATUS_PAID_FONT   = new Font(Font.HELVETICA, 9, Font.BOLD, PAID_GREEN);
    private static final Font STATUS_OVERDUE_FONT= new Font(Font.HELVETICA, 9, Font.BOLD, OVERDUE_RED);
    private static final Font SECTION_FONT    = new Font(Font.HELVETICA, 10, Font.BOLD, MID_GRAY);
    private static final Font PARTY_NAME_FONT = new Font(Font.HELVETICA, 12, Font.BOLD, BRAND_DARK);
    private static final Font PARTY_LINE_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL, MID_GRAY);
    private static final Font LABEL_FONT      = new Font(Font.HELVETICA, 8, Font.BOLD, MID_GRAY);
    private static final Font VALUE_FONT      = new Font(Font.HELVETICA, 10, Font.NORMAL, BRAND_DARK);
    private static final Font TABLE_HEADER_FONT = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Font TABLE_CELL_FONT   = new Font(Font.HELVETICA, 9, Font.NORMAL, BRAND_DARK);
    private static final Font TABLE_CELL_MUTED  = new Font(Font.HELVETICA, 9, Font.NORMAL, MID_GRAY);
    private static final Font TOTAL_LABEL_FONT  = new Font(Font.HELVETICA, 10, Font.NORMAL, MID_GRAY);
    private static final Font TOTAL_VALUE_FONT  = new Font(Font.HELVETICA, 10, Font.NORMAL, BRAND_DARK);
    private static final Font GRAND_TOTAL_FONT  = new Font(Font.HELVETICA, 13, Font.BOLD, BRAND_DARK);
    private static final Font NOTES_HEAD_FONT   = new Font(Font.HELVETICA, 9, Font.BOLD, MID_GRAY);
    private static final Font NOTES_FONT        = new Font(Font.HELVETICA, 9, Font.NORMAL, BRAND_DARK);
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

    public byte[] generate(FeeNote feeNote, AccClient client, AccountantProfile profile) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 50, 50, 60, 60);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(new FooterHandler(feeNote.getInvoiceNumber()));

            doc.open();
            addHeader(doc, feeNote, profile);
            addDivider(doc, BRAND_DARK);
            addPartiesSection(doc, profile, client);
            addMeta(doc, feeNote);
            addLinesTable(doc, feeNote.getLines());
            addTotals(doc, feeNote);
            if ("PAID".equals(feeNote.getStatus())) addPaidBanner(doc, feeNote);
            if (feeNote.getNotes() != null && !feeNote.getNotes().isBlank()) addNotesBlock(doc, feeNote.getNotes());
            addPaymentInstructions(doc, feeNote);
            doc.close();

            log.info("Generated Fee Note PDF for {}", feeNote.getInvoiceNumber());
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Fee Note PDF generation failed for {}: {}", feeNote.getInvoiceNumber(), e.getMessage());
            throw new RuntimeException("Failed to generate fee note PDF", e);
        }
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private void addHeader(Document doc, FeeNote feeNote, AccountantProfile profile) throws DocumentException {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{1, 1});

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.setPadding(0);
        left.addElement(new Paragraph("HandyFlow", BRAND_FONT));
        Paragraph firmP = new Paragraph(profile.getFirmName(), FIRM_FONT);
        firmP.setSpacingBefore(2);
        left.addElement(firmP);
        if (profile.getVatNumber() != null && !profile.getVatNumber().isBlank()) {
            left.addElement(new Paragraph("VAT: " + profile.getVatNumber(), FIRM_FONT));
        }
        if (profile.getPracticeNumber() != null && !profile.getPracticeNumber().isBlank()) {
            left.addElement(new Paragraph("Practice No: " + profile.getPracticeNumber(), FIRM_FONT));
        }
        header.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setPadding(0);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Paragraph typeP = new Paragraph("FEE NOTE", DOC_TYPE_FONT);
        typeP.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(typeP);

        Paragraph numP = new Paragraph(feeNote.getInvoiceNumber(), NUMBER_FONT);
        numP.setAlignment(Element.ALIGN_RIGHT);
        numP.setSpacingBefore(2);
        right.addElement(numP);

        Font statusFont = "PAID".equals(feeNote.getStatus()) ? STATUS_PAID_FONT
                : "OVERDUE".equals(feeNote.getStatus()) ? STATUS_OVERDUE_FONT
                : STATUS_FONT;
        Paragraph statusP = new Paragraph(feeNote.getStatus(), statusFont);
        statusP.setAlignment(Element.ALIGN_RIGHT);
        statusP.setSpacingBefore(4);
        right.addElement(statusP);

        header.addCell(right);
        doc.add(header);
    }

    // ── From / To parties ───────────────────────────────────────────────────
    // No address block for either party — confirmed neither
    // AccountantProfile nor AccClient has an address field. See this
    // class's own Javadoc for why that's not silently papered over.

    private void addPartiesSection(Document doc, AccountantProfile profile, AccClient client) throws DocumentException {
        PdfPTable parties = new PdfPTable(2);
        parties.setWidthPercentage(100);
        parties.setWidths(new float[]{1, 1});
        parties.setSpacingBefore(14);
        parties.setSpacingAfter(14);

        parties.addCell(partyCell("FROM", profile.getFirmName(),
                joinNonBlank(" \u00b7 ", profile.getContactEmail(), profile.getContactPhone())));

        // FIX: previously showed registeredName in parentheses next to
        // the trading name — e.g. "FastPrint CC (2015/987654/23)".
        // Confirmed via a real generated PDF that this is genuinely
        // ambiguous: that value is unmistakably a CIPC registration
        // number pattern (YYYY/NNNNNN/NN), not a company name, and
        // registrationNumber is its own separate field on AccClient that
        // was never surfaced anywhere in this document at all. Every
        // value is explicitly labeled now, so this can't be misread
        // regardless of what's actually in registeredName for a given
        // client.
        String legalNameLine = (client.getRegisteredName() != null && !client.getRegisteredName().isBlank()
                && !client.getRegisteredName().equals(client.getTradingName()))
                ? client.getRegisteredName() : null;
        String refLine = joinNonBlank(" \u00b7 ",
                client.getRegistrationNumber() != null ? "Reg: " + client.getRegistrationNumber() : null,
                client.getVatNumber() != null ? "VAT: " + client.getVatNumber() : null,
                client.getTaxReferenceNumber() != null ? "Tax Ref: " + client.getTaxReferenceNumber() : null);
        String contactLine = joinNonBlank(" \u00b7 ", client.getContactEmail(), client.getContactPhone());

        parties.addCell(partyCell("TO", client.getTradingName(), legalNameLine, refLine, contactLine));

        doc.add(parties);
    }

    private PdfPCell partyCell(String label, String name, String... extraLines) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBackgroundColor(LIGHT_GRAY);
        cell.setPadding(12);
        cell.addElement(new Paragraph(label, SECTION_FONT));
        Paragraph nameP = new Paragraph(name != null ? name : "\u2014", PARTY_NAME_FONT);
        nameP.setSpacingBefore(4);
        cell.addElement(nameP);
        for (String line : extraLines) {
            if (line == null || line.isBlank()) continue;
            Paragraph p = new Paragraph(line, PARTY_LINE_FONT);
            p.setSpacingBefore(3);
            cell.addElement(p);
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

    // ── Meta grid ─────────────────────────────────────────────────────────────

    private void addMeta(Document doc, FeeNote feeNote) throws DocumentException {
        PdfPTable meta = new PdfPTable(3);
        meta.setWidthPercentage(100);
        meta.setSpacingAfter(16);

        addMetaCell(meta, "INVOICE DATE", D.format(feeNote.getInvoiceDate()));
        addMetaCell(meta, "DUE DATE", D.format(feeNote.getDueDate()));
        addMetaCell(meta, "PAYMENT TERMS", feeNote.isRecurring() ? "Recurring" : "Once-off");

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

    private void addLinesTable(Document doc, java.util.List<FeeNoteLine> lines) throws DocumentException {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{3.2f, 0.9f, 1.1f, 0.8f, 1.2f});
        table.setSpacingAfter(10);

        for (String h : new String[]{"Description", "Qty", "Unit Price", "VAT %", "Amount"}) {
            PdfPCell headerCell = new PdfPCell(new Phrase(h, TABLE_HEADER_FONT));
            headerCell.setBackgroundColor(BRAND_TEAL);
            headerCell.setBorder(Rectangle.NO_BORDER);
            headerCell.setPadding(7);
            table.addCell(headerCell);
        }

        int i = 0;
        for (FeeNoteLine line : lines) {
            Color rowBg = (i++ % 2 == 0) ? Color.WHITE : LIGHT_GRAY;
            addLineCell(table, line.getDescription(), TABLE_CELL_FONT, Element.ALIGN_LEFT, rowBg);
            addLineCell(table, formatQty(line.getQuantity()), TABLE_CELL_FONT, Element.ALIGN_RIGHT, rowBg);
            addLineCell(table, "R " + formatMoney(line.getUnitPrice()), TABLE_CELL_FONT, Element.ALIGN_RIGHT, rowBg);
            addLineCell(table, line.getVatRate().stripTrailingZeros().toPlainString() + "%", TABLE_CELL_MUTED, Element.ALIGN_RIGHT, rowBg);
            addLineCell(table, "R " + formatMoney(line.getAmount()), TABLE_CELL_FONT, Element.ALIGN_RIGHT, rowBg);
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

    private void addTotals(Document doc, FeeNote feeNote) throws DocumentException {
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

        totalsCell.addElement(totalRow("Subtotal (excl. VAT)", feeNote.getSubtotal(), TOTAL_LABEL_FONT, TOTAL_VALUE_FONT));
        totalsCell.addElement(totalRow("VAT", feeNote.getVatAmount(), TOTAL_LABEL_FONT, TOTAL_VALUE_FONT));

        PdfPTable grandRow = new PdfPTable(2);
        grandRow.setWidthPercentage(100);
        grandRow.setWidths(new float[]{1.3f, 1f});
        grandRow.setSpacingBefore(6);
        PdfPCell gLabel = new PdfPCell(new Phrase("Total Due", TOTAL_LABEL_FONT));
        gLabel.setBorder(Rectangle.TOP);
        gLabel.setBorderColor(BORDER_GRAY);
        gLabel.setPaddingTop(6);
        grandRow.addCell(gLabel);
        PdfPCell gValue = new PdfPCell(new Phrase("R " + formatMoney(feeNote.getTotal()), GRAND_TOTAL_FONT));
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

    // ── Paid banner ───────────────────────────────────────────────────────────

    private void addPaidBanner(Document doc, FeeNote feeNote) throws DocumentException {
        PdfPTable banner = new PdfPTable(1);
        banner.setWidthPercentage(100);
        banner.setSpacingAfter(14);
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(PAID_GREEN_BG);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(10);
        String paidLine = "\u2713 Paid in full"
                + (feeNote.getPaidAt() != null ? " \u00b7 " + D.format(feeNote.getPaidAt()) : "");
        Font f = new Font(Font.HELVETICA, 10, Font.BOLD, PAID_GREEN);
        cell.addElement(new Paragraph(paidLine, f));
        banner.addCell(cell);
        doc.add(banner);
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

    // ── Payment instructions ─────────────────────────────────────────────────
    // No bank/EFT details section — AccountantProfile has no banking
    // fields. This tells the client what reference to use rather than
    // inventing account details that don't exist in the system.

    private void addPaymentInstructions(Document doc, FeeNote feeNote) throws DocumentException {
        if ("PAID".equals(feeNote.getStatus())) return;
        Paragraph p = new Paragraph(
                "Please make payment by the due date, quoting " + feeNote.getInvoiceNumber()
                        + " as your payment reference.",
                NOTES_FONT);
        p.setSpacingBefore(4);
        doc.add(p);
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
                // A broken footer must never take down PDF generation for
                // a document that otherwise generated correctly.
            }
        }
    }
}