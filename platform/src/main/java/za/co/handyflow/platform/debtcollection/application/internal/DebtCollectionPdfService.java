package za.co.handyflow.platform.debtcollection.application.internal;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.debtcollection.domain.model.DebtCollectionCase;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Debt Collection document generation — the case register export (same
 * "register as PDF" pattern as every other compliance/log export in this
 * codebase) plus a formal demand letter per case. Built on OpenPDF
 * (com.lowagie.text.*), same structure/brand colors/footer technique as
 * LegalCompliancePdfService/ContractPdfGenerator — see that class's own
 * Javadoc for why OpenPDF and not iText7/OpenHTMLtoPDF.
 * <p>
 * The demand letter is a plain formal-letter layout (not a table), since
 * it is a document sent TO the debtor, not an internal register. It
 * deliberately does NOT include the NCA/Debt-Collectors-Act third-party-
 * collector disclosures (identify as a third-party collector, name the
 * original creditor, state the debtor's statutory rights) — those apply
 * to a registered debt collector acting on someone else's behalf, not an
 * original creditor collecting its own debt, which is exactly the
 * boundary this module was scoped to (see package-info). The Collections
 * Agency variant's own demand-letter generator will need those
 * disclosures baked in as a hard requirement, not optional boilerplate.
 */
@Slf4j
@Component
public class DebtCollectionPdfService {

    private static final Color BRAND_COLOR = new Color(30, 41, 59);
    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.WHITE);
    private static final Font SUBTITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.WHITE);
    private static final Font TABLE_HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
    private static final Font TABLE_CELL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font FOOTER_FONT = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
    private static final Font BODY_FONT = FontFactory.getFont(FontFactory.HELVETICA, 11);
    private static final Font BODY_BOLD_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public byte[] generateCaseRegister(String tenantName, List<DebtCollectionCase> cases) {
        String[] headers = {"Case #", "Debtor", "Status", "Outstanding", "Opened", "Next Action", "Assigned To"};
        float[] widths = {1.2f, 2.2f, 1.4f, 1.4f, 1.2f, 1.3f, 1.6f};
        Document doc = new Document(PageSize.A4.rotate(), 30, 30, 60, 40);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new FooterHandler("Debt Collection Case Register"));
            doc.open();
            addHeader(doc, tenantName, "Debt Collection Case Register", cases.size());

            PdfPTable table = new PdfPTable(widths);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10);
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, TABLE_HEADER_FONT));
                cell.setBackgroundColor(BRAND_COLOR);
                cell.setPadding(5);
                table.addCell(cell);
            }
            for (DebtCollectionCase c : cases) {
                for (String value : new String[]{
                        nullSafe(c.getCaseNumber()),
                        nullSafe(c.getDebtorName()),
                        nullSafe(c.getStatus() != null ? c.getStatus().name() : null),
                        c.getTotalOutstanding() != null ? c.getTotalOutstanding().toPlainString() : "",
                        c.getOpenedDate() != null ? c.getOpenedDate().format(DATE_FMT) : "",
                        c.getNextActionDate() != null ? c.getNextActionDate().format(DATE_FMT) : "—",
                        nullSafe(c.getAssignedToUserName())
                }) {
                    PdfPCell cell = new PdfPCell(new Phrase(value, TABLE_CELL_FONT));
                    cell.setPadding(4);
                    table.addCell(cell);
                }
            }
            if (cases.isEmpty()) {
                PdfPCell empty = new PdfPCell(new Phrase("No records", TABLE_CELL_FONT));
                empty.setColspan(headers.length);
                empty.setPadding(8);
                empty.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(empty);
            }
            doc.add(table);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate Debt Collection case register PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate Debt Collection case register PDF", e);
        }
    }

    public byte[] generateDemandLetter(String tenantName, DebtCollectionCase c) {
        Document doc = new Document(PageSize.A4, 50, 50, 60, 50);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new FooterHandler("Demand Letter — " + c.getCaseNumber()));
            doc.open();

            String today = java.time.LocalDate.now().format(DATE_FMT);
            doc.add(new Paragraph(today, BODY_FONT));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(nullSafe(c.getDebtorName()), BODY_BOLD_FONT));
            if (c.getDebtorEmail() != null) doc.add(new Paragraph(c.getDebtorEmail(), BODY_FONT));
            if (c.getDebtorPhone() != null) doc.add(new Paragraph(c.getDebtorPhone(), BODY_FONT));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("FORMAL DEMAND FOR PAYMENT — Reference: " + c.getCaseNumber(), BODY_BOLD_FONT));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Dear " + nullSafe(c.getDebtorName()) + ",", BODY_FONT));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(
                    "Our records show an outstanding balance of R " + (c.getTotalOutstanding() != null
                            ? c.getTotalOutstanding().toPlainString() : "0.00")
                            + " owing to " + (tenantName != null ? tenantName : "us") + ", covering "
                            + c.getLinkedInvoiceIds().size() + " invoice(s), which remains unpaid despite prior "
                            + "reminders. This letter constitutes formal demand for payment in full.",
                    BODY_FONT));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(
                    "Please settle this account within 7 (seven) days of the date of this letter. Should payment "
                            + "not be received, or should you wish to discuss a structured repayment arrangement, "
                            + "please contact us immediately using the details below to avoid further action, "
                            + "which may include formal handover for legal recovery.",
                    BODY_FONT));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Regards,", BODY_FONT));
            doc.add(new Paragraph(tenantName != null ? tenantName : "HandyFlow", BODY_BOLD_FONT));

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate demand letter for case {}: {}", c.getCaseNumber(), e.getMessage(), e);
            throw new RuntimeException("Failed to generate demand letter", e);
        }
    }

    private void addHeader(Document doc, String tenantName, String reportTitle, int rowCount) throws Exception {
        PdfPTable header = new PdfPTable(1);
        header.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(BRAND_COLOR);
        cell.setPadding(12);
        cell.setBorder(0);
        cell.addElement(new Paragraph(reportTitle, TITLE_FONT));
        cell.addElement(new Paragraph(
                (tenantName != null ? tenantName : "HandyFlow") + " · " + rowCount + " record(s) · generated "
                        + java.time.LocalDate.now().format(DATE_FMT), SUBTITLE_FONT));
        header.addCell(cell);
        doc.add(header);
    }

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }

    private static class FooterHandler extends PdfPageEventHelper {
        private final String reportTitle;

        FooterHandler(String reportTitle) {
            this.reportTitle = reportTitle;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                com.lowagie.text.pdf.PdfContentByte cb = writer.getDirectContent();
                Phrase footer = new Phrase(
                        "HandyFlow · " + reportTitle + " · Page " + writer.getPageNumber(), FOOTER_FONT);
                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, footer,
                        document.leftMargin(), document.bottomMargin() - 20, 0);
            } catch (Exception ignored) {
                // A broken footer must never take down PDF generation for an otherwise-valid document.
            }
        }
    }
}
