package za.co.handyflow.platform.clinic.application.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.IBlockElement;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.clinic.domain.model.ClinicLabResult;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatient;
import za.co.handyflow.platform.clinic.domain.repository.ClinicLabResultRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPatientRepository;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * FIX: "no lab result summary PDF" gap — distinct from the raw uploaded lab
 * PDF (a static file, download-only), this renders the *parsed/interpreted*
 * data as a clean formatted document for the patient's own records.
 * <p>
 * ClinicLabResult.parsedMarkersJson is a real jsonb column, but nothing in
 * this codebase currently populates it — no OCR/parsing pipeline exists yet
 * (confirmed in an earlier audit of this module). This gracefully degrades
 * when it's empty: shows the interpretation and lab metadata, and says
 * plainly that no structured marker data is available, rather than
 * rendering a broken or misleadingly-empty table.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicLabSummaryPdfService {

    private final ClinicLabResultRepository labResultRepo;
    private final ClinicPatientRepository patientRepo;
    private final TenantFacade tenantFacade;
    private final ObjectMapper objectMapper;

    private static final DeviceRgb BRAND_DARK = new DeviceRgb(15, 76, 92);
    private static final DeviceRgb TEAL       = new DeviceRgb(13, 148, 136);
    private static final DeviceRgb RED        = new DeviceRgb(180, 60, 50);
    private static final DeviceRgb TEXT_MUTED = new DeviceRgb(120, 130, 140);
    private static final DeviceRgb WHITE      = new DeviceRgb(255, 255, 255);
    private static final DeviceRgb LIGHT_BG   = new DeviceRgb(240, 250, 246);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    public byte[] generate(TenantId tenantId, UUID resultId) {
        ClinicLabResult result = labResultRepo.findByIdAndTenant(tenantId, resultId)
                .orElseThrow(() -> new ResourceNotFoundException("LabResult", resultId.toString()));

        ClinicPatient patient = result.getPatientId() != null
                ? patientRepo.findActiveById(tenantId, result.getPatientId()).orElse(null)
                : null;
        String companyName = tenantFacade.findTenantDetails(tenantId).map(t -> t.companyName()).orElse("");
        String logoUrl = tenantFacade.findTenantDetails(tenantId).map(t -> t.logoUrl()).orElse(null);

        List<Map<String, Object>> markers = parseMarkers(result.getParsedMarkersJson(), resultId);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos));
            Document document = new Document(pdfDoc, PageSize.A4);
            document.setMargins(36, 40, 36, 40);

            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

            addHeader(document, companyName, logoUrl, bold, regular);
            addResultInfo(document, result, patient, bold, regular);
            addMarkersTable(document, markers, bold, regular);
            addInterpretation(document, result, bold, regular);
            addFooter(document, regular);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate lab summary PDF for result={}: {}", resultId, e.getMessage(), e);
            throw new RuntimeException("Lab summary PDF generation failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseMarkers(String json, UUID resultId) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            log.warn("Could not parse markers JSON for lab result={}: {}", resultId, e.getMessage());
            return List.of();
        }
    }

    private void addHeader(Document doc, String companyName, String logoUrl, PdfFont bold, PdfFont regular) {
        Table header = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth();
        Table left = new Table(1).useAllAvailableWidth();
        boolean logoAdded = false;
        if (logoUrl != null && !logoUrl.isBlank()) {
            try {
                byte[] imageBytes = decodeLogoBytes(logoUrl);
                Image logo = new Image(com.itextpdf.io.image.ImageDataFactory.create(imageBytes))
                        .setMaxHeight(50).setAutoScale(false);
                left.addCell(cellOf(logo));
                logoAdded = true;
            } catch (Exception ex) {
                log.warn("Could not load logo for lab summary: {}", ex.getMessage());
            }
        }
        if (!logoAdded) {
            left.addCell(cellOf(new Paragraph(companyName).setFont(bold).setFontSize(14)));
        } else {
            left.addCell(cellOf(new Paragraph(companyName).setFont(bold).setFontSize(10).setFontColor(TEXT_MUTED).setMarginTop(4)));
        }
        header.addCell(cellOf(left));
        header.addCell(cellOf(new Paragraph("LAB RESULT SUMMARY").setFont(bold).setFontSize(16)
                .setFontColor(TEAL).setTextAlignment(TextAlignment.RIGHT)));
        doc.add(header);
        doc.add(new LineSeparator(new SolidLine(1f)).setMarginTop(8).setMarginBottom(14).setFontColor(TEAL));
    }

    private void addResultInfo(Document doc, ClinicLabResult result, ClinicPatient patient, PdfFont bold, PdfFont regular) {
        Table info = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth().setMarginBottom(16);
        info.addCell(cellOf(new Paragraph("Patient: " + (patient != null ? patient.getFullName()
                : (result.getPatientNameRaw() != null ? result.getPatientNameRaw() + " (unmatched)" : "—")))
                .setFont(regular).setFontSize(10)));
        info.addCell(cellOf(new Paragraph("Collected: " + (result.getCollectedAt() != null
                ? result.getCollectedAt().atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FMT) : "—"))
                .setFont(regular).setFontSize(10).setTextAlignment(TextAlignment.RIGHT)));
        info.addCell(cellOf(new Paragraph("Reference: " + (result.getLabReference() != null ? result.getLabReference() : "—"))
                .setFont(regular).setFontSize(10)));
        info.addCell(cellOf(new Paragraph("Source: " + (result.getSource() != null ? result.getSource() : "—"))
                .setFont(regular).setFontSize(10).setTextAlignment(TextAlignment.RIGHT)));
        doc.add(info);
    }

    private void addMarkersTable(Document doc, List<Map<String, Object>> markers, PdfFont bold, PdfFont regular) {
        doc.add(new Paragraph("RESULTS").setFont(bold).setFontSize(10).setFontColor(TEXT_MUTED).setMarginBottom(6));

        if (markers.isEmpty()) {
            Table box = new Table(1).useAllAvailableWidth().setBackgroundColor(LIGHT_BG).setPadding(12).setMarginBottom(16);
            box.addCell(cellOf(new Paragraph("No structured marker data is available for this result — see the uploaded document and interpretation below.")
                    .setFont(regular).setFontSize(9).setFontColor(TEXT_MUTED)));
            doc.add(box);
            return;
        }

        Table table = new Table(UnitValue.createPercentArray(new float[]{2, 1, 1, 1.5f, 0.8f})).useAllAvailableWidth().setMarginBottom(16);
        for (String h : new String[]{"Marker", "Value", "Unit", "Reference range", "Flag"}) {
            table.addHeaderCell(headerCell(h, bold));
        }
        for (Map<String, Object> m : markers) {
            String flag = str(m.get("flag"));
            boolean abnormal = flag != null && !flag.isBlank() && !"N".equalsIgnoreCase(flag) && !"NORMAL".equalsIgnoreCase(flag);
            table.addCell(cellOf(new Paragraph(str(m.get("marker"))).setFont(regular).setFontSize(9)));
            table.addCell(cellOf(new Paragraph(str(m.get("value"))).setFont(bold).setFontSize(9)
                    .setFontColor(abnormal ? RED : BRAND_DARK)));
            table.addCell(cellOf(new Paragraph(str(m.get("unit"))).setFont(regular).setFontSize(9)));
            table.addCell(cellOf(new Paragraph(str(m.get("refRange"))).setFont(regular).setFontSize(9)));
            table.addCell(cellOf(new Paragraph(flag != null ? flag : "—").setFont(bold).setFontSize(9)
                    .setFontColor(abnormal ? RED : TEXT_MUTED)));
        }
        doc.add(table);
    }

    private void addInterpretation(Document doc, ClinicLabResult result, PdfFont bold, PdfFont regular) {
        if (result.getInterpretation() == null || result.getInterpretation().isBlank()) return;
        doc.add(new Paragraph("INTERPRETATION").setFont(bold).setFontSize(10).setFontColor(TEXT_MUTED).setMarginBottom(4));
        doc.add(new Paragraph(result.getInterpretation()).setFont(regular).setFontSize(10).setMarginBottom(14));
    }

    private void addFooter(Document doc, PdfFont regular) {
        doc.add(new Paragraph("This summary is provided for the patient's own records. Always discuss results with your treating practitioner before acting on them.")
                .setFont(regular).setFontSize(8).setFontColor(TEXT_MUTED).setTextAlignment(TextAlignment.CENTER).setMarginTop(24));
    }

    private String str(Object o) {
        return o != null ? o.toString() : "—";
    }

    private Cell cellOf(IBlockElement content) {
        Cell c = new Cell();
        c.add(content);
        c.setBorder(Border.NO_BORDER);
        return c;
    }

    /** FIX: build failure — Image implements ILeafElement, not IBlockElement, in iText7. See ClinicPatientInvoicePdfService for the full explanation. */
    private Cell cellOf(Image image) {
        Cell c = new Cell();
        c.add(image);
        c.setBorder(Border.NO_BORDER);
        return c;
    }

    private Cell headerCell(String text, PdfFont bold) {
        Cell c = new Cell();
        c.add(new Paragraph(text).setFont(bold).setFontSize(8).setFontColor(WHITE));
        c.setBackgroundColor(BRAND_DARK);
        c.setBorder(Border.NO_BORDER);
        return c;
    }

    /** Same fix as InvoicePdfService/QuotePdfService/ReceiptPdfService/CreditNotePdfService — logoUrl is a data: URI. */
    private byte[] decodeLogoBytes(String logoUrl) throws Exception {
        if (logoUrl.startsWith("data:")) {
            int commaIdx = logoUrl.indexOf(',');
            if (commaIdx < 0) throw new IllegalArgumentException("Malformed data URI");
            return java.util.Base64.getDecoder().decode(logoUrl.substring(commaIdx + 1));
        }
        try (var in = new java.net.URL(logoUrl).openStream()) {
            return in.readAllBytes();
        }
    }
}