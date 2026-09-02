package za.co.handyflow.platform.trainingprovider.application.internal;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.trainingprovider.domain.model.*;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * OpenPDF — same library, brand colour and table/footer helper shape as
 * every other module's own PDF service in this engagement. Three
 * documents (one more than Module 4a, since this variant has both an
 * accredited certificate AND a real invoice to produce): the
 * certificate itself, a session attendance register, and a client
 * invoice.
 */
@Service
public class TrainProvPdfService {

    private static final Color BRAND_COLOR = new Color(30, 41, 59);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    // ── Certificate ──────────────────────────────────────────────────────────

    public byte[] generateCertificate(TrainProvCertificate certificate, String providerName) {
        Document document = new Document(PageSize.A4.rotate(), 40, 40, 60, 60);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 26, Font.BOLD, BRAND_COLOR);
            Font subtitleFont = new Font(Font.HELVETICA, 14, Font.NORMAL, Color.DARK_GRAY);
            Font nameFont = new Font(Font.HELVETICA, 22, Font.BOLDITALIC, Color.BLACK);
            Font bodyFont = new Font(Font.HELVETICA, 12, Font.NORMAL, Color.DARK_GRAY);
            Font smallFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.GRAY);

            addCentered(document, "\n", bodyFont);
            addCentered(document, "CERTIFICATE OF COMPLETION", titleFont);
            addCentered(document, providerName, subtitleFont);
            addCentered(document, "\n\n", bodyFont);
            addCentered(document, "This certifies that", bodyFont);
            addCentered(document, "\n", bodyFont);
            addCentered(document, certificate.getDelegateNameSnapshot(), nameFont);
            addCentered(document, "\n", bodyFont);
            addCentered(document, "of " + certificate.getClientNameSnapshot(), bodyFont);
            addCentered(document, "\n", bodyFont);
            addCentered(document, "has successfully completed", bodyFont);
            addCentered(document, "\n", bodyFont);
            addCentered(document, certificate.getCourseTitleSnapshot(), subtitleFont);
            if (certificate.getUnitStandardSnapshot() != null) {
                addCentered(document, "Unit Standard " + certificate.getUnitStandardSnapshot(), smallFont);
            }
            addCentered(document, "\n\n", bodyFont);

            String validity = certificate.getExpiryDate() != null
                    ? "Issued: " + certificate.getIssueDate().format(DATE_FMT) + "   |   Valid until: " + certificate.getExpiryDate().format(DATE_FMT)
                    : "Issued: " + certificate.getIssueDate().format(DATE_FMT) + "   |   No expiry";
            addCentered(document, validity, bodyFont);
            addCentered(document, "\n", bodyFont);
            addCentered(document, "Certificate No. " + certificate.getCertificateNumber(), smallFont);

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate certificate PDF", e);
        }
    }

    private void addCentered(Document document, String text, Font font) throws DocumentException {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_CENTER);
        document.add(p);
    }

    // ── Session attendance register ─────────────────────────────────────────

    public byte[] generateAttendanceRegister(TrainProvSession session, TrainProvCourse course,
                                              List<TrainProvEnrollment> enrollments, String providerName) {
        Document document = new Document(PageSize.A4, 40, 40, 50, 50);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new FooterEvent(providerName));
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD, BRAND_COLOR);
            Font headerFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
            Font bodyFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
            Font metaFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.DARK_GRAY);

            document.add(new Paragraph(providerName, metaFont));
            document.add(new Paragraph("Training Attendance Register", titleFont));
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Course: " + course.getTitle() + " (" + course.getCourseCode() + ")", bodyFont));
            document.add(new Paragraph("Session: " + session.getStartDate().format(DATE_FMT) + " – "
                    + session.getEndDate().format(DATE_FMT) + " (" + session.getSessionType() + ")", bodyFont));
            if (session.getVenue() != null) document.add(new Paragraph("Venue: " + session.getVenue(), bodyFont));
            if (session.getTrainerName() != null) document.add(new Paragraph("Trainer: " + session.getTrainerName(), bodyFont));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(3);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{3f, 2f, 2f});

            for (String col : new String[]{"Delegate", "Status", "Outcome"}) {
                PdfPCell cell = new PdfPCell(new Phrase(col, headerFont));
                cell.setBackgroundColor(BRAND_COLOR);
                cell.setPadding(6);
                table.addCell(cell);
            }

            for (TrainProvEnrollment e : enrollments) {
                table.addCell(new Phrase(e.getDelegateNameSnapshot(), bodyFont));
                table.addCell(new Phrase(e.getStatus(), bodyFont));
                String outcome = e.getPassed() != null ? (e.getPassed() ? "Passed" : "Failed") : "-";
                table.addCell(new Phrase(outcome, bodyFont));
            }
            document.add(table);

            document.add(new Paragraph(" "));
            document.add(new Paragraph("Total enrolled: " + enrollments.size(), metaFont));

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate attendance register PDF", e);
        }
    }

    // ── Client invoice ──────────────────────────────────────────────────────

    public byte[] generateInvoice(TrainProvInvoice invoice, TrainProvClient client, String providerName) {
        Document document = new Document(PageSize.A4, 40, 40, 50, 50);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new FooterEvent(providerName));
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD, BRAND_COLOR);
            Font bodyFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
            Font metaFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.DARK_GRAY);
            Font boldFont = new Font(Font.HELVETICA, 11, Font.BOLD, Color.BLACK);

            document.add(new Paragraph(providerName, metaFont));
            document.add(new Paragraph("Tax Invoice " + invoice.getInvoiceNumber(), titleFont));
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Bill to: " + client.getTradingName(), bodyFont));
            if (client.getAddress() != null) document.add(new Paragraph(client.getAddress(), bodyFont));
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Period: " + invoice.getPeriodStart().format(DATE_FMT) + " – "
                    + invoice.getPeriodEnd().format(DATE_FMT), bodyFont));
            document.add(new Paragraph("Issue date: " + invoice.getIssueDate().format(DATE_FMT), bodyFont));
            document.add(new Paragraph("Due date: " + invoice.getDueDate().format(DATE_FMT), bodyFont));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(60);
            table.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.setWidths(new float[]{2f, 1f});

            addSummaryRow(table, "Delegates trained", String.valueOf(invoice.getDelegateCount()), bodyFont, bodyFont);
            addSummaryRow(table, "Subtotal", "R " + invoice.getSubtotal(), bodyFont, bodyFont);
            addSummaryRow(table, "VAT", "R " + invoice.getVatAmount(), bodyFont, bodyFont);
            addSummaryRow(table, "Total", "R " + invoice.getTotal(), boldFont, boldFont);
            addSummaryRow(table, "Paid to date", "R " + invoice.getAmountPaid(), bodyFont, bodyFont);
            addSummaryRow(table, "Balance due", "R " + invoice.balance(), boldFont, boldFont);
            document.add(table);

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate invoice PDF", e);
        }
    }

    private void addSummaryRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private static class FooterEvent extends PdfPageEventHelper {
        private final String providerName;

        FooterEvent(String providerName) {
            this.providerName = providerName;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Font footerFont = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.GRAY);
            Phrase footer = new Phrase(providerName + " — Page " + writer.getPageNumber(), footerFont);
            com.lowagie.text.pdf.ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_CENTER,
                    footer, (document.right() + document.left()) / 2, document.bottom() - 20, 0);
        }
    }
}
