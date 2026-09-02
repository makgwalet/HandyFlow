package za.co.handyflow.platform.collectionsagency.application.internal;

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
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyClient;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyDebtorAccount;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Collections Agency document generation. Built on OpenPDF
 * (com.lowagie.text.*), same structure/brand colors/footer technique as
 * DebtCollectionPdfService/LegalCompliancePdfService — see those
 * classes' own Javadoc for why OpenPDF and not iText7/OpenHTMLtoPDF.
 * <p>
 * Two documents:
 * <ul>
 *   <li>{@link #generatePortfolioStatement} — a creditor client's own
 *   trust/recovery statement: portfolio summary, an aging breakdown of
 *   still-outstanding balances, and a recovery-rate figure. This is the
 *   "client portfolio/recovery reporting" deliverable this module's own
 *   domain analysis called out, and is also what the client portal's
 *   own trust-statement view is built from (see
 *   CollAgencyPortalDataService.getMyTrustStatement()) — this PDF is the
 *   downloadable/printable version of the same underlying data.</li>
 *   <li>{@link #generateDemandLetter} — UNLIKE debtcollection's own
 *   demand letter, this one HARD-CODES the three NCA third-party-
 *   collector disclosures directly into the letter body: this agency
 *   identifies itself as a third-party collector (not the original
 *   creditor), names the actual original creditor, and states the
 *   debtor's statutory rights. This is not optional boilerplate — it is
 *   the documentary equivalent of the same disclosure rule
 *   CollAgencyContactLog.record() enforces for a live contact attempt,
 *   and is called out as a hard requirement in this module's own
 *   package-info.</li>
 * </ul>
 */
@Slf4j
@Component
public class CollAgencyPdfService {

    private static final Color BRAND_COLOR = new Color(30, 41, 59);
    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.WHITE);
    private static final Font SUBTITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.WHITE);
    private static final Font TABLE_HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
    private static final Font TABLE_CELL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font FOOTER_FONT = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
    private static final Font BODY_FONT = FontFactory.getFont(FontFactory.HELVETICA, 11);
    private static final Font BODY_BOLD_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
    private static final Font SECTION_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private static final java.util.Set<String> TERMINAL_STATUSES =
            java.util.Set.of("RECOVERED", "RETURNED_TO_CLIENT", "WRITTEN_OFF", "CLOSED");

    public byte[] generatePortfolioStatement(String agencyName, CollAgencyClient client,
                                              List<CollAgencyDebtorAccount> accounts) {
        Document doc = new Document(PageSize.A4.rotate(), 30, 30, 60, 40);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new FooterHandler("Portfolio & Recovery Statement — " + client.getTradingName()));
            doc.open();
            addHeader(doc, agencyName, "Portfolio & Recovery Statement — " + client.getTradingName(), accounts.size());

            addSummarySection(doc, client, accounts);
            addAgingSection(doc, accounts);

            doc.add(new Paragraph("Placed Accounts", SECTION_FONT));
            doc.add(new Paragraph(" "));

            String[] headers = {"Account Ref", "Debtor", "Original Amount", "Current Balance", "Status", "Placed", "Closed"};
            float[] widths = {1.2f, 2.0f, 1.3f, 1.3f, 1.4f, 1.1f, 1.1f};
            PdfPTable table = new PdfPTable(widths);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10);
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, TABLE_HEADER_FONT));
                cell.setBackgroundColor(BRAND_COLOR);
                cell.setPadding(5);
                table.addCell(cell);
            }
            for (CollAgencyDebtorAccount a : accounts) {
                for (String value : new String[]{
                        nullSafe(a.getAccountReference()),
                        nullSafe(a.getDebtorName()),
                        a.getOriginalDebtAmount() != null ? a.getOriginalDebtAmount().toPlainString() : "",
                        a.getCurrentBalance() != null ? a.getCurrentBalance().toPlainString() : "",
                        nullSafe(a.getStatus()),
                        a.getPlacedDate() != null ? a.getPlacedDate().format(DATE_FMT) : "",
                        a.getClosedDate() != null ? a.getClosedDate().format(DATE_FMT) : "—"
                }) {
                    PdfPCell cell = new PdfPCell(new Phrase(value, TABLE_CELL_FONT));
                    cell.setPadding(4);
                    table.addCell(cell);
                }
            }
            if (accounts.isEmpty()) {
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
            log.error("Failed to generate portfolio statement for client {}: {}", client.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to generate portfolio statement", e);
        }
    }

    public byte[] generateDemandLetter(String agencyName, CollAgencyClient client, CollAgencyDebtorAccount account) {
        Document doc = new Document(PageSize.A4, 50, 50, 60, 50);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new FooterHandler("Demand Letter — " + nullSafe(account.getAccountReference())));
            doc.open();

            String today = LocalDate.now().format(DATE_FMT);
            doc.add(new Paragraph(today, BODY_FONT));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(nullSafe(account.getDebtorName()), BODY_BOLD_FONT));
            if (account.getDebtorAddress() != null) doc.add(new Paragraph(account.getDebtorAddress(), BODY_FONT));
            if (account.getDebtorEmail() != null) doc.add(new Paragraph(account.getDebtorEmail(), BODY_FONT));
            if (account.getDebtorPhone() != null) doc.add(new Paragraph(account.getDebtorPhone(), BODY_FONT));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(
                    "FORMAL DEMAND FOR PAYMENT — Reference: " + nullSafe(account.getAccountReference()), BODY_BOLD_FONT));
            doc.add(new Paragraph(" "));

            // ── Mandatory NCA third-party-collector disclosures — hard-coded, not optional ──
            doc.add(new Paragraph(
                    "NOTICE: " + (agencyName != null ? agencyName : "This agency")
                            + " is a registered third-party debt collector acting on the instruction of "
                            + nullSafe(account.getOriginalCreditorName())
                            + " (the original creditor to whom this debt is owed), and is not itself the "
                            + "original creditor.", BODY_BOLD_FONT));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(
                    "You have certain statutory rights under the National Credit Act, including the right to "
                            + "dispute this debt, to request a copy of your credit agreement, to apply for debt "
                            + "review, and to be treated fairly and lawfully throughout the collection process. "
                            + "If you believe this debt is disputed or incorrect, please contact us immediately "
                            + "using the details below.", BODY_FONT));
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("Dear " + nullSafe(account.getDebtorName()) + ",", BODY_FONT));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(
                    "Our records show an outstanding balance of R "
                            + (account.getCurrentBalance() != null ? account.getCurrentBalance().toPlainString() : "0.00")
                            + " owing to " + nullSafe(account.getOriginalCreditorName())
                            + ", originally dated "
                            + (account.getOriginalDebtDate() != null ? account.getOriginalDebtDate().format(DATE_FMT) : "unknown")
                            + ", which remains unpaid despite prior contact. This letter constitutes formal demand "
                            + "for payment in full.", BODY_FONT));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(
                    "Please settle this account within 7 (seven) days of the date of this letter, or contact us "
                            + "to arrange a structured repayment plan. Failure to respond may result in further "
                            + "recovery action.", BODY_FONT));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Regards,", BODY_FONT));
            doc.add(new Paragraph(agencyName != null ? agencyName : "HandyFlow Collections", BODY_BOLD_FONT));
            doc.add(new Paragraph("on behalf of " + nullSafe(account.getOriginalCreditorName()), BODY_FONT));

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate demand letter for debtor account {}: {}", account.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to generate demand letter", e);
        }
    }

    private void addSummarySection(Document doc, CollAgencyClient client, List<CollAgencyDebtorAccount> accounts) throws Exception {
        BigDecimal totalPlaced = accounts.stream().map(CollAgencyDebtorAccount::getOriginalDebtAmount)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalOutstanding = accounts.stream().map(CollAgencyDebtorAccount::getCurrentBalance)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRecovered = totalPlaced.subtract(totalOutstanding);
        BigDecimal recoveryRatePct = totalPlaced.signum() > 0
                ? totalRecovered.multiply(new BigDecimal("100")).divide(totalPlaced, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        long recoveredCount = accounts.stream().filter(a -> "RECOVERED".equals(a.getStatus())).count();

        doc.add(new Paragraph("Summary", SECTION_FONT));
        doc.add(new Paragraph(" "));
        PdfPTable summary = new PdfPTable(4);
        summary.setWidthPercentage(100);
        summary.setSpacingAfter(14);
        addSummaryCell(summary, "Accounts Placed", String.valueOf(accounts.size()));
        addSummaryCell(summary, "Total Placed Value", "R " + totalPlaced.toPlainString());
        addSummaryCell(summary, "Currently Outstanding", "R " + totalOutstanding.toPlainString());
        addSummaryCell(summary, "Recovery Rate", recoveryRatePct + "% (" + recoveredCount + " account(s) fully recovered)");
        doc.add(summary);
    }

    private void addSummaryCell(PdfPTable table, String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(8);
        cell.setBorderColor(new Color(220, 220, 220));
        cell.addElement(new Paragraph(label, FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY)));
        cell.addElement(new Paragraph(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
        table.addCell(cell);
    }

    /**
     * Aging bucketed on currentBalance for accounts still carrying a
     * balance (terminal statuses excluded — a RECOVERED/WRITTEN_OFF/
     * RETURNED_TO_CLIENT/CLOSED account has nothing left to age),
     * bucketed by days since placedDate — a proxy for "how long this
     * balance has been outstanding with this agency", not the original
     * debt's own age before placement (which this module doesn't track
     * granularly enough to bucket on).
     */
    private void addAgingSection(Document doc, List<CollAgencyDebtorAccount> accounts) throws Exception {
        LocalDate today = LocalDate.now();
        BigDecimal b0to30 = BigDecimal.ZERO, b31to60 = BigDecimal.ZERO, b61to90 = BigDecimal.ZERO, b90plus = BigDecimal.ZERO;
        for (CollAgencyDebtorAccount a : accounts) {
            if (TERMINAL_STATUSES.contains(a.getStatus()) || a.getCurrentBalance() == null
                    || a.getCurrentBalance().signum() <= 0 || a.getPlacedDate() == null) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(a.getPlacedDate(), today);
            if (days <= 30) b0to30 = b0to30.add(a.getCurrentBalance());
            else if (days <= 60) b31to60 = b31to60.add(a.getCurrentBalance());
            else if (days <= 90) b61to90 = b61to90.add(a.getCurrentBalance());
            else b90plus = b90plus.add(a.getCurrentBalance());
        }

        doc.add(new Paragraph("Aging — Outstanding Balances by Days Placed", SECTION_FONT));
        doc.add(new Paragraph(" "));
        PdfPTable aging = new PdfPTable(4);
        aging.setWidthPercentage(100);
        aging.setSpacingAfter(14);
        addSummaryCell(aging, "0–30 days", "R " + b0to30.toPlainString());
        addSummaryCell(aging, "31–60 days", "R " + b31to60.toPlainString());
        addSummaryCell(aging, "61–90 days", "R " + b61to90.toPlainString());
        addSummaryCell(aging, "90+ days", "R " + b90plus.toPlainString());
        doc.add(aging);
    }

    private void addHeader(Document doc, String agencyName, String reportTitle, int rowCount) throws Exception {
        PdfPTable header = new PdfPTable(1);
        header.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(BRAND_COLOR);
        cell.setPadding(12);
        cell.setBorder(0);
        cell.addElement(new Paragraph(reportTitle, TITLE_FONT));
        cell.addElement(new Paragraph(
                (agencyName != null ? agencyName : "HandyFlow") + " · " + rowCount + " record(s) · generated "
                        + LocalDate.now().format(DATE_FMT), SUBTITLE_FONT));
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
