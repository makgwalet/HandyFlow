package za.co.handyflow.platform.recruiter.application.internal;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.recruiter.domain.model.RecApplicant;
import za.co.handyflow.platform.recruiter.domain.model.RecApplication;
import za.co.handyflow.platform.recruiter.domain.model.RecInterview;
import za.co.handyflow.platform.recruiter.domain.model.RecJob;
import za.co.handyflow.platform.recruiter.domain.repository.RecApplicantRepository;
import za.co.handyflow.platform.recruiter.domain.repository.RecApplicationRepository;
import za.co.handyflow.platform.recruiter.domain.repository.RecInterviewRepository;
import za.co.handyflow.platform.recruiter.domain.repository.RecJobRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Recruiter module's own PDF generator — offer letters and candidate
 * scorecards. Built on OpenPDF (com.lowagie.text), matching
 * AccFeeNotePdfGenerator and CreativePdfGenerator, not the itext7-core
 * dependency also present in this project's pom.xml (AGPL unless a
 * commercial license has been purchased — see BookingConfirmationPdfService's
 * own Javadoc, which uses itext7-core and flags this same risk itself).
 * <p>
 * A separate service from RecruiterService, mirroring CreativePdfGenerator's
 * relationship to CreativeService — PDF generation queries its own data
 * directly (its own repository injections, its own @Transactional methods)
 * rather than accepting pre-loaded entities from the business-logic
 * service. fetchTenantName() below is a deliberately small, self-contained
 * duplicate of the identically-named method in RecruiterService rather than
 * a cross-service dependency between a "generator" and a "service" — it's
 * three lines; the duplication costs less than the coupling would.
 * <p>
 * REVISION NOTE: this replaces an earlier version of this PDF work that
 * built a shared za.co.handyflow.platform.shared.PdfService intended for
 * use across all modules. That was wrong on two counts once real examples
 * were available: it used openhtmltopdf (a third PDF library, when the
 * project already standardized on OpenPDF for this exact reason across
 * Accountant/Creative/Supply Chain), and no shared PDF service exists
 * anywhere in this codebase — every module owns its own generator.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecruiterPdfGenerator {

    private static final Color BRAND_NAVY  = new Color(27, 58, 107);
    private static final Color MID_GRAY    = new Color(100, 116, 139);
    private static final Color LIGHT_GRAY  = new Color(248, 250, 252);
    private static final Color BORDER_GRAY = new Color(226, 232, 240);

    // Matches AccFeeNotePdfGenerator/CreativePdfGenerator's convention —
    // SAST, not UTC, since that's what a South African hiring committee
    // reading this scorecard expects to see.
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter
            .ofPattern("dd MMM yyyy, HH:mm").withZone(SAST);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private static final Font TITLE_FONT   = new Font(Font.HELVETICA, 16, Font.BOLD, BRAND_NAVY);
    private static final Font META_FONT    = new Font(Font.HELVETICA, 9, Font.NORMAL, MID_GRAY);
    private static final Font BODY_FONT    = new Font(Font.HELVETICA, 11, Font.NORMAL);
    private static final Font BODY_FONT_SM = new Font(Font.HELVETICA, 10, Font.NORMAL);
    private static final Font LABEL_FONT   = new Font(Font.HELVETICA, 11, Font.NORMAL, MID_GRAY);
    private static final Font LABEL_FONT_SM= new Font(Font.HELVETICA, 8, Font.BOLD, MID_GRAY);
    private static final Font BOLD_FONT    = new Font(Font.HELVETICA, 11, Font.BOLD);
    private static final Font BOLD_FONT_SM = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Font SECTION_FONT = new Font(Font.HELVETICA, 12, Font.BOLD, BRAND_NAVY);
    private static final Font HEADING_FONT = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Font CELL_FONT    = new Font(Font.HELVETICA, 9, Font.NORMAL);
    private static final Font CELL_MUTED   = new Font(Font.HELVETICA, 9, Font.NORMAL, MID_GRAY);

    private final RecApplicationRepository applicationRepo;
    private final RecApplicantRepository   applicantRepo;
    private final RecJobRepository         jobRepo;
    private final RecInterviewRepository   interviewRepo;
    private final JdbcTemplate             jdbc;

    // ── Offer letter ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] generateOfferLetter(TenantId tenantId, UUID applicationId) {
        RecApplication app = applicationRepo.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", applicationId.toString()));
        if (app.getOfferedSalary() == null) {
            throw new HandyFlowException(
                    "Offer terms have not been recorded for this application — move it to OFFER "
                            + "with salary details first",
                    HttpStatus.BAD_REQUEST, "OFFER_TERMS_MISSING");
        }
        RecApplicant applicant = applicantRepo.findById(app.getApplicantId())
                .orElseThrow(() -> new ResourceNotFoundException("Applicant", app.getApplicantId().toString()));
        RecJob job = jobRepo.findById(app.getJobId())
                .orElseThrow(() -> new ResourceNotFoundException("Job", app.getJobId().toString()));
        String tenantName = fetchTenantName(tenantId);

        String salaryLine = job.getSalaryCurrency() + " " + app.getOfferedSalary().toPlainString()
                + (app.getOfferedSalaryFrequency() != null
                ? " per " + app.getOfferedSalaryFrequency().toLowerCase() : "");
        String startDateLine = app.getOfferedStartDate() != null
                ? DATE_FMT.format(app.getOfferedStartDate()) : "To be confirmed";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Document doc = new Document(PageSize.A4, 54, 54, 60, 54);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Paragraph title = new Paragraph(tenantName, TITLE_FONT);
            title.setSpacingAfter(2);
            doc.add(title);
            Paragraph meta = new Paragraph("Offer of Employment", META_FONT);
            meta.setSpacingAfter(24);
            doc.add(meta);

            Paragraph greeting = new Paragraph("Dear " + applicant.getFirstName() + ",", BODY_FONT);
            greeting.setSpacingAfter(8);
            doc.add(greeting);

            Paragraph intro = new Paragraph(
                    "We are pleased to offer you the position of " + job.getTitle() + " with " + tenantName
                            + ", subject to the terms below.", BODY_FONT);
            intro.setSpacingAfter(18);
            doc.add(intro);

            PdfPTable terms = new PdfPTable(2);
            terms.setWidthPercentage(100);
            terms.setWidths(new float[]{1, 2});
            addTermRow(terms, "Position", job.getTitle());
            addTermRow(terms, "Department", job.getDepartment() != null ? job.getDepartment() : "\u2014");
            addTermRow(terms, "Start date", startDateLine);
            addTermRow(terms, "Remuneration", salaryLine);
            terms.setSpacingAfter(16);
            doc.add(terms);

            if (app.getOfferBenefits() != null && !app.getOfferBenefits().isBlank()) {
                Paragraph benefitsHead = new Paragraph("Benefits", BOLD_FONT);
                benefitsHead.setSpacingAfter(4);
                doc.add(benefitsHead);
                Paragraph benefits = new Paragraph(app.getOfferBenefits(), BODY_FONT);
                benefits.setSpacingAfter(16);
                doc.add(benefits);
            }

            Paragraph closing = new Paragraph(
                    "Please confirm your acceptance of this offer by replying to this email or contacting us directly.",
                    BODY_FONT);
            closing.setSpacingAfter(30);
            doc.add(closing);

            doc.add(new Paragraph("Yours sincerely,", BODY_FONT));
            doc.add(new Paragraph(tenantName, BODY_FONT));

            doc.close();
        } catch (DocumentException e) {
            log.error("Offer letter PDF generation failed for application={}: {}", applicationId, e.getMessage());
            throw new IllegalStateException("Failed to generate offer letter", e);
        }

        log.info("Generated offer letter PDF for application={}", applicationId);
        return out.toByteArray();
    }

    // ── Candidate scorecard ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] generateScorecard(TenantId tenantId, UUID applicationId) {
        RecApplication app = applicationRepo.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", applicationId.toString()));
        RecApplicant applicant = applicantRepo.findById(app.getApplicantId())
                .orElseThrow(() -> new ResourceNotFoundException("Applicant", app.getApplicantId().toString()));
        RecJob job = jobRepo.findById(app.getJobId())
                .orElseThrow(() -> new ResourceNotFoundException("Job", app.getJobId().toString()));
        List<RecInterview> interviews = interviewRepo.findByApplicationIdOrderByScheduledAtAsc(applicationId);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Document doc = new Document(PageSize.A4, 44, 44, 50, 44);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Paragraph title = new Paragraph(applicant.getFullName(), TITLE_FONT);
            title.setSpacingAfter(2);
            doc.add(title);
            Paragraph meta = new Paragraph("Candidate Scorecard \u2014 " + job.getTitle(), META_FONT);
            meta.setSpacingAfter(16);
            doc.add(meta);

            Paragraph summary = new Paragraph();
            summary.add(new Chunk("Stage: ", BOLD_FONT_SM));
            summary.add(new Chunk(app.getStage() + "     ", BODY_FONT_SM));
            summary.add(new Chunk("Overall score: ", BOLD_FONT_SM));
            summary.add(new Chunk(app.getScore() != null ? app.getScore() + "/5" : "Not scored", BODY_FONT_SM));
            summary.setSpacingAfter(10);
            doc.add(summary);

            if (app.getNotes() != null && !app.getNotes().isBlank()) {
                Paragraph notesHead = new Paragraph("Recruiter notes", BOLD_FONT_SM);
                notesHead.setSpacingAfter(3);
                doc.add(notesHead);
                Paragraph notes = new Paragraph(app.getNotes(), BODY_FONT_SM);
                notes.setSpacingAfter(14);
                doc.add(notes);
            }

            Paragraph interviewsHeading = new Paragraph("Interviews", SECTION_FONT);
            interviewsHeading.setSpacingAfter(6);
            doc.add(interviewsHeading);

            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1.2f, 1.6f, 1.4f, 1.2f, 0.8f});
            for (String h : List.of("Type", "When", "Interviewer", "Outcome", "Score")) {
                PdfPCell header = new PdfPCell(new Phrase(h, HEADING_FONT));
                header.setBackgroundColor(BRAND_NAVY);
                header.setPadding(6);
                header.setBorderColor(BRAND_NAVY);
                table.addCell(header);
            }

            for (RecInterview iv : interviews) {
                addBodyCell(table, iv.getInterviewType());
                addBodyCell(table, iv.getScheduledAt() != null ? DATETIME_FMT.format(iv.getScheduledAt()) : "\u2014");
                addBodyCell(table, iv.getInterviewerName() != null ? iv.getInterviewerName() : "\u2014");
                addBodyCell(table, iv.getOutcome() != null ? iv.getOutcome() : "PENDING");
                addBodyCell(table, iv.getScore() != null ? iv.getScore() + "/5" : "\u2014");
                if (iv.getNotes() != null && !iv.getNotes().isBlank()) {
                    PdfPCell notesCell = new PdfPCell(new Phrase(iv.getNotes(), CELL_MUTED));
                    notesCell.setColspan(5);
                    notesCell.setBorder(Rectangle.BOTTOM);
                    notesCell.setBorderColor(BORDER_GRAY);
                    notesCell.setPadding(6);
                    table.addCell(notesCell);
                }
            }
            doc.add(table);

            doc.close();
        } catch (DocumentException e) {
            log.error("Scorecard PDF generation failed for application={}: {}", applicationId, e.getMessage());
            throw new IllegalStateException("Failed to generate candidate scorecard", e);
        }

        log.info("Generated scorecard PDF for application={}", applicationId);
        return out.toByteArray();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void addTermRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, LABEL_FONT));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPaddingBottom(6);
        table.addCell(labelCell);
        PdfPCell valueCell = new PdfPCell(new Phrase(value, BODY_FONT));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPaddingBottom(6);
        table.addCell(valueCell);
    }

    private void addBodyCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, CELL_FONT));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(BORDER_GRAY);
        cell.setPadding(6);
        table.addCell(cell);
    }

    // Deliberate duplicate of RecruiterService.fetchTenantName() — see class
    // Javadoc for why this isn't a cross-service call instead.
    private String fetchTenantName(TenantId tenantId) {
        try {
            return jdbc.queryForObject(
                    "SELECT name FROM tenants WHERE id = ?",
                    String.class, tenantId.getValue());
        } catch (Exception e) { return "HandyFlow"; }
    }
}