package za.co.handyflow.platform.creative.application.internal;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.creative.domain.model.CreDeliverable;
import za.co.handyflow.platform.creative.domain.model.CreJob;
import za.co.handyflow.platform.creative.domain.model.CreProof;
import za.co.handyflow.platform.creative.domain.repository.CreDeliverableRepository;
import za.co.handyflow.platform.creative.domain.repository.CreJobRepository;
import za.co.handyflow.platform.creative.domain.repository.CreProofRepository;
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
 * The three PDFs flagged as missing in the Creative module review:
 *   - Approval certificate: highest value per the review — every field it
 *     needs (approver name/email/IP, timestamp) was already being captured
 *     on CreProof.approve(), just never packaged into anything. Kept as a
 *     SEPARATE document from the proof file itself (not baked into the same
 *     PDF), matching the DocuSign/Adobe Sign convention the review called
 *     out as the more legally defensible pattern — the proof stays a clean
 *     asset for the client, the certificate is the compliance artifact.
 *   - Job brief: for handing to a freelancer or printing for an in-person
 *     client meeting.
 *   - Deliverables manifest: a handoff/invoicing record of what was
 *     delivered and when, for jobs with more than one final file.
 * <p>
 * A separate service from CreativeService, mirroring FleetLogbookService's
 * relationship to FleetService — PDF generation queries its own data
 * directly rather than routing through the business-logic service.
 * fetchTenantName()/fetchUserName() below are deliberately small,
 * self-contained duplicates of the identically-named methods in
 * CreativeService rather than a cross-service dependency between a
 * "generator" and a "service" — each is three lines; the duplication costs
 * less than the coupling would.
 * <p>
 * LICENSING NOTE: built on OpenPDF, not the itext7-core dependency also
 * present in this project's pom.xml — see FleetLogbookService's Javadoc for
 * the full reasoning (itext7-core is AGPL unless a commercial license has
 * been purchased).
 */
@Service
@RequiredArgsConstructor
public class CreativePdfGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMMM yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd MMMM yyyy, HH:mm");
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private static final Font TITLE_FONT   = new Font(Font.HELVETICA, 20, Font.BOLD, new Color(27, 58, 107));
    private static final Font SUBTITLE_FONT= new Font(Font.HELVETICA, 11, Font.NORMAL, new Color(100, 116, 139));
    private static final Font HEADING_FONT = new Font(Font.HELVETICA, 11, Font.BOLD, Color.WHITE);
    private static final Font BODY_FONT    = new Font(Font.HELVETICA, 10);
    private static final Font BODY_BOLD    = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Font LABEL_FONT   = new Font(Font.HELVETICA, 8, Font.BOLD, new Color(148, 163, 184));
    private static final Color BRAND_NAVY  = new Color(27, 58, 107);
    private static final Color LIGHT_GREY  = new Color(248, 250, 252);
    private static final Color BORDER_GREY = new Color(226, 232, 240);

    private final CreJobRepository jobRepo;
    private final CreProofRepository proofRepo;
    private final CreDeliverableRepository deliverableRepo;
    private final JdbcTemplate jdbc;

    // ── Approval Certificate ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] generateApprovalCertificate(TenantId tenantId, UUID jobId, UUID proofId) {
        CreJob job = findJob(tenantId, jobId);
        CreProof proof = proofRepo.findById(proofId)
                .orElseThrow(() -> new ResourceNotFoundException("Proof", proofId.toString()));
        if (!proof.isApproved()) {
            throw new HandyFlowException(
                    "Only an APPROVED proof has a certificate — this one is currently " + proof.getStatus() + ".",
                    HttpStatus.BAD_REQUEST, "PROOF_NOT_APPROVED");
        }
        String tenantName = fetchTenantName(tenantId);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Document doc = new Document(PageSize.A4, 54, 54, 54, 54);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Paragraph title = new Paragraph("Certificate of Approval", TITLE_FONT);
            title.setSpacingAfter(2);
            doc.add(title);
            Paragraph subtitle = new Paragraph(tenantName + " — Creative Studio", SUBTITLE_FONT);
            subtitle.setSpacingAfter(24);
            doc.add(subtitle);

            PdfPTable identity = new PdfPTable(2);
            identity.setWidthPercentage(100);
            identity.setWidths(new float[]{1, 2});
            addRow(identity, "Job", job.getTitle());
            addRow(identity, "Client", job.getClientName());
            addRow(identity, "Proof version", "Version " + proof.getVersionNumber()
                    + (proof.getFileName() != null ? " — " + proof.getFileName() : ""));
            identity.setSpacingAfter(20);
            doc.add(identity);

            Paragraph sectionLabel = new Paragraph("APPROVED BY", LABEL_FONT);
            sectionLabel.setSpacingAfter(8);
            doc.add(sectionLabel);

            PdfPTable approval = new PdfPTable(2);
            approval.setWidthPercentage(100);
            approval.setWidths(new float[]{1, 2});
            addRow(approval, "Name", proof.getApprovedByName());
            addRow(approval, "Email", proof.getApprovedByEmail() != null ? proof.getApprovedByEmail() : "—");
            addRow(approval, "Date & time",
                    proof.getApprovedAt() != null
                            ? DATETIME_FMT.withZone(SAST).format(proof.getApprovedAt()) + " SAST"
                            : "—");
            addRow(approval, "IP address", proof.getApprovedByIp() != null ? proof.getApprovedByIp() : "—");
            approval.setSpacingAfter(28);
            doc.add(approval);

            Paragraph legal = new Paragraph();
            legal.add(new Chunk(
                    "This certificate confirms that the above proof was reviewed and approved electronically "
                            + "via a secure, single-use link. The approval was recorded with the approver's name, "
                            + "email address, IP address, and timestamp as shown above, constituting a record of "
                            + "sign-off. This document is generated automatically by HandyFlow and is not itself "
                            + "signed.",
                    new Font(Font.HELVETICA, 8, Font.ITALIC, new Color(148, 163, 184))));
            doc.add(legal);

            doc.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate approval certificate", e);
        }
        return out.toByteArray();
    }

    // ── Job Brief ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] generateJobBrief(TenantId tenantId, UUID jobId) {
        CreJob job = findJob(tenantId, jobId);
        String tenantName = fetchTenantName(tenantId);
        String assignedName = job.getAssignedTo() != null ? fetchUserName(job.getAssignedTo()) : null;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Document doc = new Document(PageSize.A4, 54, 54, 54, 54);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Paragraph title = new Paragraph(job.getTitle(), TITLE_FONT);
            title.setSpacingAfter(2);
            doc.add(title);
            Paragraph subtitle = new Paragraph(tenantName + " — Creative Brief", SUBTITLE_FONT);
            subtitle.setSpacingAfter(20);
            doc.add(subtitle);

            PdfPTable meta = new PdfPTable(4);
            meta.setWidthPercentage(100);
            addSummaryCell(meta, "Client", job.getClientName());
            addSummaryCell(meta, "Job type", job.getJobType() != null ? job.getJobType().replace("_", " ") : "—");
            addSummaryCell(meta, "Priority", job.getPriority());
            addSummaryCell(meta, "Due date", job.getDueDate() != null ? DATE_FMT.format(job.getDueDate()) : "—");
            meta.setSpacingAfter(20);
            doc.add(meta);

            if (job.getBrief() != null && !job.getBrief().isBlank()) {
                addSection(doc, "Brief", job.getBrief());
            }
            if (job.getDescription() != null && !job.getDescription().isBlank()) {
                addSection(doc, "Description", job.getDescription());
            }
            if (assignedName != null) {
                addSection(doc, "Assigned to", assignedName);
            }
            if (job.getBudget() != null || job.getQuotedAmount() != null) {
                String budgetLine = (job.getBudget() != null ? "Budget: R " + job.getBudget() : "")
                        + (job.getBudget() != null && job.getQuotedAmount() != null ? "   " : "")
                        + (job.getQuotedAmount() != null ? "Quoted: R " + job.getQuotedAmount() : "");
                addSection(doc, "Commercials", budgetLine);
            }
            if (job.getNotes() != null && !job.getNotes().isBlank()) {
                addSection(doc, "Notes", job.getNotes());
            }

            doc.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate job brief", e);
        }
        return out.toByteArray();
    }

    // ── Deliverables Manifest ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] generateDeliverablesManifest(TenantId tenantId, UUID jobId) {
        CreJob job = findJob(tenantId, jobId);
        List<CreDeliverable> deliverables = deliverableRepo.findByJobIdOrderByCreatedAtDesc(jobId);
        String tenantName = fetchTenantName(tenantId);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Document doc = new Document(PageSize.A4, 40, 40, 54, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Paragraph title = new Paragraph("Deliverables Manifest", TITLE_FONT);
            title.setSpacingAfter(2);
            doc.add(title);
            Paragraph subtitle = new Paragraph(
                    tenantName + " — " + job.getTitle() + " (" + job.getClientName() + ")", SUBTITLE_FONT);
            subtitle.setSpacingAfter(20);
            doc.add(subtitle);

            if (deliverables.isEmpty()) {
                doc.add(new Paragraph("No deliverables have been uploaded for this job yet.", BODY_FONT));
            } else {
                PdfPTable table = new PdfPTable(4);
                table.setWidthPercentage(100);
                table.setWidths(new float[]{2.5f, 1, 1, 1.3f});
                table.setHeaderRows(1);

                for (String h : List.of("File", "Type", "Size", "Delivered")) {
                    PdfPCell header = new PdfPCell(new Phrase(h, HEADING_FONT));
                    header.setBackgroundColor(BRAND_NAVY);
                    header.setPadding(6);
                    header.setBorderColor(BRAND_NAVY);
                    table.addCell(header);
                }

                boolean shaded = false;
                for (CreDeliverable d : deliverables) {
                    Color bg = shaded ? LIGHT_GREY : Color.WHITE;
                    shaded = !shaded;
                    addBodyCell(table, d.getFileName(), bg);
                    addBodyCell(table, d.getFileType() != null ? d.getFileType() : "—", bg);
                    addBodyCell(table, formatFileSize(d.getFileSize()), bg);
                    addBodyCell(table, d.getCreatedAt() != null
                            ? DATE_FMT.withZone(SAST).format(d.getCreatedAt()) : "—", bg);
                }
                doc.add(table);
            }

            doc.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate deliverables manifest", e);
        }
        return out.toByteArray();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private CreJob findJob(TenantId tenantId, UUID jobId) {
        return jobRepo.findByIdAndTenantId(jobId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Job", jobId.toString()));
    }

    private void addRow(PdfPTable table, String label, String value) {
        PdfPCell l = new PdfPCell(new Phrase(label, LABEL_FONT));
        l.setBorder(Rectangle.NO_BORDER);
        l.setPaddingBottom(6);
        PdfPCell v = new PdfPCell(new Phrase(value != null ? value : "—", BODY_BOLD));
        v.setBorder(Rectangle.NO_BORDER);
        v.setPaddingBottom(6);
        table.addCell(l);
        table.addCell(v);
    }

    private void addSummaryCell(PdfPTable table, String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(LIGHT_GREY);
        cell.setBorderColor(BORDER_GREY);
        cell.setPadding(8);
        Paragraph p = new Paragraph();
        p.add(new Chunk(label.toUpperCase() + "\n", LABEL_FONT));
        p.add(new Chunk(value != null ? value : "—", BODY_BOLD));
        cell.addElement(p);
        table.addCell(cell);
    }

    private void addSection(Document doc, String heading, String body) throws DocumentException {
        Paragraph h = new Paragraph(heading.toUpperCase(), LABEL_FONT);
        h.setSpacingBefore(10);
        h.setSpacingAfter(4);
        doc.add(h);
        Paragraph b = new Paragraph(body, BODY_FONT);
        b.setSpacingAfter(4);
        doc.add(b);
    }

    private void addBodyCell(PdfPTable table, String text, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, BODY_FONT));
        cell.setPadding(6);
        cell.setBackgroundColor(bg);
        cell.setBorderColor(BORDER_GREY);
        table.addCell(cell);
    }

    private String formatFileSize(Long bytes) {
        if (bytes == null) return "—";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private String fetchTenantName(TenantId tenantId) {
        try {
            return jdbc.queryForObject("SELECT name FROM tenants WHERE id = ?",
                    String.class, tenantId.getValue());
        } catch (Exception e) { return "HandyFlow"; }
    }

    private String fetchUserName(UUID userId) {
        if (userId == null) return null;
        try {
            return jdbc.queryForObject(
                    "SELECT TRIM(CONCAT(first_name, ' ', last_name)) FROM users WHERE id = ?",
                    String.class, userId);
        } catch (Exception e) {
            return null;
        }
    }
}