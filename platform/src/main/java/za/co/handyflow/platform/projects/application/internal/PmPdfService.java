package za.co.handyflow.platform.projects.application.internal;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.draw.LineSeparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.domain.model.*;
import za.co.handyflow.platform.projects.domain.repository.*;
import za.co.handyflow.platform.shared.TenantContext;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Generates PDF reports for the Project Management module.
 * Uses OpenPDF (com.github.librepdf:openpdf:1.3.30) — Apache/LGPL licensed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PmPdfService {

    private final ProjectRepository     projectRepo;
    private final ProjectRiskRepository riskRepo;
    private final SiteDiaryRepository  siteDiaryRepo;
    private final SnagItemRepository   snagItemRepo;
    private final ChangeOrderRepository coRepo;

    private static final Color NAVY       = new Color(0x1B, 0x3A, 0x6B);
    private static final Color LIGHT_GREY = new Color(0xF8, 0xFA, 0xFC);
    private static final Color MID_GREY   = new Color(0xE2, 0xE8, 0xF0);
    private static final Color RED_CLR    = new Color(0xDC, 0x26, 0x26);
    private static final Color AMBER_CLR  = new Color(0xD9, 0x77, 0x06);
    private static final Color GREEN_CLR  = new Color(0x16, 0xA3, 0x4A);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Risk Register (OHSA-ready)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] generateRiskRegister(UUID projectId) {
        UUID              tenantId = tenantId();
        Project           project  = getProject(projectId, tenantId);
        List<ProjectRisk> risks    = riskRepo.findByProject(projectId);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 36, 36, 60, 40);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            addPageHeader(writer, project.getName(), "Risk Register");
            doc.open();
            addReportTitle(doc, project, "Risk Register — OHSA Act 85 Compliance");

            long red   = risks.stream().filter(r -> "RED".equals(r.getRating())   && "OPEN".equals(r.getStatus())).count();
            long amber = risks.stream().filter(r -> "AMBER".equals(r.getRating()) && "OPEN".equals(r.getStatus())).count();
            long green = risks.stream().filter(r -> "GREEN".equals(r.getRating()) && "OPEN".equals(r.getStatus())).count();
            addParagraph(doc, String.format("Open Risks:  %d RED  ·  %d AMBER  ·  %d GREEN", red, amber, green),
                    FontFactory.getFont(FontFactory.HELVETICA, 10, RED_CLR));
            doc.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(new float[]{0.7f, 3f, 1.2f, 0.8f, 0.8f, 0.8f, 1f, 3f, 1.5f, 0.8f});
            table.setWidthPercentage(100);
            tableHeader(table, "#", "Title", "Category", "Prob", "Impact", "Score", "Rating", "Mitigation", "Owner", "OHSA");

            for (ProjectRisk r : risks) {
                Color rc    = ratingColour(r.getRating());
                int   score = r.getProbability() * r.getImpact();   // DB GENERATED column not mapped — compute here
                row(table,     r.getRiskNumber() != null ? r.getRiskNumber() : "—");
                row(table,     r.getTitle());
                row(table,     r.getCategory()   != null ? r.getCategory()   : "—");
                row(table,     String.valueOf(r.getProbability()));
                row(table,     String.valueOf(r.getImpact()));
                boldRow(table, String.valueOf(score), rc);
                boldRow(table, r.getRating(), rc);
                row(table,     r.getMitigation() != null ? r.getMitigation() : "—");
                row(table,     r.getOwnerName()  != null ? r.getOwnerName()  : "—");
                row(table,     r.isOhsa() ? "✓" : "");
            }
            doc.add(table);
            addFooter(doc);
        } catch (Exception e) {
            log.error("[PM] Risk register PDF failed project={}: {}", projectId, e.getMessage(), e);
            throw new RuntimeException("PDF generation failed: " + e.getMessage(), e);
        } finally {
            doc.close();
        }
        return baos.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Site Diary
    //    Field mapping from SiteDiary entity:
    //      supervisor → submittedByName   (no separate supervisorName field)
    //      plantCount → not in entity    (omitted)
    //      materialsDelivered → not in entity (omitted)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] generateSiteDiary(UUID projectId, UUID diaryId) {
        UUID      tenantId = tenantId();
        Project   project  = getProject(projectId, tenantId);
        SiteDiary diary    = siteDiaryRepo.findByTenantAndId(tenantId, diaryId)
                .orElseThrow(() -> new IllegalArgumentException("Site diary not found: " + diaryId));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 60, 50);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            addPageHeader(writer, project.getName(), "Site Diary");
            doc.open();
            addReportTitle(doc, project, "Daily Site Diary — " + fmt(diary.getDiaryDate()));

            PdfPTable meta = new PdfPTable(4);
            meta.setWidthPercentage(100);
            meta.setSpacingAfter(12);
            metaCell(meta, "Date",            fmt(diary.getDiaryDate()));
            metaCell(meta, "Submitted By",    nvl(diary.getSubmittedByName()));
            metaCell(meta, "Workers Present", String.valueOf(diary.getWorkersPresent()));
            metaCell(meta, "Workers Planned", diary.getWorkersPlanned() != null ? String.valueOf(diary.getWorkersPlanned()) : "—");
            metaCell(meta, "Weather",         nvl(diary.getWeather()));
            metaCell(meta, "Temperature",     diary.getTempCelsius() != null ? diary.getTempCelsius() + " °C" : "—");
            metaCell(meta, "Generated",       fmt(LocalDate.now()));
            metaCell(meta, "Status",          "Submitted");
            doc.add(meta);

            section(doc, "Work Description",           diary.getWorkDescription());
            section(doc, "Progress Notes",             diary.getProgressNotes());
            section(doc, "Issues / Non-conformances",  diary.getIssues());
            section(doc, "Incidents",                  diary.getIncidents());
            section(doc, "Toolbox Talk Topic",         diary.getToolboxTopic());
            section(doc, "Equipment Notes",            diary.getEquipmentNotes());
            section(doc, "Visitors",                   diary.getVisitorNames());

            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(" "));
            PdfPTable sigs = new PdfPTable(3);
            sigs.setWidthPercentage(80);
            signatureCell(sigs, "Site Supervisor");
            signatureCell(sigs, "Project Manager");
            signatureCell(sigs, "Client Representative");
            doc.add(sigs);

            addFooter(doc);
        } catch (Exception e) {
            log.error("[PM] Site diary PDF failed diaryId={}: {}", diaryId, e.getMessage(), e);
            throw new RuntimeException("PDF generation failed", e);
        } finally {
            doc.close();
        }
        return baos.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Snag List
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] generateSnagList(UUID projectId) {
        UUID           tenantId = tenantId();
        Project        project  = getProject(projectId, tenantId);
        List<SnagItem> snags    = snagItemRepo.findByProject(projectId);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 60, 40);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            addPageHeader(writer, project.getName(), "Snag List");
            doc.open();
            addReportTitle(doc, project, "Snag List — " + fmt(LocalDate.now()));
            addParagraph(doc, snags.size() + " items  ·  " +
                            snags.stream().filter(s -> "OPEN".equals(s.getStatus()) || "IN_PROGRESS".equals(s.getStatus())).count() + " open",
                    FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY));
            doc.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(new float[]{0.7f, 3f, 1f, 1f, 2f, 1.5f, 1.2f});
            table.setWidthPercentage(100);
            tableHeader(table, "#", "Title", "Severity", "Status", "Location", "Assigned To", "Due Date");

            for (SnagItem s : snags) {
                row(table,     s.getSnagNumber() != null ? s.getSnagNumber() : "—");
                row(table,     s.getTitle());
                boldRow(table, s.getSeverity(), severityColour(s.getSeverity()));
                row(table,     s.getStatus());
                row(table,     s.getLocation()       != null ? s.getLocation()       : "—");
                row(table,     s.getAssignedToName() != null ? s.getAssignedToName() : "—");
                row(table,     s.getDueDate()        != null ? fmt(s.getDueDate())   : "—");
            }
            doc.add(table);
            addFooter(doc);
        } catch (Exception e) {
            log.error("[PM] Snag list PDF failed project={}: {}", projectId, e.getMessage(), e);
            throw new RuntimeException("PDF generation failed", e);
        } finally {
            doc.close();
        }
        return baos.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Change Order
    //    Field mapping from ChangeOrder entity:
    //      changeNumber  (not coNumber)
    //      no changeType field  → show status instead
    //      submittedBy is UUID  → no name stored, display "—"
    //      approvedByName       → String getter exists
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] generateChangeOrder(UUID projectId, UUID coId) {
        UUID        tenantId = tenantId();
        Project     project  = getProject(projectId, tenantId);
        ChangeOrder co       = coRepo.findById(coId)
                .filter(c -> tenantId.equals(c.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Change order not found: " + coId));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 50, 50, 70, 50);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            addPageHeader(writer, project.getName(), "Change Order");
            doc.open();
            addReportTitle(doc, project, "Change Order — " + co.getChangeNumber());

            PdfPTable meta = new PdfPTable(2);
            meta.setWidthPercentage(70);
            meta.setHorizontalAlignment(Element.ALIGN_LEFT);
            meta.setSpacingAfter(16);
            metaCell(meta, "CO Number", co.getChangeNumber());
            metaCell(meta, "Status",    co.getStatus());
            metaCell(meta, "Title",     co.getTitle());
            metaCell(meta, "Date",      fmt(LocalDate.now()));
            doc.add(meta);

            section(doc, "Description", co.getDescription());
            section(doc, "Reason",      co.getReason());

            doc.add(new Paragraph(" "));
            PdfPTable impact = new PdfPTable(2);
            impact.setWidthPercentage(50);
            impact.setHorizontalAlignment(Element.ALIGN_LEFT);
            impact.setSpacingAfter(20);
            impactHeader(impact, "Impact Summary");
            impactRow(impact, "Cost Impact",
                    "R " + co.getCostImpact().toPlainString(),
                    co.getCostImpact().signum() > 0 ? RED_CLR : GREEN_CLR);
            impactRow(impact, "Schedule Impact",
                    co.getScheduleImpact() + " day(s)",
                    co.getScheduleImpact() > 0 ? AMBER_CLR : GREEN_CLR);
            doc.add(impact);

            if ("APPROVED".equals(co.getStatus())) {
                section(doc, "Approved By", co.getApprovedByName() != null ? co.getApprovedByName() : "—");
            }

            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(" "));
            PdfPTable sigs = new PdfPTable(3);
            sigs.setWidthPercentage(90);
            signatureCell(sigs, "Prepared By");
            signatureCell(sigs, "Project Manager");
            signatureCell(sigs, "APPROVED".equals(co.getStatus()) ? "Approved By ✓" : "Approved By");
            doc.add(sigs);

            addFooter(doc);
        } catch (Exception e) {
            log.error("[PM] Change order PDF failed coId={}: {}", coId, e.getMessage(), e);
            throw new RuntimeException("PDF generation failed", e);
        } finally {
            doc.close();
        }
        return baos.toByteArray();
    }

    // ─── PDF helpers ──────────────────────────────────────────────────────────

    private void addPageHeader(PdfWriter writer, String projectName, String reportType) {
        writer.setPageEvent(new PdfPageEventHelper() {
            @Override
            public void onEndPage(PdfWriter w, Document d) {
                PdfContentByte cb = w.getDirectContent();
                cb.setColorFill(NAVY);
                cb.rectangle(d.left(), d.top() + 10, d.right() - d.left(), 24);
                cb.fill();
                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                        new Phrase(projectName, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)),
                        d.left() + 8, d.top() + 19, 0);
                ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                        new Phrase(reportType + "  ·  Page " + w.getPageNumber(),
                                FontFactory.getFont(FontFactory.HELVETICA, 8, Color.WHITE)),
                        d.right() - 8, d.top() + 19, 0);
            }
        });
    }

    private void addReportTitle(Document doc, Project project, String title) throws DocumentException {
        Paragraph heading = new Paragraph(title,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, NAVY));
        heading.setSpacingBefore(8);
        heading.setSpacingAfter(4);
        doc.add(heading);

        String sub = project.getProjectNumber();
        if (project.getClientName()  != null) sub += "  ·  " + project.getClientName();
        if (project.getSiteAddress() != null) sub += "  ·  " + project.getSiteAddress();
        Paragraph subPara = new Paragraph(sub,
                FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY));
        subPara.setSpacingAfter(12);
        doc.add(subPara);

        doc.add(new Chunk(new LineSeparator(1f, 100f, MID_GREY, Element.ALIGN_CENTER, -2f)));
        doc.add(new Paragraph(" "));
    }

    private void addFooter(Document doc) throws DocumentException {
        doc.add(new Paragraph(" "));
        doc.add(new Chunk(new LineSeparator(0.5f, 100f, MID_GREY, Element.ALIGN_CENTER, -2f)));
        Paragraph fp = new Paragraph(
                "Generated by HandyFlow on " + fmt(LocalDate.now()) +
                        "  ·  This document is computer-generated and valid without a signature unless stated.",
                FontFactory.getFont(FontFactory.HELVETICA, 8, Color.LIGHT_GRAY));
        fp.setSpacingBefore(4);
        doc.add(fp);
    }

    private void tableHeader(PdfPTable table, String... headers) {
        Font hf = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, hf));
            cell.setBackgroundColor(NAVY);
            cell.setPadding(6);
            cell.setBorderColor(NAVY);
            table.addCell(cell);
        }
    }

    private void row(PdfPTable t, String val) {
        PdfPCell cell = new PdfPCell(new Phrase(
                val != null ? val : "—",
                FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY)));
        cell.setPadding(5);
        cell.setBorderColor(MID_GREY);
        t.addCell(cell);
    }

    private void boldRow(PdfPTable t, String val, Color color) {
        PdfPCell cell = new PdfPCell(new Phrase(
                val != null ? val : "—",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, color)));
        cell.setPadding(5);
        cell.setBorderColor(MID_GREY);
        t.addCell(cell);
    }

    private void metaCell(PdfPTable t, String key, String value) {
        PdfPCell kc = new PdfPCell(new Phrase(key.toUpperCase(),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.GRAY)));
        kc.setBackgroundColor(LIGHT_GREY); kc.setPadding(5); kc.setBorderColor(MID_GREY);
        PdfPCell vc = new PdfPCell(new Phrase(value != null ? value : "—",
                FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY)));
        vc.setPadding(5); vc.setBorderColor(MID_GREY);
        t.addCell(kc); t.addCell(vc);
    }

    private void section(Document doc, String heading, String content) throws DocumentException {
        if (content == null || content.isBlank()) return;
        Paragraph h = new Paragraph(heading,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, NAVY));
        h.setSpacingBefore(12); h.setSpacingAfter(4);
        Paragraph b = new Paragraph(content,
                FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY));
        b.setSpacingAfter(6); b.setLeading(14);
        doc.add(h); doc.add(b);
    }

    private void signatureCell(PdfPTable t, String label) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
        PdfPCell c = new PdfPCell();
        c.setPadding(8); c.setBorderColor(MID_GREY);
        c.addElement(new Paragraph("\n\n\n", f));
        c.addElement(new Paragraph("_________________________", f));
        c.addElement(new Paragraph(label, f));
        c.addElement(new Paragraph("Date: ___________________", f));
        t.addCell(c);
    }

    private void impactHeader(PdfPTable t, String title) {
        PdfPCell c = new PdfPCell(new Phrase(title,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
        c.setColspan(2); c.setBackgroundColor(NAVY); c.setPadding(6); c.setBorderColor(NAVY);
        t.addCell(c);
    }

    private void impactRow(PdfPTable t, String key, String value, Color valColor) {
        PdfPCell kc = new PdfPCell(new Phrase(key,
                FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY)));
        kc.setPadding(5); kc.setBorderColor(MID_GREY);
        PdfPCell vc = new PdfPCell(new Phrase(value,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, valColor)));
        vc.setPadding(5); vc.setBorderColor(MID_GREY);
        t.addCell(kc); t.addCell(vc);
    }

    private void addParagraph(Document doc, String text, Font font) throws DocumentException {
        doc.add(new Paragraph(text, font));
    }

    private Project getProject(UUID projectId, UUID tenantId) {
        return projectRepo.findById(projectId)
                .filter(p -> tenantId.equals(p.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
    }

    /** Parse TenantContext string to UUID — consistent with how other PM services do it. */
    private static UUID tenantId() {
        return UUID.fromString(TenantContext.getTenantId());
    }

    private static Color ratingColour(String rating) {
        return switch (rating != null ? rating : "") {
            case "RED"   -> RED_CLR;
            case "AMBER" -> AMBER_CLR;
            default      -> GREEN_CLR;
        };
    }

    private static Color severityColour(String severity) {
        return switch (severity != null ? severity : "") {
            case "CRITICAL", "HIGH" -> RED_CLR;
            case "MEDIUM"           -> AMBER_CLR;
            default                 -> GREEN_CLR;
        };
    }

    private static String fmt(LocalDate d) { return d != null ? d.format(DATE_FMT) : "—"; }
    private static String nvl(String s)    { return s != null && !s.isBlank() ? s : "—"; }
}
