package za.co.handyflow.platform.complianceservices.application.internal;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.font.PdfFontFactory.EmbeddingStrategy;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTender;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTenderPersonnel;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTenderRequirement;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceClient;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientTenderPersonnelRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientTenderRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientTenderRequirementRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceClientRepository;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.identity.TenantDetails;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Client-scoped counterpart to compliancetender.TenderPdfService — same
 * iText 7 conventions from .claude/skills/pdf-generation/SKILL.md, same
 * live-state-not-snapshot generation reasoning. One real addition this
 * version needs that the tenant-scoped one doesn't: the document is
 * branded as the SERVICE PROVIDER's own (tenant logo/name, exactly like
 * the tenant-scoped version), but the header also names which CLIENT
 * it's prepared for — without that, a service provider managing many
 * clients would have no way to tell one client's exported tender summary
 * from another's just by looking at the document itself.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientTenderPdfService {

    private static final DeviceRgb NAVY       = new DeviceRgb(0x1B, 0x3A, 0x6B);
    private static final DeviceRgb TEAL       = new DeviceRgb(0x0D, 0x94, 0x88);
    private static final DeviceRgb LIGHT_GRAY = new DeviceRgb(0xF8, 0xFA, 0xFC);
    private static final DeviceRgb MID_GRAY   = new DeviceRgb(0xE2, 0xE8, 0xF0);
    private static final DeviceRgb TEXT_GRAY  = new DeviceRgb(0x64, 0x74, 0x8B);
    private static final DeviceRgb TEXT_DARK  = new DeviceRgb(0x0F, 0x17, 0x2A);
    private static final DeviceRgb GREEN      = new DeviceRgb(0x16, 0xA3, 0x4A);
    private static final DeviceRgb AMBER      = new DeviceRgb(0xD9, 0x77, 0x06);
    private static final DeviceRgb RED        = new DeviceRgb(0xDC, 0x26, 0x26);

    private final ClientTenderRepository tenderRepository;
    private final ClientTenderRequirementRepository requirementRepository;
    private final ClientTenderPersonnelRepository personnelRepository;
    private final ComplianceClientRepository clientRepository;
    private final TenantFacade tenantFacade;
    private final HrFacade hrFacade;

    @Transactional(readOnly = true)
    public byte[] generateTenderSummaryPdf(TenantId tenantId, UUID clientTenderId) {
        ClientTender tender = tenderRepository.findByIdForTenant(tenantId, clientTenderId)
                .orElseThrow(() -> new ResourceNotFoundException("ClientTender", clientTenderId.toString()));
        ComplianceClient client = clientRepository.findByIdForTenant(tenantId, tender.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException("ComplianceClient", tender.getClientId().toString()));
        List<ClientTenderRequirement> requirements = requirementRepository.findByTender(tenantId, clientTenderId);
        List<ClientTenderPersonnel> personnel = personnelRepository.findByTender(tenantId, clientTenderId);
        TenantDetails tenant = tenantFacade.findTenantDetails(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId.toString()));

        try {
            PdfFont regular = loadFont("/fonts/LiberationSans-Regular.ttf");
            PdfFont bold = loadFont("/fonts/LiberationSans-Bold.ttf");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document doc = new Document(new PdfDocument(new PdfWriter(baos)), PageSize.A4);
            doc.setMargins(0, 36, 40, 36);

            addHeader(doc, tender, client, tenant, regular, bold);
            addTenderDetails(doc, tender, regular, bold);
            addRequirementMatrix(doc, requirements, regular, bold);
            addPersonnel(doc, tenantId, personnel, regular, bold);
            addFooter(doc, client, regular);

            doc.close();
            log.info("Client tender summary PDF generated tender={} client={} requirements={} personnel={} tenant={}",
                    clientTenderId, tender.getClientId(), requirements.size(), personnel.size(), tenantId);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate client tender summary PDF: " + e.getMessage(), e);
        }
    }

    private PdfFont loadFont(String classpathPath) throws Exception {
        return PdfFontFactory.createFont(
                Objects.requireNonNull(getClass().getResourceAsStream(classpathPath)).readAllBytes(),
                PdfEncodings.WINANSI, EmbeddingStrategy.FORCE_EMBEDDED);
    }

    private void addHeader(Document doc, ClientTender tender, ComplianceClient client, TenantDetails tenant,
                           PdfFont regular, PdfFont bold) {
        Table bar = new Table(new float[]{1})
                .setWidth(UnitValue.createPercentValue(100))
                .setBackgroundColor(NAVY)
                .setMarginBottom(0);
        bar.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(0).setHeight(6));
        doc.add(bar);

        Table headerRow = new Table(new float[]{1, 1}).setWidth(UnitValue.createPercentValue(100)).setMarginTop(20);

        Cell left = new Cell().setBorder(Border.NO_BORDER).setPaddingLeft(0);
        Image logo = loadLogo(tenant);
        if (logo != null) {
            left.add(logo);
        } else {
            left.add(new Paragraph(tenant.companyName() != null ? tenant.companyName() : "")
                    .setFont(bold).setFontSize(16).setFontColor(NAVY));
        }
        left.add(new Paragraph("Prepared for: " + client.getName())
                .setFont(regular).setFontSize(10).setFontColor(TEXT_GRAY).setMarginTop(4));
        headerRow.addCell(left);

        Cell right = new Cell().setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT);
        right.add(new Paragraph("TENDER SUMMARY").setFont(bold).setFontSize(20).setFontColor(TEAL));
        right.add(new Paragraph(tender.getTenderNumber()).setFont(bold).setFontSize(12).setFontColor(TEXT_DARK));
        right.add(new Paragraph(statusLabel(tender.getStatus()))
                .setFont(bold).setFontSize(10).setFontColor(statusColor(tender.getStatus())));
        headerRow.addCell(right);

        doc.add(headerRow);
        addFullWidthLine(doc);
    }

    private Image loadLogo(TenantDetails tenant) {
        if (tenant.logoUrl() == null || tenant.logoUrl().isBlank()) return null;
        try {
            return new Image(ImageDataFactory.create(decodeLogoBytes(tenant.logoUrl())))
                    .setMaxHeight(36).setAutoScale(false);
        } catch (Exception ex) {
            log.warn("Could not load tenant logo tenant={}: {}", tenant.slug(), ex.getMessage());
            return null;
        }
    }

    private byte[] decodeLogoBytes(String logoUrl) throws Exception {
        if (logoUrl.startsWith("data:")) {
            int commaIdx = logoUrl.indexOf(',');
            if (commaIdx < 0) throw new IllegalArgumentException("Malformed data URI");
            return java.util.Base64.getDecoder().decode(logoUrl.substring(commaIdx + 1));
        }
        try (var in = new URL(logoUrl).openStream()) {
            return in.readAllBytes();
        }
    }

    private void addTenderDetails(Document doc, ClientTender tender, PdfFont regular, PdfFont bold) {
        doc.add(new Paragraph(tender.getName()).setFont(bold).setFontSize(15).setFontColor(TEXT_DARK).setMarginTop(20));

        Table details = new Table(new float[]{1, 1}).setWidth(UnitValue.createPercentValue(100)).setMarginTop(10);
        details.addCell(detailCell("Tender Authority", nvl(tender.getTenderAuthority()), regular, bold));
        details.addCell(detailCell("Authority Reference", nvl(tender.getAuthorityReferenceNumber()), regular, bold));
        details.addCell(detailCell("Closing Date", formatDate(tender.getClosingDate()), regular, bold));
        details.addCell(detailCell("Briefing Date", formatDate(tender.getBriefingDate()), regular, bold));
        details.addCell(detailCell("Estimated Value", formatZar(tender.getEstimatedValue()), regular, bold));
        details.addCell(detailCell("Required Class of Work", nvl(tender.getRequiredClassOfWork()), regular, bold));
        doc.add(details);
    }

    private Cell detailCell(String label, String value, PdfFont regular, PdfFont bold) {
        Cell cell = new Cell().setBorder(Border.NO_BORDER).setPaddingBottom(10);
        cell.add(new Paragraph(label.toUpperCase())
                .setFont(bold).setFontSize(8).setFontColor(TEXT_GRAY).setCharacterSpacing(0.5f).setMarginBottom(2));
        cell.add(new Paragraph(value).setFont(regular).setFontSize(11).setFontColor(TEXT_DARK));
        return cell;
    }

    private void addRequirementMatrix(Document doc, List<ClientTenderRequirement> requirements, PdfFont regular, PdfFont bold) {
        doc.add(new Paragraph("Requirement Matrix").setFont(bold).setFontSize(13).setFontColor(NAVY).setMarginTop(24));

        if (requirements.isEmpty()) {
            doc.add(new Paragraph("No requirements captured yet.").setFont(regular).setFontSize(10).setFontColor(TEXT_GRAY));
            return;
        }

        Table table = new Table(new float[]{3, 1, 1}).setWidth(UnitValue.createPercentValue(100)).setMarginTop(8);
        table.addHeaderCell(headerCell("Requirement", bold));
        table.addHeaderCell(headerCell("Source", bold));
        table.addHeaderCell(headerCell("Status", bold));

        for (ClientTenderRequirement r : requirements) {
            table.addCell(bodyCell(r.getDescription(), regular));
            table.addCell(bodyCell(r.getSource(), regular));
            Cell statusCell = new Cell().setBorder(new SolidBorder(MID_GRAY, 0.5f)).setPadding(8);
            statusCell.add(new Paragraph(r.getStatus()).setFont(bold).setFontSize(9).setFontColor(requirementStatusColor(r.getStatus())));
            table.addCell(statusCell);
        }
        doc.add(table);
    }

    private void addPersonnel(Document doc, TenantId tenantId, List<ClientTenderPersonnel> personnel, PdfFont regular, PdfFont bold) {
        doc.add(new Paragraph("Key Personnel").setFont(bold).setFontSize(13).setFontColor(NAVY).setMarginTop(24));

        if (personnel.isEmpty()) {
            doc.add(new Paragraph("No personnel added yet.").setFont(regular).setFontSize(10).setFontColor(TEXT_GRAY));
            return;
        }

        Table table = new Table(new float[]{2, 1, 1}).setWidth(UnitValue.createPercentValue(100)).setMarginTop(8);
        table.addHeaderCell(headerCell("Name", bold));
        table.addHeaderCell(headerCell("Employee No.", bold));
        table.addHeaderCell(headerCell("Role on Tender", bold));

        for (ClientTenderPersonnel p : personnel) {
            var employee = hrFacade.findEmployeeById(tenantId, p.getEmployeeId()).orElse(null);
            table.addCell(bodyCell(employee != null ? employee.fullName() : "(employee record no longer available)", regular));
            table.addCell(bodyCell(employee != null ? employee.employeeNumber() : "—", regular));
            table.addCell(bodyCell(p.getRole(), regular));
        }
        doc.add(table);
    }

    private Cell headerCell(String text, PdfFont bold) {
        return new Cell().setBackgroundColor(LIGHT_GRAY).setBorder(new SolidBorder(MID_GRAY, 0.5f)).setPadding(8)
                .add(new Paragraph(text.toUpperCase()).setFont(bold).setFontSize(8).setFontColor(TEXT_GRAY).setCharacterSpacing(0.3f));
    }

    private Cell bodyCell(String text, PdfFont regular) {
        return new Cell().setBorder(new SolidBorder(MID_GRAY, 0.5f)).setPadding(8)
                .add(new Paragraph(nvl(text)).setFont(regular).setFontSize(10).setFontColor(TEXT_DARK));
    }

    private void addFooter(Document doc, ComplianceClient client, PdfFont regular) {
        doc.add(new Paragraph().setMarginTop(24));
        addFullWidthLine(doc);
        doc.add(new Paragraph(
                "This is a working document reflecting the tender's current status at the time of generation, "
                        + "prepared on behalf of " + client.getName() + ". It is a document/compliance readiness "
                        + "indicator, not a legal guarantee of tender eligibility.")
                .setFont(regular).setFontSize(8).setFontColor(TEXT_GRAY).setMarginTop(8));
    }

    private void addFullWidthLine(Document doc) {
        Table line = new Table(new float[]{1}).setWidth(UnitValue.createPercentValue(100));
        line.addCell(new Cell().setBorder(Border.NO_BORDER).setBorderBottom(new SolidBorder(MID_GRAY, 1)).setPadding(0));
        doc.add(line);
    }

    private String statusLabel(String status) {
        return status.replace('_', ' ');
    }

    private DeviceRgb statusColor(String status) {
        return switch (status) {
            case "AWARDED" -> GREEN;
            case "UNSUCCESSFUL", "WITHDRAWN" -> RED;
            case "SUBMITTED", "CLARIFICATION", "SHORTLISTED", "NEGOTIATION" -> TEAL;
            default -> AMBER;
        };
    }

    private DeviceRgb requirementStatusColor(String status) {
        return switch (status) {
            case "MET" -> GREEN;
            case "MISSING" -> RED;
            case "NOT_APPLICABLE" -> TEXT_GRAY;
            default -> AMBER;
        };
    }

    private String nvl(String s) { return s != null && !s.isBlank() ? s : "—"; }

    private String formatDate(java.time.LocalDate date) {
        return date != null ? date.format(DateTimeFormatter.ofPattern("d MMMM yyyy")) : "—";
    }

    private String formatZar(BigDecimal value) {
        if (value == null) return "—";
        return "R " + String.format(java.util.Locale.US, "%,.2f", value.setScale(2, RoundingMode.HALF_UP));
    }
}
