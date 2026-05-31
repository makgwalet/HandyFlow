package za.co.handyflow.platform.invoicing.application.internal;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
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
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.identity.TenantDetails;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.invoicing.domain.model.Quote;
import za.co.handyflow.platform.invoicing.domain.model.QuoteLineItem;
import za.co.handyflow.platform.invoicing.domain.repository.QuoteRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuotePdfService {

    private final QuoteRepository quoteRepository;
    private final TenantFacade    tenantFacade;
    private final CrmFacade       crmFacade;

    private static final DeviceRgb NAVY       = new DeviceRgb(0x1B, 0x3A, 0x6B);
    private static final DeviceRgb TEAL       = new DeviceRgb(0x0D, 0x94, 0x88);
    private static final DeviceRgb WHITE      = new DeviceRgb(0xFF, 0xFF, 0xFF);
    private static final DeviceRgb LIGHT_GRAY = new DeviceRgb(0xF8, 0xFA, 0xFC);
    private static final DeviceRgb MID_GRAY   = new DeviceRgb(0xE2, 0xE8, 0xF0);
    private static final DeviceRgb TEXT_GRAY  = new DeviceRgb(0x64, 0x74, 0x8B);
    private static final DeviceRgb TEXT_DARK  = new DeviceRgb(0x0F, 0x17, 0x2A);
    private static final DeviceRgb AMBER      = new DeviceRgb(0xD9, 0x77, 0x06);

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("d MMMM yyyy");

    @Transactional(readOnly = true)
    public byte[] generateQuotePdf(UUID quoteId, TenantId tenantId) {
        Quote quote = quoteRepository
                .findActiveByIdWithLineItems(tenantId, quoteId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Quote", quoteId.toString()));

        TenantDetails tenant = tenantFacade
                .findTenantDetails(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant", tenantId.toString()));

        try {
            byte[] pdf = buildPdf(quote, tenant, tenantId);
            log.info("Generated PDF quote={} tenant={} bytes={}",
                    quote.getQuoteNumber(), tenant.slug(), pdf.length);
            return pdf;
        } catch (Exception e) {
            log.error("PDF generation failed quote={}: {}", quoteId, e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF quote", e);
        }
    }

    private byte[] buildPdf(Quote quote, TenantDetails tenant,
                            TenantId tenantId) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter   writer = new PdfWriter(baos);
        PdfDocument pdf    = new PdfDocument(writer);
        Document    doc    = new Document(pdf, PageSize.A4);
        doc.setMargins(36, 50, 50, 50);

        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

        addHeader(doc, quote, tenant, regular, bold);
        addBillingSection(doc, quote, tenant, tenantId, regular, bold);
        addDivider(doc);
        addLineItemsTable(doc, quote, regular, bold);
        addTotalsBox(doc, quote, regular, bold);
        addTermsSection(doc, tenant, quote, regular, bold);
        addFooter(doc, tenant, regular);

        doc.close();
        return baos.toByteArray();
    }

    private void addHeader(Document doc, Quote quote, TenantDetails tenant,
                           PdfFont regular, PdfFont bold) {
        Table header = new Table(UnitValue.createPercentArray(new float[]{55, 45}))
                .setWidth(UnitValue.createPercentValue(100))
                .setBorder(Border.NO_BORDER);

        Cell left = new Cell()
                .setBorder(Border.NO_BORDER)
                .setBackgroundColor(NAVY)
                .setPadding(20);

        left.add(new Paragraph(tenant.companyName())
                .setFont(bold).setFontSize(18)
                .setFontColor(WHITE).setMarginBottom(4));

        if (tenant.vatNumber() != null) {
            left.add(new Paragraph("VAT Reg No: " + tenant.vatNumber())
                    .setFont(regular).setFontSize(9)
                    .setFontColor(LIGHT_GRAY).setMarginBottom(0));
        }

        // WHY AMBER for quote header? Visually distinct from TEAL invoice —
        // makes it immediately obvious this is a quote, not a tax invoice.
        Cell right = new Cell()
                .setBorder(Border.NO_BORDER)
                .setBackgroundColor(AMBER)
                .setPadding(20)
                .setTextAlignment(TextAlignment.RIGHT);

        right.add(new Paragraph("QUOTATION")
                .setFont(bold).setFontSize(22)
                .setFontColor(WHITE).setMarginBottom(6));

        right.add(new Paragraph(quote.getQuoteNumber())
                .setFont(bold).setFontSize(14)
                .setFontColor(WHITE).setMarginBottom(2));

        String quoteDate = quote.getCreatedAt() != null
                ? quote.getCreatedAt().atZone(ZoneId.systemDefault())
                .toLocalDate().format(DATE_FMT)
                : LocalDate.now().format(DATE_FMT);

        right.add(new Paragraph(quoteDate)
                .setFont(regular).setFontSize(9)
                .setFontColor(LIGHT_GRAY).setMarginBottom(0));

        // Expiry — 30 days from creation
        LocalDate expiry = quote.getCreatedAt() != null
                ? quote.getCreatedAt().atZone(ZoneId.systemDefault())
                .toLocalDate().plusDays(30)
                : LocalDate.now().plusDays(30);

        right.add(new Paragraph("Valid until: " + expiry.format(DATE_FMT))
                .setFont(regular).setFontSize(9)
                .setFontColor(LIGHT_GRAY).setMarginBottom(0));

        header.addCell(left);
        header.addCell(right);
        doc.add(header);
    }

    private void addBillingSection(Document doc, Quote quote, TenantDetails tenant,
                                   TenantId tenantId, PdfFont regular, PdfFont bold) {
        Table topRow = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                .setWidth(UnitValue.createPercentValue(100))
                .setBorder(Border.NO_BORDER)
                .setMarginTop(20);

        // FROM
        Cell from = new Cell().setBorder(Border.NO_BORDER).setPaddingRight(20);
        from.add(sectionLabel("FROM", bold));
        from.add(new Paragraph(tenant.companyName())
                .setFont(bold).setFontSize(11).setFontColor(TEXT_DARK).setMarginBottom(3));

        if (tenant.address() != null) {
            from.add(addressLine(tenant.address(), regular));
        }
        if (tenant.phone() != null) {
            from.add(new Paragraph("Tel: " + tenant.phone())
                    .setFont(regular).setFontSize(9).setFontColor(TEXT_GRAY).setMarginBottom(1));
        }
        if (tenant.email() != null) {
            from.add(new Paragraph(tenant.email())
                    .setFont(regular).setFontSize(9).setFontColor(TEXT_GRAY).setMarginBottom(1));
        }

        // QUOTE DETAILS
        Cell details = new Cell().setBorder(Border.NO_BORDER);
        details.add(sectionLabel("QUOTE DETAILS", bold));

        String quoteDate = quote.getCreatedAt() != null
                ? quote.getCreatedAt().atZone(ZoneId.systemDefault())
                .toLocalDate().format(DATE_FMT)
                : LocalDate.now().format(DATE_FMT);

        LocalDate expiry = quote.getCreatedAt() != null
                ? quote.getCreatedAt().atZone(ZoneId.systemDefault())
                .toLocalDate().plusDays(30)
                : LocalDate.now().plusDays(30);

        details.add(metaLine("Quote number:", quote.getQuoteNumber(), regular, bold));
        details.add(metaLine("Quote date:", quoteDate, regular, bold));
        details.add(metaLine("Valid until:", expiry.format(DATE_FMT), regular, bold));
        details.add(metaLine("Status:", quote.getStatus().name(), regular, bold));

        topRow.addCell(from);
        topRow.addCell(details);
        doc.add(topRow);

        // QUOTED TO
        doc.add(new Paragraph().setMarginTop(16));

        String customerName = crmFacade
                .findCustomerById(tenantId, quote.getCustomerId())
                .map(c -> c.name())
                .orElse("Unknown Customer");

        doc.add(sectionLabel("QUOTED TO", bold));
        doc.add(new Paragraph(customerName)
                .setFont(regular).setFontSize(10).setFontColor(TEXT_DARK).setMarginBottom(2));
    }

    private void addLineItemsTable(Document doc, Quote quote,
                                   PdfFont regular, PdfFont bold) {
        doc.add(new Paragraph().setMarginTop(20));

        float[] cols = { 5f, 1.5f, 2f, 1.5f, 1.5f, 2f };
        Table table = new Table(UnitValue.createPercentArray(cols))
                .setWidth(UnitValue.createPercentValue(100));

        for (String h : new String[]{ "Description", "Unit", "Unit Price (excl)", "Qty", "VAT %", "Line Total (excl)" }) {
            table.addHeaderCell(
                    new Cell()
                            .setBackgroundColor(NAVY)
                            .setBorder(Border.NO_BORDER)
                            .setPadding(8)
                            .add(new Paragraph(h)
                                    .setFont(bold).setFontSize(8.5f)
                                    .setFontColor(WHITE))
            );
        }

        boolean shade = false;
        for (QuoteLineItem li : quote.getLineItems()) {
            DeviceRgb rowBg = shade ? LIGHT_GRAY : WHITE;
            shade = !shade;

            table.addCell(dataCell(li.getDescription(),   rowBg, bold,    true));
            table.addCell(dataCell(li.getUnit(),           rowBg, regular, false));
            table.addCell(dataCell(fmt(li.getUnitPrice()), rowBg, regular, false));
            table.addCell(dataCell(qty(li.getQuantity()),  rowBg, regular, false));
            table.addCell(dataCell(li.getVatRate().stripTrailingZeros().toPlainString() + "%", rowBg, regular, false));
            table.addCell(dataCell(fmt(li.getLineTotal()), rowBg, bold,    false));
        }

        doc.add(table);
    }

    private void addTotalsBox(Document doc, Quote quote,
                              PdfFont regular, PdfFont bold) {
        Table outer = new Table(UnitValue.createPercentArray(new float[]{ 55, 45 }))
                .setWidth(UnitValue.createPercentValue(100))
                .setBorder(Border.NO_BORDER)
                .setMarginTop(8);

        outer.addCell(new Cell().setBorder(Border.NO_BORDER));

        Table inner = new Table(UnitValue.createPercentArray(new float[]{ 60, 40 }))
                .setWidth(UnitValue.createPercentValue(100))
                .setBorder(Border.NO_BORDER);

        inner.addCell(totLabel("Subtotal (excl. VAT):", regular));
        inner.addCell(totValue(fmt(quote.getSubtotal()), regular));

        inner.addCell(totLabel("VAT (15%):", regular));
        inner.addCell(totValue(fmt(quote.getVatTotal()), regular));

        inner.addCell(new Cell(1, 2)
                .setBorder(Border.NO_BORDER)
                .setBorderTop(new SolidBorder(AMBER, 1.5f))
                .setHeight(4));

        // WHY AMBER for quote total? Consistent with amber quote header —
        // reinforces "this is a quote, not an invoice yet".
        inner.addCell(
                new Cell().setBackgroundColor(AMBER).setBorder(Border.NO_BORDER)
                        .setPadding(10)
                        .add(new Paragraph("QUOTED TOTAL (ZAR)")
                                .setFont(bold).setFontSize(10).setFontColor(WHITE))
        );
        inner.addCell(
                new Cell().setBackgroundColor(AMBER).setBorder(Border.NO_BORDER)
                        .setPadding(10).setTextAlignment(TextAlignment.RIGHT)
                        .add(new Paragraph(fmt(quote.getTotal()))
                                .setFont(bold).setFontSize(14).setFontColor(WHITE))
        );

        Cell rightCell = new Cell().setBorder(Border.NO_BORDER);
        rightCell.add(inner);
        outer.addCell(rightCell);
        doc.add(outer);
    }

    private void addTermsSection(Document doc, TenantDetails tenant,
                                 Quote quote, PdfFont regular, PdfFont bold) {
        doc.add(new Paragraph().setMarginTop(24));

        Table terms = new Table(UnitValue.createPercentArray(new float[]{ 50, 50 }))
                .setWidth(UnitValue.createPercentValue(100))
                .setBorder(Border.NO_BORDER);

        // Acceptance instructions
        Cell acceptCell = new Cell()
                .setBorder(Border.NO_BORDER)
                .setBorderLeft(new SolidBorder(AMBER, 3))
                .setBackgroundColor(LIGHT_GRAY)
                .setPadding(14);

        acceptCell.add(sectionLabel("TO ACCEPT THIS QUOTE", bold));
        acceptCell.add(new Paragraph(
                "Reply to this quote by email or contact us directly to proceed. "
                        + "Once accepted, a tax invoice will be issued.")
                .setFont(regular).setFontSize(9).setFontColor(TEXT_GRAY));

        if (tenant.email() != null) {
            acceptCell.add(new Paragraph("\n" + tenant.email())
                    .setFont(bold).setFontSize(9).setFontColor(TEXT_DARK));
        }

        // Payment terms
        Cell termsCell = new Cell()
                .setBorder(Border.NO_BORDER)
                .setPaddingLeft(20)
                .setPaddingTop(14);

        termsCell.add(sectionLabel("PAYMENT TERMS", bold));
        String payTerms = tenant.paymentTerms() != null
                ? tenant.paymentTerms()
                : "Payment due within 30 days of invoice date. EFT payments only.";
        termsCell.add(new Paragraph(payTerms)
                .setFont(regular).setFontSize(9).setFontColor(TEXT_GRAY));

        terms.addCell(acceptCell);
        terms.addCell(termsCell);
        doc.add(terms);
    }

    private void addFooter(Document doc, TenantDetails tenant, PdfFont regular) {
        doc.add(new Paragraph().setMarginTop(32));
        doc.add(new Paragraph()
                .setBorderTop(new SolidBorder(MID_GRAY, 1))
                .setMarginBottom(8));
        doc.add(new Paragraph(
                "This quotation is valid for 30 days from the date of issue. "
                        + "Prices are subject to change after the validity period. "
                        + "Issued by HandyFlow Business Operating System.")
                .setFont(regular).setFontSize(8).setFontColor(TEXT_GRAY)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(3));
        if (tenant.vatNumber() != null) {
            doc.add(new Paragraph("Supplier VAT Registration: " + tenant.vatNumber())
                    .setFont(regular).setFontSize(8).setFontColor(TEXT_GRAY)
                    .setTextAlignment(TextAlignment.CENTER));
        }
    }

    private void addDivider(Document doc) {
        doc.add(new Paragraph()
                .setBorderTop(new SolidBorder(MID_GRAY, 1))
                .setMarginTop(16).setMarginBottom(0));
    }

    private Paragraph sectionLabel(String text, PdfFont bold) {
        return new Paragraph(text)
                .setFont(bold).setFontSize(8).setFontColor(TEXT_GRAY)
                .setCharacterSpacing(1f).setMarginBottom(6);
    }

    private Paragraph metaLine(String label, String value,
                               PdfFont regular, PdfFont bold) {
        return new Paragraph()
                .add(new Text(label + "  ").setFont(regular).setFontSize(9).setFontColor(TEXT_GRAY))
                .add(new Text(value).setFont(bold).setFontSize(9).setFontColor(TEXT_DARK))
                .setMarginBottom(3);
    }

    private Paragraph addressLine(Map<String, String> address, PdfFont regular) {
        String formatted = String.join(", ",
                        nvl(address.get("street")),
                        nvl(address.get("suburb")),
                        nvl(address.get("city")),
                        nvl(address.get("postalCode"))
                ).replaceAll("(, )+", ", ")
                .replaceAll("^, |, $", "");
        return new Paragraph(formatted)
                .setFont(regular).setFontSize(9).setFontColor(TEXT_GRAY).setMarginBottom(4);
    }

    private Cell dataCell(String text, DeviceRgb bg, PdfFont font, boolean isBold) {
        return new Cell()
                .setBackgroundColor(bg)
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(MID_GRAY, 0.5f))
                .setPadding(7)
                .add(new Paragraph(text)
                        .setFont(font).setFontSize(9).setFontColor(TEXT_DARK));
    }

    private Cell totLabel(String text, PdfFont font) {
        return new Cell().setBorder(Border.NO_BORDER).setPadding(5)
                .add(new Paragraph(text).setFont(font).setFontSize(9).setFontColor(TEXT_GRAY));
    }

    private Cell totValue(String text, PdfFont font) {
        return new Cell().setBorder(Border.NO_BORDER).setPadding(5)
                .setTextAlignment(TextAlignment.RIGHT)
                .add(new Paragraph(text).setFont(font).setFontSize(9).setFontColor(TEXT_DARK));
    }

    private String fmt(BigDecimal value) {
        if (value == null) return "R 0.00";
        return "R " + String.format(java.util.Locale.US, "%,.2f",
                value.setScale(2, RoundingMode.HALF_UP));
    }

    private String qty(BigDecimal value) {
        if (value == null) return "0";
        return value.stripTrailingZeros().toPlainString();
    }

    private String nvl(String value) {
        return value != null ? value : "";
    }
}