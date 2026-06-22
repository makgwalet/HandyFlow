package za.co.handyflow.platform.invoicing.application.internal;

import com.itextpdf.io.image.ImageDataFactory;
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

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.font.PdfFontFactory.EmbeddingStrategy;
import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
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

    // ── Colours — WHY AMBER accent for quotes?
    // Immediately distinguishable from the teal invoice at a glance.
    // Client sees amber = "awaiting your approval", teal = "please pay this".
    private static final DeviceRgb NAVY        = new DeviceRgb(0x1B, 0x3A, 0x6B);
    private static final DeviceRgb AMBER       = new DeviceRgb(0xD9, 0x77, 0x06);
    private static final DeviceRgb AMBER_LIGHT = new DeviceRgb(0xFE, 0xF3, 0xC7);
    private static final DeviceRgb WHITE       = new DeviceRgb(0xFF, 0xFF, 0xFF);
    private static final DeviceRgb LIGHT_GRAY  = new DeviceRgb(0xF8, 0xFA, 0xFC);
    private static final DeviceRgb MID_GRAY    = new DeviceRgb(0xE2, 0xE8, 0xF0);
    private static final DeviceRgb TEXT_GRAY   = new DeviceRgb(0x64, 0x74, 0x8B);
    private static final DeviceRgb TEXT_DARK   = new DeviceRgb(0x0F, 0x17, 0x2A);
    private static final DeviceRgb ROW_ALT     = new DeviceRgb(0xFF, 0xFB, 0xEB);

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("d MMMM yyyy");

    @Transactional(readOnly = true)
    public byte[] generateQuotePdf(UUID quoteId, TenantId tenantId) {
        Quote quote = quoteRepository
                .findActiveByIdWithLineItems(tenantId, quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", quoteId.toString()));
        TenantDetails tenant = tenantFacade
                .findTenantDetails(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId.toString()));
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
        Document doc = new Document(new PdfDocument(new PdfWriter(baos)), PageSize.A4);
        doc.setMargins(0, 0, 40, 0);

        PdfFont regular = PdfFontFactory.createFont(
                Objects.requireNonNull(
                        QuotePdfService.class.getResourceAsStream("/fonts/LiberationSans-Regular.ttf")
                ).readAllBytes(),
                PdfEncodings.WINANSI, EmbeddingStrategy.FORCE_EMBEDDED);
        PdfFont bold = PdfFontFactory.createFont(
                Objects.requireNonNull(
                        QuotePdfService.class.getResourceAsStream("/fonts/LiberationSans-Bold.ttf")
                ).readAllBytes(),
                PdfEncodings.WINANSI, EmbeddingStrategy.FORCE_EMBEDDED);

        addHeader(doc, quote, tenant, regular, bold);
        addAddressBlock(doc, quote, tenant, tenantId, regular, bold);
        addLineItemsTable(doc, quote, regular, bold);
        addTotalsBlock(doc, quote, regular, bold);
        addTermsSection(doc, tenant, quote, regular, bold);
        addFooter(doc, tenant, regular);

        doc.close();
        return baos.toByteArray();
    }

    private void addHeader(Document doc, Quote quote, TenantDetails tenant,
                           PdfFont regular, PdfFont bold) {
        // Amber accent bar
        Table bar = new Table(new float[]{1})
                .setWidth(UnitValue.createPercentValue(100)).setBorder(Border.NO_BORDER);
        bar.addCell(new Cell().setBackgroundColor(AMBER).setHeight(6).setBorder(Border.NO_BORDER));
        doc.add(bar);

        Table header = new Table(UnitValue.createPercentArray(new float[]{55, 45}))
                .setWidth(UnitValue.createPercentValue(100))
                .setBorder(Border.NO_BORDER).setBackgroundColor(LIGHT_GRAY);

        // Left: logo or company name
        Cell left = new Cell().setBorder(Border.NO_BORDER).setBackgroundColor(LIGHT_GRAY)
                .setPaddingLeft(40).setPaddingTop(24).setPaddingBottom(24).setPaddingRight(20);

        boolean logoAdded = false;
        if (tenant.logoUrl() != null && !tenant.logoUrl().isBlank()) {
            try {
                Image logo = new Image(ImageDataFactory.create(new URL(tenant.logoUrl())))
                        .setMaxHeight(60).setAutoScale(false);
                left.add(logo);
                left.add(new Paragraph(tenant.companyName())
                        .setFont(bold).setFontSize(10).setFontColor(TEXT_DARK)
                        .setMarginTop(6).setMarginBottom(2));
                logoAdded = true;
            } catch (Exception ex) {
                log.warn("Could not load logo: {}", ex.getMessage());
            }
        }
        if (!logoAdded) {
            left.add(new Paragraph(tenant.companyName())
                    .setFont(bold).setFontSize(20).setFontColor(NAVY).setMarginBottom(4));
        }
        if (tenant.vatNumber() != null) {
            left.add(new Paragraph("VAT Reg No: " + tenant.vatNumber())
                    .setFont(regular).setFontSize(8).setFontColor(TEXT_GRAY));
        }

        // Right: QUOTATION label
        Cell right = new Cell().setBorder(Border.NO_BORDER).setBackgroundColor(LIGHT_GRAY)
                .setPaddingRight(40).setPaddingTop(24).setPaddingBottom(24).setPaddingLeft(20)
                .setTextAlignment(TextAlignment.RIGHT);

        right.add(new Paragraph("QUOTATION")
                .setFont(bold).setFontSize(26).setFontColor(AMBER).setMarginBottom(8));
        right.add(new Paragraph(quote.getQuoteNumber())
                .setFont(bold).setFontSize(13).setFontColor(TEXT_DARK).setMarginBottom(3));

        String quoteDate = quote.getCreatedAt() != null
                ? quote.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FMT)
                : LocalDate.now().format(DATE_FMT);
        LocalDate expiry = quote.getCreatedAt() != null
                ? quote.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate().plusDays(30)
                : LocalDate.now().plusDays(30);

        right.add(new Paragraph(quoteDate)
                .setFont(regular).setFontSize(9).setFontColor(TEXT_GRAY).setMarginBottom(2));
        right.add(new Paragraph("Valid until: " + expiry.format(DATE_FMT))
                .setFont(bold).setFontSize(9).setFontColor(AMBER).setMarginBottom(2));
        right.add(new Paragraph("Status: " + quote.getStatus().name())
                .setFont(bold).setFontSize(8).setFontColor(TEXT_GRAY));

        header.addCell(left);
        header.addCell(right);
        doc.add(header);

        Table sep = new Table(new float[]{1})
                .setWidth(UnitValue.createPercentValue(100)).setBorder(Border.NO_BORDER);
        sep.addCell(new Cell().setBackgroundColor(MID_GRAY).setHeight(1).setBorder(Border.NO_BORDER));
        doc.add(sep);
    }

    private void addAddressBlock(Document doc, Quote quote, TenantDetails tenant,
                                 TenantId tenantId, PdfFont regular, PdfFont bold) {
        Table block = new Table(UnitValue.createPercentArray(new float[]{30, 35, 35}))
                .setWidth(UnitValue.createPercentValue(100)).setBorder(Border.NO_BORDER);

        // FROM
        Cell from = new Cell().setBorder(Border.NO_BORDER)
                .setPaddingLeft(40).setPaddingTop(20).setPaddingBottom(20).setPaddingRight(16);
        from.add(sectionLabel("FROM", bold));
        from.add(new Paragraph(tenant.companyName())
                .setFont(bold).setFontSize(9).setFontColor(TEXT_DARK).setMarginBottom(3));
        if (tenant.address() != null) from.add(addressLine(tenant.address(), regular));
        if (tenant.phone() != null)   from.add(bodyLine("Tel: " + tenant.phone(), regular));
        if (tenant.email() != null)   from.add(bodyLine(tenant.email(), regular));

        // QUOTED TO
        Cell quotedTo = new Cell().setBorder(Border.NO_BORDER)
                .setBorderLeft(new SolidBorder(MID_GRAY, 1))
                .setPaddingLeft(16).setPaddingTop(20).setPaddingBottom(20).setPaddingRight(16);
        quotedTo.add(sectionLabel("QUOTED TO", bold));

        String customerName = resolveCustomerName(quote, tenantId);
        quotedTo.add(new Paragraph(customerName)
                .setFont(bold).setFontSize(10).setFontColor(TEXT_DARK).setMarginBottom(3));
        if (quote.getCustomerId() == null) {
            if (quote.getWalkinClientEmail() != null)
                quotedTo.add(bodyLine(quote.getWalkinClientEmail(), regular));
            if (quote.getWalkinClientPhone() != null)
                quotedTo.add(bodyLine(quote.getWalkinClientPhone(), regular));
        }

        // QUOTE DETAILS
        Cell details = new Cell().setBorder(Border.NO_BORDER)
                .setBorderLeft(new SolidBorder(MID_GRAY, 1))
                .setPaddingLeft(16).setPaddingTop(20).setPaddingBottom(20).setPaddingRight(40);
        details.add(sectionLabel("QUOTE DETAILS", bold));

        String quoteDate = quote.getCreatedAt() != null
                ? quote.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FMT)
                : LocalDate.now().format(DATE_FMT);
        LocalDate expiry = quote.getCreatedAt() != null
                ? quote.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate().plusDays(30)
                : LocalDate.now().plusDays(30);

        details.add(metaLine("Quote number:", quote.getQuoteNumber(), regular, bold));
        details.add(metaLine("Quote date:",   quoteDate,              regular, bold));
        details.add(metaLine("Valid until:",  expiry.format(DATE_FMT), regular, bold));
        details.add(metaLine("Status:",       quote.getStatus().name(), regular, bold));

        block.addCell(from);
        block.addCell(quotedTo);
        block.addCell(details);
        doc.add(block);
        addFullWidthLine(doc);
    }

    private void addLineItemsTable(Document doc, Quote quote,
                                   PdfFont regular, PdfFont bold) {
        doc.add(new Paragraph().setMarginTop(4));

        Table table = new Table(UnitValue.createPercentArray(new float[]{38, 10, 14, 8, 8, 14, 8}))
                .setWidth(UnitValue.createPercentValue(100)).setBorder(Border.NO_BORDER)
                .setMarginLeft(40).setMarginRight(40);

        String[] headers = {"Description", "Unit", "Unit Price\n(excl)", "Qty", "VAT %",
                "Line Total\n(excl)", ""};
        for (int i = 0; i < headers.length; i++) {
            table.addHeaderCell(new Cell()
                    .setBackgroundColor(NAVY).setBorder(Border.NO_BORDER).setPadding(8)
                    .setTextAlignment(i >= 2 ? TextAlignment.RIGHT : TextAlignment.LEFT)
                    .add(new Paragraph(headers[i])
                            .setFont(bold).setFontSize(8).setFontColor(WHITE)));
        }

        java.util.List<QuoteLineItem> items = quote.getLineItems();
        for (int i = 0; i < items.size(); i++) {
            QuoteLineItem li = items.get(i);
            DeviceRgb rowBg = (i % 2 == 0) ? WHITE : ROW_ALT;
            boolean last = (i == items.size() - 1);

            table.addCell(lineCell(li.getDescription(), rowBg, regular, TextAlignment.LEFT, last));
            table.addCell(lineCell(li.getUnit(),         rowBg, regular, TextAlignment.LEFT, last));
            table.addCell(lineCell(fmt(li.getUnitPrice()), rowBg, regular, TextAlignment.RIGHT, last));
            table.addCell(lineCell(qty(li.getQuantity()),  rowBg, regular, TextAlignment.RIGHT, last));
            table.addCell(lineCell(li.getVatRate().stripTrailingZeros().toPlainString() + "%",
                    rowBg, regular, TextAlignment.RIGHT, last));
            table.addCell(lineCell(fmt(li.getLineTotal()), rowBg, bold, TextAlignment.RIGHT, last));
            table.addCell(new Cell().setBackgroundColor(rowBg).setBorder(Border.NO_BORDER)
                    .setBorderBottom(last ? Border.NO_BORDER : new SolidBorder(MID_GRAY, 0.5f)));
        }
        doc.add(table);
    }

    private void addTotalsBlock(Document doc, Quote quote,
                                PdfFont regular, PdfFont bold) {
        Table outer = new Table(UnitValue.createPercentArray(new float[]{55, 45}))
                .setWidth(UnitValue.createPercentValue(100)).setBorder(Border.NO_BORDER)
                .setMarginTop(8).setMarginLeft(40).setMarginRight(40);
        outer.addCell(new Cell().setBorder(Border.NO_BORDER));

        Table inner = new Table(UnitValue.createPercentArray(new float[]{60, 40}))
                .setWidth(UnitValue.createPercentValue(100)).setBorder(Border.NO_BORDER);

        inner.addCell(totLabel("Subtotal (excl. VAT):", regular));
        inner.addCell(totValue(fmt(quote.getSubtotal()), regular));
        inner.addCell(totLabel("VAT (15%):", regular));
        inner.addCell(totValue(fmt(quote.getVatTotal()), regular));
        inner.addCell(new Cell(1, 2).setBorder(Border.NO_BORDER)
                .setBorderTop(new SolidBorder(MID_GRAY, 1)).setHeight(1).setPadding(0));
        inner.addCell(new Cell().setBackgroundColor(NAVY).setBorder(Border.NO_BORDER).setPadding(10)
                .add(new Paragraph("QUOTED TOTAL (ZAR)")
                        .setFont(bold).setFontSize(9).setFontColor(WHITE)));
        inner.addCell(new Cell().setBackgroundColor(NAVY).setBorder(Border.NO_BORDER).setPadding(10)
                .setTextAlignment(TextAlignment.RIGHT)
                .add(new Paragraph(fmt(quote.getTotal()))
                        .setFont(bold).setFontSize(14).setFontColor(WHITE)));

        outer.addCell(new Cell().setBorder(Border.NO_BORDER).add(inner));
        doc.add(outer);
    }

    private void addTermsSection(Document doc, TenantDetails tenant,
                                 Quote quote, PdfFont regular, PdfFont bold) {
        doc.add(new Paragraph().setMarginTop(20));
        addFullWidthLine(doc);

        Table t = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                .setWidth(UnitValue.createPercentValue(100)).setBorder(Border.NO_BORDER)
                .setMarginLeft(40).setMarginRight(40);

        // Accept section
        Cell accept = new Cell().setBorder(Border.NO_BORDER)
                .setBorderLeft(new SolidBorder(AMBER, 3))
                .setBackgroundColor(AMBER_LIGHT).setPadding(14).setMarginTop(16);
        accept.add(sectionLabel("TO ACCEPT THIS QUOTE", bold));
        accept.add(new Paragraph("Reply to this quote by email or contact us directly to proceed. "
                + "Once accepted, a tax invoice will be issued.")
                .setFont(regular).setFontSize(9).setFontColor(TEXT_GRAY).setMarginBottom(6));
        if (tenant.email() != null) {
            accept.add(new Paragraph(tenant.email())
                    .setFont(bold).setFontSize(9).setFontColor(AMBER));
        }

        // Payment terms
        Cell terms = new Cell().setBorder(Border.NO_BORDER)
                .setPaddingLeft(24).setPaddingTop(14).setMarginTop(16);
        terms.add(sectionLabel("PAYMENT TERMS", bold));
        String payTerms = tenant.paymentTerms() != null
                ? tenant.paymentTerms()
                : "Payment due within 30 days of invoice date. EFT preferred.";
        terms.add(new Paragraph(payTerms).setFont(regular).setFontSize(9).setFontColor(TEXT_GRAY));

        t.addCell(accept);
        t.addCell(terms);
        doc.add(t);
    }

    private void addFooter(Document doc, TenantDetails tenant, PdfFont regular) {
        doc.add(new Paragraph().setMarginTop(24));
        addFullWidthLine(doc);
        doc.add(new Paragraph(
                "This quotation is valid for 30 days from the date of issue. "
                        + "Prices are subject to change after the validity period. "
                        + "Issued by HandyFlow Business Operating System.")
                .setFont(regular).setFontSize(8).setFontColor(TEXT_GRAY)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginLeft(40).setMarginRight(40).setMarginTop(8).setMarginBottom(2));
        if (tenant.vatNumber() != null) {
            doc.add(new Paragraph("Supplier VAT Registration: " + tenant.vatNumber())
                    .setFont(regular).setFontSize(8).setFontColor(TEXT_GRAY)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginLeft(40).setMarginRight(40));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void addFullWidthLine(Document doc) {
        Table line = new Table(new float[]{1})
                .setWidth(UnitValue.createPercentValue(100)).setBorder(Border.NO_BORDER);
        line.addCell(new Cell().setBackgroundColor(MID_GRAY).setHeight(1).setBorder(Border.NO_BORDER));
        doc.add(line);
    }

    private Paragraph sectionLabel(String text, PdfFont bold) {
        return new Paragraph(text).setFont(bold).setFontSize(8).setFontColor(TEXT_GRAY)
                .setCharacterSpacing(0.8f).setMarginBottom(6);
    }

    private Paragraph bodyLine(String text, PdfFont regular) {
        return new Paragraph(text).setFont(regular).setFontSize(9)
                .setFontColor(TEXT_GRAY).setMarginBottom(2);
    }

    private Paragraph metaLine(String label, String value, PdfFont regular, PdfFont bold) {
        return new Paragraph()
                .add(new Text(label + "  ").setFont(regular).setFontSize(9).setFontColor(TEXT_GRAY))
                .add(new Text(value != null ? value : "—").setFont(bold).setFontSize(9).setFontColor(TEXT_DARK))
                .setMarginBottom(3);
    }

    private Paragraph addressLine(Map<String, String> address, PdfFont regular) {
        String formatted = String.join(", ",
                        nvl(address.get("street")), nvl(address.get("suburb")),
                        nvl(address.get("city")),   nvl(address.get("postalCode")))
                .replaceAll("(, )+", ", ").replaceAll("^, |, $", "");
        return new Paragraph(formatted).setFont(regular).setFontSize(9)
                .setFontColor(TEXT_GRAY).setMarginBottom(4);
    }

    private Cell lineCell(String text, DeviceRgb bg, PdfFont font,
                          TextAlignment align, boolean lastRow) {
        return new Cell().setBackgroundColor(bg).setBorder(Border.NO_BORDER)
                .setBorderBottom(lastRow ? Border.NO_BORDER : new SolidBorder(MID_GRAY, 0.5f))
                .setPadding(7).setTextAlignment(align)
                .add(new Paragraph(text != null ? text : "")
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

    private String resolveCustomerName(Quote quote, TenantId tenantId) {
        if (quote.getCustomerId() == null)
            return quote.getWalkinClientName() != null ? quote.getWalkinClientName() : "Walk-in Client";
        try {
            return crmFacade.findCustomerById(tenantId, quote.getCustomerId())
                    .map(c -> c.name()).orElse("Customer");
        } catch (Exception e) { return "Customer"; }
    }

    private String fmt(BigDecimal value) {
        if (value == null) return "R 0.00";
        return "R " + String.format(java.util.Locale.US, "%,.2f", value.setScale(2, RoundingMode.HALF_UP));
    }

    private String qty(BigDecimal value) {
        if (value == null) return "0";
        return value.stripTrailingZeros().toPlainString();
    }

    private String nvl(String value) { return value != null ? value : ""; }
}
