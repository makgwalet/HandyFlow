package za.co.handyflow.platform.clinic.application.internal;

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
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.TenantId;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/** Its own document — same rationale as every other Pdf service in this codebase: self-contained, no coupling. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicStatementOfAccountPdfService {

    private final ClinicStatementOfAccountService statementService;
    private final TenantFacade tenantFacade;
    private final EmailService emailService;

    private static final DeviceRgb BRAND_DARK = new DeviceRgb(15, 76, 92);
    private static final DeviceRgb TEAL       = new DeviceRgb(13, 148, 136);
    private static final DeviceRgb TEXT_MUTED = new DeviceRgb(120, 130, 140);
    private static final DeviceRgb BG_LIGHT   = new DeviceRgb(240, 250, 246);
    private static final DeviceRgb WHITE      = new DeviceRgb(255, 255, 255);
    private static final DateTimeFormatter DATE_FMT  = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_FMT = DateTimeFormatter.ofPattern("d MMM yy", Locale.ENGLISH);

    /**
     * FIX: "no PDF is ever emailed" gap — but statements are a rolled-up,
     * on-demand view, not tied to any single event, so unlike the visit
     * summary or lab result there's no sensible "automatic" trigger to
     * hang this off. This is a manual, staff-triggered send instead of an
     * automatic one — reuses generate() rather than duplicating the
     * render logic, so the emailed PDF is guaranteed identical to the
     * download.
     */
    public void emailStatement(TenantId tenantId, UUID patientId, LocalDate from, LocalDate to) {
        var statement = statementService.buildStatement(tenantId, patientId, from, to);
        if (statement.patientEmail() == null || statement.patientEmail().isBlank()) {
            throw new IllegalStateException("This patient has no email on file — statement not sent");
        }
        byte[] pdfBytes = generate(tenantId, patientId, from, to);
        String greetingName = statement.patientName() != null ? statement.patientName().split(" ")[0] : "there";
        String html = "<p>Dear " + greetingName + ",</p>"
                + "<p>Please find your statement of account attached.</p>"
                + "<p>If you have any questions, please contact the practice.</p>";
        emailService.sendWithAttachment(statement.patientEmail(), "Your statement of account", html,
                "statement-" + patientId + ".pdf", pdfBytes);
        log.info("Emailed statement of account patient={}", patientId);
    }

    public byte[] generate(TenantId tenantId, UUID patientId, LocalDate from, LocalDate to) {
        var statement = statementService.buildStatement(tenantId, patientId, from, to);
        String companyName = tenantFacade.findTenantDetails(tenantId).map(t -> t.companyName()).orElse("");
        String logoUrl = tenantFacade.findTenantDetails(tenantId).map(t -> t.logoUrl()).orElse(null);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos));
            Document document = new Document(pdfDoc, PageSize.A4);
            document.setMargins(36, 40, 36, 40);

            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

            addHeader(document, companyName, logoUrl, statement, bold, regular);
            addClaimsTable(document, statement, bold, regular);
            addPaymentsTable(document, statement, bold, regular);
            addFooter(document, regular);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate patient statement PDF for patient={}: {}", patientId, e.getMessage(), e);
            throw new RuntimeException("Patient statement PDF generation failed", e);
        }
    }

    private void addHeader(Document doc, String companyName, String logoUrl, ClinicStatementOfAccountService.PatientStatement s,
                           PdfFont bold, PdfFont regular) {
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
                log.warn("Could not load logo for patient statement: {}", ex.getMessage());
            }
        }
        if (!logoAdded) {
            left.addCell(cellOf(new Paragraph(companyName).setFont(bold).setFontSize(14)));
        } else {
            left.addCell(cellOf(new Paragraph(companyName).setFont(bold).setFontSize(10).setFontColor(TEXT_MUTED).setMarginTop(4)));
        }
        header.addCell(cellOf(left));
        header.addCell(cellOf(new Paragraph("STATEMENT OF ACCOUNT").setFont(bold).setFontSize(18)
                .setFontColor(TEAL).setTextAlignment(TextAlignment.RIGHT)));
        doc.add(header);
        doc.add(new LineSeparator(new SolidLine(1f)).setMarginTop(8).setMarginBottom(14).setFontColor(TEAL));

        Table info = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth().setMarginBottom(16);
        info.addCell(cellOf(new Paragraph("Patient: " + s.patientName()).setFont(regular).setFontSize(10)));
        info.addCell(cellOf(new Paragraph("Generated: " + LocalDate.now().format(DATE_FMT))
                .setFont(regular).setFontSize(10).setTextAlignment(TextAlignment.RIGHT)));
        String periodStr = (s.periodFrom() != null ? s.periodFrom().format(SHORT_FMT) : "All time")
                + " – " + (s.periodTo() != null ? s.periodTo().format(SHORT_FMT) : "present");
        info.addCell(cellOf(new Paragraph("Period: " + periodStr).setFont(regular).setFontSize(10)));
        info.addCell(cellOf(new Paragraph(s.patientPhone() != null ? s.patientPhone() : "")
                .setFont(regular).setFontSize(10).setFontColor(TEXT_MUTED).setTextAlignment(TextAlignment.RIGHT)));
        doc.add(info);

        Table amountBox = new Table(1).useAllAvailableWidth().setBackgroundColor(BG_LIGHT).setPadding(14).setMarginBottom(16);
        amountBox.addCell(cellOf(new Paragraph("BALANCE OWING").setFont(bold).setFontSize(9).setFontColor(TEAL)));
        amountBox.addCell(cellOf(new Paragraph(zar(s.balance())).setFont(bold).setFontSize(24).setFontColor(BRAND_DARK).setMarginTop(2)));
        doc.add(amountBox);
    }

    private void addClaimsTable(Document doc, ClinicStatementOfAccountService.PatientStatement s, PdfFont bold, PdfFont regular) {
        doc.add(new Paragraph("CLAIMS / VISITS").setFont(bold).setFontSize(10).setFontColor(TEXT_MUTED).setMarginBottom(6));
        Table table = new Table(UnitValue.createPercentArray(new float[]{1.2f, 2, 1, 1, 1, 1.2f})).useAllAvailableWidth().setMarginBottom(16);
        for (String h : new String[]{"Date", "Scheme", "Gross", "Scheme paid", "Patient portion", "Status"}) {
            table.addHeaderCell(headerCell(h, bold));
        }
        if (s.claims().isEmpty()) {
            Cell empty = new Cell(1, 6).setBorder(Border.NO_BORDER).setPadding(10);
            empty.add(new Paragraph("No claims in this period.").setFont(regular).setFontSize(9).setFontColor(TEXT_MUTED));
            table.addCell(empty);
        }
        for (var c : s.claims()) {
            table.addCell(cellOf(new Paragraph(c.date() != null ? c.date().format(SHORT_FMT) : "—").setFont(regular).setFontSize(9)));
            table.addCell(cellOf(new Paragraph(c.schemeName() != null ? c.schemeName() : "—").setFont(regular).setFontSize(9)));
            table.addCell(cellOf(new Paragraph(zar(c.grossAmount())).setFont(regular).setFontSize(9)));
            table.addCell(cellOf(new Paragraph(zar(c.schemePortion())).setFont(regular).setFontSize(9)));
            table.addCell(cellOf(new Paragraph(zar(c.patientPortion())).setFont(bold).setFontSize(9)));
            table.addCell(cellOf(new Paragraph(c.status()).setFont(regular).setFontSize(9)));
        }
        doc.add(table);
    }

    private void addPaymentsTable(Document doc, ClinicStatementOfAccountService.PatientStatement s, PdfFont bold, PdfFont regular) {
        doc.add(new Paragraph("PAYMENTS RECEIVED").setFont(bold).setFontSize(10).setFontColor(TEXT_MUTED).setMarginBottom(6));
        Table table = new Table(UnitValue.createPercentArray(new float[]{1.5f, 1.5f, 1, 2})).useAllAvailableWidth();
        for (String h : new String[]{"Date", "Method", "Amount", "Reference"}) {
            table.addHeaderCell(headerCell(h, bold));
        }
        if (s.payments().isEmpty()) {
            Cell empty = new Cell(1, 4).setBorder(Border.NO_BORDER).setPadding(10);
            empty.add(new Paragraph("No payments recorded in this period.").setFont(regular).setFontSize(9).setFontColor(TEXT_MUTED));
            table.addCell(empty);
        }
        for (var p : s.payments()) {
            table.addCell(cellOf(new Paragraph(p.date() != null ? p.date().format(SHORT_FMT) : "—").setFont(regular).setFontSize(9)));
            table.addCell(cellOf(new Paragraph(p.method() != null ? p.method() : "—").setFont(regular).setFontSize(9)));
            table.addCell(cellOf(new Paragraph(zar(p.amount())).setFont(bold).setFontSize(9)));
            table.addCell(cellOf(new Paragraph(p.reference() != null ? p.reference() : "—").setFont(regular).setFontSize(9)));
        }
        doc.add(table);

        Table totals = new Table(UnitValue.createPercentArray(new float[]{2, 1})).useAllAvailableWidth().setMarginTop(12);
        addTotalRow(totals, "Total patient portion", zar(s.totalPatientPortion()), regular);
        addTotalRow(totals, "Total paid", zar(s.totalPaid()), regular);
        addTotalRow(totals, "Balance owing", zar(s.balance()), bold);
        doc.add(totals);
    }

    private void addTotalRow(Table t, String label, String value, PdfFont font) {
        t.addCell(cellOf(new Paragraph(label).setFont(font).setFontSize(10)));
        t.addCell(cellOf(new Paragraph(value).setFont(font).setFontSize(10).setTextAlignment(TextAlignment.RIGHT)));
    }

    private void addFooter(Document doc, PdfFont regular) {
        doc.add(new Paragraph("Please remit payment for any outstanding balance at your earliest convenience. Contact the practice with any queries.")
                .setFont(regular).setFontSize(8).setFontColor(TEXT_MUTED).setTextAlignment(TextAlignment.CENTER).setMarginTop(24));
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

    private String zar(BigDecimal amount) {
        return "R " + String.format(Locale.US, "%,.2f", amount != null ? amount : BigDecimal.ZERO);
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