package za.co.handyflow.platform.legalcompliance.application.internal;

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
import za.co.handyflow.platform.legalcompliance.domain.model.DsarRequest;
import za.co.handyflow.platform.legalcompliance.domain.model.LitigationMatter;
import za.co.handyflow.platform.legalcompliance.domain.model.PopiaProcessingActivity;
import za.co.handyflow.platform.legalcompliance.domain.model.RegulatoryObligation;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Register/log exports for Legal/Compliance — one PDF per aggregate type
 * (regulatory obligations, litigation matters, POPIA processing
 * activities, DSAR requests). "Register export as a PDF" is standard
 * compliance-artifact expectation for every one of these record types (an
 * auditor or regulator asking "show me your obligation register" wants a
 * document, not a login).
 * <p>
 * Built on OpenPDF (com.lowagie.text.*), NOT iText7 (com.itextpdf.*) and
 * NOT OpenHTMLtoPDF — per the corrected finding recorded in this
 * engagement's status doc: iText7-core is AGPL-licensed with no commercial
 * license purchased for this project, and the OpenHTMLtoPDF pattern the
 * original implementation prompt referenced was tried once
 * (RecruiterPdfGenerator's own Javadoc records this) and abandoned. This
 * class follows the same OpenPDF structure, brand colors, and footer
 * technique as ContractPdfGenerator / ScPoPdfGenerator /
 * PayBureauPayslipPdfGenerator — the established precedent, not a new one.
 * <p>
 * Footer uses ColumnText.showTextAligned(...), not hand-paired
 * beginText()/showText()/endText() calls — see ContractPdfGenerator's own
 * FooterHandler Javadoc for the documented reason (unbalanced text-object
 * state across page boundaries on multi-page documents).
 */
@Slf4j
@Component
public class LegalCompliancePdfService {

    private static final Color BRAND_COLOR = new Color(30, 41, 59); // matches ContractPdfGenerator's header color
    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.WHITE);
    private static final Font SUBTITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.WHITE);
    private static final Font TABLE_HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
    private static final Font TABLE_CELL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font FOOTER_FONT = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public byte[] generateObligationRegister(String tenantName, List<RegulatoryObligation> obligations) {
        String[] headers = {"Title", "Category", "Regulation Ref.", "Review Date", "Recurrence", "Status", "Responsible"};
        float[] widths = {3f, 1.5f, 2f, 1.3f, 1.2f, 1.2f, 1.8f};
        return render(tenantName, "Regulatory Obligation Register", headers, widths, obligations, o -> new String[]{
                nullSafe(o.getTitle()),
                nullSafe(o.getCategory() != null ? o.getCategory().name() : null),
                nullSafe(o.getRegulationReference()),
                o.getReviewDate() != null ? o.getReviewDate().format(DATE_FMT) : "",
                nullSafe(o.getRecurrence() != null ? o.getRecurrence().name() : null),
                nullSafe(o.getStatus() != null ? o.getStatus().name() : null),
                nullSafe(o.getResponsibleUserName())
        });
    }

    public byte[] generateLitigationRegister(String tenantName, List<LitigationMatter> matters) {
        String[] headers = {"Matter #", "Title", "Type", "Opposing Party", "Status", "Opened", "Next Key Date"};
        float[] widths = {1.3f, 2.5f, 1.3f, 2f, 1.2f, 1.2f, 1.3f};
        return render(tenantName, "Litigation / Dispute Register", headers, widths, matters, m -> new String[]{
                nullSafe(m.getMatterNumber()),
                nullSafe(m.getTitle()),
                nullSafe(m.getMatterType() != null ? m.getMatterType().name() : null),
                nullSafe(m.getOpposingParty()),
                nullSafe(m.getStatus() != null ? m.getStatus().name() : null),
                m.getOpenedDate() != null ? m.getOpenedDate().format(DATE_FMT) : "",
                m.getNextKeyDate() != null ? m.getNextKeyDate().format(DATE_FMT) : "—"
        });
    }

    public byte[] generatePopiaRegister(String tenantName, List<PopiaProcessingActivity> activities) {
        String[] headers = {"Activity", "Data Category", "Lawful Basis", "Department", "Cross-Border", "Active"};
        float[] widths = {2.5f, 1.6f, 1.6f, 1.8f, 1.3f, 1f};
        return render(tenantName, "POPIA Processing-Activity Register", headers, widths, activities, a -> new String[]{
                nullSafe(a.getActivityName()),
                nullSafe(a.getDataCategory() != null ? a.getDataCategory().name() : null),
                nullSafe(a.getLawfulBasis() != null ? a.getLawfulBasis().name() : null),
                nullSafe(a.getResponsibleDepartment()),
                a.isCrossBorderTransfer() ? "Yes" : "No",
                a.isActive() ? "Yes" : "No"
        });
    }

    public byte[] generateDsarLog(String tenantName, List<DsarRequest> requests) {
        String[] headers = {"Request #", "Type", "Requester", "Received", "Due", "Status"};
        float[] widths = {1.4f, 1.4f, 2.2f, 1.2f, 1.2f, 1.4f};
        return render(tenantName, "DSAR Request Log", headers, widths, requests, r -> new String[]{
                nullSafe(r.getRequestNumber()),
                nullSafe(r.getRequestType() != null ? r.getRequestType().name() : null),
                nullSafe(r.getRequesterName()),
                r.getReceivedDate() != null ? r.getReceivedDate().format(DATE_FMT) : "",
                r.getDueDate() != null ? r.getDueDate().format(DATE_FMT) : "",
                nullSafe(r.getStatus() != null ? r.getStatus().name() : null)
        });
    }

    // ── Shared rendering ─────────────────────────────────────────────────────

    private <T> byte[] render(String tenantName, String reportTitle, String[] headers, float[] widths,
                               List<T> rows, RowMapper<T> mapper) {
        Document doc = new Document(PageSize.A4.rotate(), 30, 30, 60, 40);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new FooterHandler(reportTitle));
            doc.open();

            addHeader(doc, tenantName, reportTitle, rows.size());

            PdfPTable table = new PdfPTable(widths);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10);
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, TABLE_HEADER_FONT));
                cell.setBackgroundColor(BRAND_COLOR);
                cell.setPadding(5);
                table.addCell(cell);
            }
            for (T row : rows) {
                for (String value : mapper.map(row)) {
                    PdfPCell cell = new PdfPCell(new Phrase(value, TABLE_CELL_FONT));
                    cell.setPadding(4);
                    table.addCell(cell);
                }
            }
            if (rows.isEmpty()) {
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
            log.error("Failed to generate Legal/Compliance PDF '{}': {}", reportTitle, e.getMessage(), e);
            throw new RuntimeException("Failed to generate " + reportTitle + " PDF", e);
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

    @FunctionalInterface
    private interface RowMapper<T> {
        String[] map(T row);
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
