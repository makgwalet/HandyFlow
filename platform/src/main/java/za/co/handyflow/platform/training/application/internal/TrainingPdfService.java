package za.co.handyflow.platform.training.application.internal;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.training.domain.model.TrainingCertificate;
import za.co.handyflow.platform.training.domain.model.TrainingCourse;
import za.co.handyflow.platform.training.domain.model.TrainingEnrollment;
import za.co.handyflow.platform.training.domain.model.TrainingSession;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * OpenPDF (com.lowagie.text.*) — same library and brand colour every
 * other module's own PDF service in this engagement uses. Two
 * documents: a formal completion certificate (landscape, decorative —
 * the one document in this module meant to be printed/framed, not just
 * filed) and a session attendance register (portrait, the same
 * table-register export convention as every other compliance/log
 * export in this codebase).
 */
@Service
public class TrainingPdfService {

    private static final Color BRAND_COLOR = new Color(30, 41, 59);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    // ── Certificate ──────────────────────────────────────────────────────────

    public byte[] generateCertificate(TrainingCertificate certificate, String tenantName) {
        Document document = new Document(PageSize.A4.rotate(), 40, 40, 60, 60);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 28, Font.BOLD, BRAND_COLOR);
            Font subtitleFont = new Font(Font.HELVETICA, 14, Font.NORMAL, Color.DARK_GRAY);
            Font nameFont = new Font(Font.HELVETICA, 22, Font.BOLDITALIC, Color.BLACK);
            Font bodyFont = new Font(Font.HELVETICA, 12, Font.NORMAL, Color.DARK_GRAY);
            Font smallFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.GRAY);

            addCentered(document, "\n", bodyFont);
            addCentered(document, "CERTIFICATE OF COMPLETION", titleFont);
            addCentered(document, tenantName, subtitleFont);
            addCentered(document, "\n\n", bodyFont);
            addCentered(document, "This certifies that", bodyFont);
            addCentered(document, "\n", bodyFont);
            addCentered(document, certificate.getEmployeeNameSnapshot(), nameFont);
            addCentered(document, "\n", bodyFont);
            addCentered(document, "has successfully completed", bodyFont);
            addCentered(document, "\n", bodyFont);
            addCentered(document, certificate.getCourseTitleSnapshot(), subtitleFont);
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

    public byte[] generateAttendanceRegister(TrainingSession session, TrainingCourse course,
                                              List<TrainingEnrollment> enrollments, String tenantName) {
        Document document = new Document(PageSize.A4, 40, 40, 50, 50);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new FooterEvent());
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD, BRAND_COLOR);
            Font headerFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
            Font bodyFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
            Font metaFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.DARK_GRAY);

            document.add(new Paragraph(tenantName, metaFont));
            document.add(new Paragraph("Training Attendance Register", titleFont));
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Course: " + course.getTitle() + " (" + course.getCourseCode() + ")", bodyFont));
            document.add(new Paragraph("Session: " + session.getStartDate().format(DATE_FMT) + " – "
                    + session.getEndDate().format(DATE_FMT), bodyFont));
            if (session.getVenue() != null) document.add(new Paragraph("Venue: " + session.getVenue(), bodyFont));
            if (session.getTrainerName() != null) document.add(new Paragraph("Trainer: " + session.getTrainerName(), bodyFont));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{2.5f, 1.5f, 1.5f, 2.5f});

            for (String col : new String[]{"Employee", "Emp. No.", "Status", "Outcome"}) {
                PdfPCell cell = new PdfPCell(new Phrase(col, headerFont));
                cell.setBackgroundColor(BRAND_COLOR);
                cell.setPadding(6);
                table.addCell(cell);
            }

            for (TrainingEnrollment e : enrollments) {
                table.addCell(new Phrase(e.getEmployeeNameSnapshot(), bodyFont));
                table.addCell(new Phrase(e.getEmployeeNumberSnapshot() != null ? e.getEmployeeNumberSnapshot() : "-", bodyFont));
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

    private static class FooterEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Font footerFont = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.GRAY);
            Phrase footer = new Phrase("Generated by HandyFlow — Page " + writer.getPageNumber(), footerFont);
            com.lowagie.text.pdf.ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_CENTER,
                    footer, (document.right() + document.left()) / 2, document.bottom() - 20, 0);
        }
    }
}
