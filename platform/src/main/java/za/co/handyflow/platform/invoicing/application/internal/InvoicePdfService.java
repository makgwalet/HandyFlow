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
import za.co.handyflow.platform.invoicing.domain.model.Invoice;
import za.co.handyflow.platform.invoicing.domain.model.InvoiceLineItem;
import za.co.handyflow.platform.invoicing.domain.repository.InvoiceRepository;
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
public class InvoicePdfService {

    private final InvoiceRepository invoiceRepository;
    private final TenantFacade      tenantFacade;
    private final CrmFacade         crmFacade;

    // ── Brand colours ─────────────────────────────────────────────────────────
    private static final DeviceRgb NAVY       = new DeviceRgb(0x1B, 0x3A, 0x6B);
    private static final DeviceRgb TEAL       = new DeviceRgb(0x0D, 0x94, 0x88);
    private static final DeviceRgb TEAL_LIGHT = new DeviceRgb(0xCC, 0xFB, 0xF1);
    private static final DeviceRgb WHITE      = new DeviceRgb(0xFF, 0xFF, 0xFF);
    private static final DeviceRgb LIGHT_GRAY = new DeviceRgb(0xF8, 0xFA, 0xFC);
    private static final DeviceRgb MID_GRAY   = new DeviceRgb(0xE2, 0xE8, 0xF0);
    private static final DeviceRgb TEXT_GRAY  = new DeviceRgb(0x64, 0x74, 0x8B);
    private static final DeviceRgb TEXT_DARK  = new DeviceRgb(0x0F, 0x17, 0x2A);
    private static final DeviceRgb ROW_ALT    = new DeviceRgb(0xF0, 0xFD, 0xFA);

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("d MMMM yyyy");

    // ── Entry point ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] generateInvoicePdf(UUID invoiceId, TenantId tenantId) {
        Invoice invoice = invoiceRepository
                .findActiveByIdWithLineItems(tenantId, invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId.toString()));

        TenantDetails tenant = tenantFacade
                .findTenantDetails(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId.toString()));

        try {
            byte[] pdf = buildPdf(invoice, tenant, tenantId);
            log.info("Generated PDF invoice={} tenant={} bytes={}",
                    invoice.getInvoiceNumber(), tenant.slug(), pdf.length);
            return pdf;
        } catch (Exception e) {
            log.error("PDF generation failed invoice={}: {}", invoiceId, e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF invoice", e);
        }
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    private byte[] buildPdf(Invoice invoice, TenantDetails tenant,
                            TenantId tenantId) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(new PdfDocument(new PdfWriter(baos)), PageSize.A4);
        doc.setMargins(0, 0, 40, 0);

        PdfFont regular = PdfFontFactory.createFont(
                Objects.requireNonNull(
                        InvoicePdfService.class.getResourceAsStream("/fonts/LiberationSans-Regular.ttf")
                ).readAllBytes(),
                PdfEncodings.WINANSI, EmbeddingStrategy.FORCE_EMBEDDED);
        PdfFont bold = PdfFontFactory.createFont(
                Objects.requireNonNull(
                        InvoicePdfService.class.getResourceAsStream("/fonts/LiberationSans-Bold.ttf")
                ).readAllBytes(),
                PdfEncodings.WINANSI, EmbeddingStrategy.FORCE_EMBEDDED);

        addHeader(doc, invoice, tenant, regular, bold);
        addAddressBlock(doc, invoice, tenant, tenantId, regular, bold);
        addLineItemsTable(doc, invoice, regular, bold);
        addTotalsBlock(doc, invoice, regular, bold);
        addPaymentAndTerms(doc, tenant, invoice, regular, bold);
        addFooter(doc, tenant, regular);

        doc.close();
        return baos.toByteArray();
    }

    // ── SECTION 1: Full-width header (QuickBooks style) ───────────────────────
    // Left: logo or company name — Right: TAX INVOICE label + number + date
    // A full-bleed accent bar runs across the top.

    private void addHeader(Document doc, Invoice invoice, TenantDetails tenant,
                           PdfFont regular, PdfFont bold) {

        // ── Top accent bar ────────────────────────────────────────────────────
        Table bar = new Table(new float[]{1})
                .setWidth(UnitValue.createPercentValue(100))
                .setBorder(Border.NO_BORDER);
        bar.addCell(new Cell()
                .setBackgroundColor(TEAL)
                .setHeight(6)
                .setBorder(Border.NO_BORDER));
        doc.add(bar);

        // ── Logo + company | Invoice label block ──────────────────────────────
        Table header = new Table(UnitValue.createPercentArray(new float[]{55, 45}))
                .setWidth(UnitValue.createPercentValue(100))
                .setBorder(Border.NO_BORDER)
                .setBackgroundColor(LIGHT_GRAY);

        // ── LEFT: logo (if available) or company name ─────────────────────────
        Cell left = new Cell()
                .setBorder(Border.NO_BORDER)
                .setBackgroundColor(LIGHT_GRAY)
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
                log.warn("Could not load tenant logo url={}: {}", tenant.logoUrl(), ex.getMessage());
            }
        }
        if (!logoAdded) {
            // Fallback: styled company name as logo
            left.add(new Paragraph(tenant.companyName())
                    .setFont(bold).setFontSize(20).setFontColor(NAVY)
                    .setMarginBottom(4));
        }
        if (tenant.vatNumber() != null) {
            left.add(new Paragraph("VAT Reg No: " + tenant.vatNumber())
                    .setFont(regular).setFontSize(8).setFontColor(TEXT_GRAY));
        }

        // ── RIGHT: TAX INVOICE + number + date ────────────────────────────────
        Cell right = new Cell()
                .setBorder(Border.NO_BORDER)
                .setBackgroundColor(LIGHT_GRAY)
                .setPaddingRight(40).setPaddingTop(24).setPaddingBottom(24).setPaddingLeft(20)
                .setTextAlignment(TextAlignment.RIGHT);

        right.add(new Paragraph("TAX INVOICE")
                .setFont(bold).setFontSize(26).setFontColor(TEAL)
                .setMarginBottom(8));

        right.add(new Paragraph(invoice.getInvoiceNumber())
                .setFont(bold).setFontSize(13).setFontColor(TEXT_DARK).setMarginBottom(3));

        String invoiceDate = invoice.getIssuedAt() != null
                ? invoice.getIssuedAt().atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FMT)
                : LocalDate.now().format(DATE_FMT);
        right.add(new Paragraph(invoiceDate)
                .setFont(regular).setFontSize(9).setFontColor(TEXT_GRAY).setMarginBottom(2));

        // Due date
        LocalDate due = invoice.getDueDate() != null
                ? invoice.getDueDate()
                : (invoice.getIssuedAt() != null
                ? invoice.getIssuedAt().atZone(ZoneId.systemDefault()).toLocalDate().plusDays(30)
                : LocalDate.now().plusDays(30));
        right.add(new Paragraph("Due: " + due.format(DATE_FMT))
                .setFont(bold).setFontSize(9).setFontColor(NAVY).setMarginBottom(2));

        // Status pill
        right.add(new Paragraph(invoice.getStatus().name())
                .setFont(bold).setFontSize(8).setFontColor(TEAL));

        header.addCell(left);
        header.addCell(right);
        doc.add(header);

        // ── Bottom separator ──────────────────────────────────────────────────
        Table sep = new Table(new float[]{1})
                .setWidth(UnitValue.createPercentValue(100))
                .setBorder(Border.NO_BORDER);
        sep.addCell(new Cell().setBackgroundColor(MID_GRAY).setHeight(1).setBorder(Border.NO_BORDER));
        doc.add(sep);
    }

    // ── SECTION 2: FROM / BILL TO / INVOICE META ──────────────────────────────

    private void addAddressBlock(Document doc, Invoice invoice, TenantDetails tenant,
                                 TenantId tenantId, PdfFont regular, PdfFont bold) {
        // WHY 30/35/35? FROM column has shorter content (address lines fit fine at 30%);
        // giving more width to BILL TO and INVOICE DETAILS prevents long values like
        // "INV-F74DBF2F" or company names from wrapping awkwardly.
        Table block = new Table(UnitValue.createPercentArray(new float[]{30, 35, 35}))
                .setWidth(UnitValue.createPercentValue(100))
                .setBorder(Border.NO_BORDER)
                .setMarginTop(0);

        // ── FROM ──────────────────────────────────────────────────────────────
        Cell from = new Cell().setBorder(Border.NO_BORDER)
                .setPaddingLeft(40).setPaddingTop(20).setPaddingBottom(20).setPaddingRight(16);
        from.add(sectionLabel("FROM", bold));
        // WHY fontSize 9 instead of 10? Company names like "Zeta Earthmoving (Pty) Ltd"
        // can be 30+ chars. At 10pt in a 30% column (~150px) they wrap. 9pt fits cleanly.
        from.add(new Paragraph(tenant.companyName())
                .setFont(bold).setFontSize(9).setFontColor(TEXT_DARK).setMarginBottom(3));
        if (tenant.address() != null) from.add(addressLine(tenant.address(), regular));
        if (tenant.phone() != null)
            from.add(bodyLine("Tel: " + tenant.phone(), regular));
        if (tenant.email() != null)
            from.add(bodyLine(tenant.email(), regular));

        // ── BILL TO ───────────────────────────────────────────────────────────
        Cell billTo = new Cell().setBorder(Border.NO_BORDER)
                .setBorderLeft(new SolidBorder(MID_GRAY, 1))
                .setPaddingLeft(16).setPaddingTop(20).setPaddingBottom(20).setPaddingRight(16);
        billTo.add(sectionLabel("BILL TO", bold));

        String customerName = resolveCustomerName(invoice, tenantId);
        billTo.add(new Paragraph(customerName)
                .setFont(bold).setFontSize(10).setFontColor(TEXT_DARK).setMarginBottom(3));

        // Walk-in contact details
        if (invoice.getCustomerId() == null) {
            if (invoice.getWalkinClientEmail() != null)
                billTo.add(bodyLine(invoice.getWalkinClientEmail(), regular));
            if (invoice.getWalkinClientPhone() != null)
                billTo.add(bodyLine(invoice.getWalkinClientPhone(), regular));
        } else {
            // CRM customer — try to fetch contact info
            try {
                crmFacade.findCustomerById(tenantId, invoice.getCustomerId())
                        .ifPresent(c -> {
                            if (c.email() != null) billTo.add(bodyLine(c.email(), regular));
                            if (c.phone() != null) billTo.add(bodyLine(c.phone(), regular));
                            if (c.taxNumber() != null)
                                billTo.add(bodyLine("VAT: " + c.taxNumber(), regular));
                        });
            } catch (Exception ignored) {}
        }

        // ── INVOICE DETAILS ───────────────────────────────────────────────────
        Cell details = new Cell().setBorder(Border.NO_BORDER)
                .setBorderLeft(new SolidBorder(MID_GRAY, 1))
                .setPaddingLeft(16).setPaddingTop(20).setPaddingBottom(20).setPaddingRight(40);
        details.add(sectionLabel("INVOICE DETAILS", bold));

        String invoiceDate = invoice.getIssuedAt() != null
                ? invoice.getIssuedAt().atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FMT)
                : LocalDate.now().format(DATE_FMT);
        LocalDate due = invoice.getDueDate() != null
                ? invoice.getDueDate()
                : (invoice.getIssuedAt() != null
                ? invoice.getIssuedAt().atZone(ZoneId.systemDefault()).toLocalDate().plusDays(30)
                : LocalDate.now().plusDays(30));

        details.add(metaLine("Invoice number:", invoice.getInvoiceNumber(), regular, bold));
        details.add(metaLine("Invoice date:",   invoiceDate,                 regular, bold));
        details.add(metaLine("Due date:",       due.format(DATE_FMT),        regular, bold));
        details.add(metaLine("Status:",         invoice.getStatus().name(),  regular, bold));

        block.addCell(from);
        block.addCell(billTo);
        block.addCell(details);
        doc.add(block);

        // Separator
        addFullWidthLine(doc);
    }

    // ── SECTION 3: Line items table ───────────────────────────────────────────

    private void addLineItemsTable(Document doc, Invoice invoice,
                                   PdfFont regular, PdfFont bold) {
        doc.add(new Paragraph().setMarginTop(4));

        // Column widths: Description | Unit | Unit Price | Qty | VAT% | Line Total
        Table table = new Table(UnitValue.createPercentArray(new float[]{38, 10, 14, 8, 8, 14, 8}))
                .setWidth(UnitValue.createPercentValue(100))
                .setBorder(Border.NO_BORDER)
                .setMarginLeft(40).setMarginRight(40);

        // ── Header row ────────────────────────────────────────────────────────
        String[] headers = {"Description", "Unit", "Unit Price\n(excl)", "Qty", "VAT %",
                "Line Total\n(excl)", ""};
        for (int i = 0; i < headers.length; i++) {
            table.addHeaderCell(new Cell()
                    .setBackgroundColor(NAVY)
                    .setBorder(Border.NO_BORDER)
                    .setPadding(8)
                    .setTextAlignment(i >= 2 ? TextAlignment.RIGHT : TextAlignment.LEFT)
                    .add(new Paragraph(headers[i])
                            .setFont(bold).setFontSize(8).setFontColor(WHITE)));
        }

        // ── Data rows ─────────────────────────────────────────────────────────
        java.util.List<InvoiceLineItem> items = invoice.getLineItems();
        for (int i = 0; i < items.size(); i++) {
            InvoiceLineItem li = items.get(i);
            DeviceRgb rowBg = (i % 2 == 0) ? WHITE : ROW_ALT;
            boolean last = (i == items.size() - 1);

            table.addCell(lineCell(li.getDescription(), rowBg, regular, TextAlignment.LEFT, last));
            table.addCell(lineCell(li.getUnit(),         rowBg, regular, TextAlignment.LEFT, last));
            table.addCell(lineCell(fmt(li.getUnitPrice()), rowBg, regular, TextAlignment.RIGHT, last));
            table.addCell(lineCell(qty(li.getQuantity()),  rowBg, regular, TextAlignment.RIGHT, last));
            table.addCell(lineCell(li.getVatRate().stripTrailingZeros().toPlainString() + "%",
                    rowBg, regular, TextAlignment.RIGHT, last));
            table.addCell(lineCell(fmt(li.getLineTotal()), rowBg, bold,    TextAlignment.RIGHT, last));
            // Empty last col for visual balance
            table.addCell(new Cell().setBackgroundColor(rowBg).setBorder(Border.NO_BORDER)
                    .setBorderBottom(last ? Border.NO_BORDER : new SolidBorder(MID_GRAY, 0.5f)));
        }

        doc.add(table);
    }

    // ── SECTION 4: Totals box (right-aligned, QuickBooks style) ──────────────

    private void addTotalsBlock(Document doc, Invoice invoice,
                                PdfFont regular, PdfFont bold) {
        // Outer: spacer left + totals right
        Table outer = new Table(UnitValue.createPercentArray(new float[]{55, 45}))
                .setWidth(UnitValue.createPercentValue(100))
                .setBorder(Border.NO_BORDER)
                .setMarginTop(8)
                .setMarginLeft(40).setMarginRight(40);

        outer.addCell(new Cell().setBorder(Border.NO_BORDER)); // spacer

        // Inner totals
        Table inner = new Table(UnitValue.createPercentArray(new float[]{60, 40}))
                .setWidth(UnitValue.createPercentValue(100))
                .setBorder(Border.NO_BORDER);

        inner.addCell(totLabel("Subtotal (excl. VAT):", regular));
        inner.addCell(totValue(fmt(invoice.getSubtotal()), regular));

        inner.addCell(totLabel("VAT (" + primaryVatRate(invoice) + "%):", regular));
        inner.addCell(totValue(fmt(invoice.getVatTotal()), regular));

        // Divider row
        inner.addCell(new Cell(1, 2)
                .setBorder(Border.NO_BORDER)
                .setBorderTop(new SolidBorder(MID_GRAY, 1))
                .setHeight(1).setPadding(0));

        // Total due — navy background
        inner.addCell(new Cell()
                .setBackgroundColor(NAVY).setBorder(Border.NO_BORDER).setPadding(10)
                .add(new Paragraph("TOTAL DUE (ZAR)")
                        .setFont(bold).setFontSize(9).setFontColor(WHITE)));
        inner.addCell(new Cell()
                .setBackgroundColor(NAVY).setBorder(Border.NO_BORDER).setPadding(10)
                .setTextAlignment(TextAlignment.RIGHT)
                .add(new Paragraph(fmt(invoice.getTotal()))
                        .setFont(bold).setFontSize(14).setFontColor(WHITE)));

        // Amount paid / balance
        if (invoice.getAmountPaid() != null &&
                invoice.getAmountPaid().compareTo(BigDecimal.ZERO) > 0) {
            inner.addCell(totLabel("Amount paid:", regular));
            inner.addCell(totValue(fmt(invoice.getAmountPaid()), regular));
            BigDecimal balance = invoice.getTotal().subtract(invoice.getAmountPaid());
            inner.addCell(totLabel("Balance due:", bold));
            inner.addCell(totValue(fmt(balance.max(BigDecimal.ZERO)), bold));
        }

        // Overpaid credit
        if (invoice.getCreditAmount() != null &&
                invoice.getCreditAmount().compareTo(BigDecimal.ZERO) > 0) {
            inner.addCell(new Cell()
                    .setBorder(Border.NO_BORDER).setPadding(6)
                    .add(new Paragraph("Credit on account:")
                            .setFont(bold).setFontSize(9).setFontColor(TEAL)));
            inner.addCell(new Cell()
                    .setBorder(Border.NO_BORDER).setPadding(6)
                    .setTextAlignment(TextAlignment.RIGHT)
                    .add(new Paragraph(fmt(invoice.getCreditAmount()))
                            .setFont(bold).setFontSize(9).setFontColor(TEAL)));
        }

        outer.addCell(new Cell().setBorder(Border.NO_BORDER).add(inner));
        doc.add(outer);
    }

    // ── SECTION 5: Payment details + terms ───────────────────────────────────

    private void addPaymentAndTerms(Document doc, TenantDetails tenant,
                                    Invoice invoice, PdfFont regular, PdfFont bold) {
        if (tenant.bankName() == null) return;

        doc.add(new Paragraph().setMarginTop(20));
        addFullWidthLine(doc);

        Table payment = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                .setWidth(UnitValue.createPercentValue(100))
                .setBorder(Border.NO_BORDER)
                .setMarginLeft(40).setMarginRight(40);

        // Banking — teal left accent
        Cell bank = new Cell().setBorder(Border.NO_BORDER)
                .setBorderLeft(new SolidBorder(TEAL, 3))
                .setBackgroundColor(TEAL_LIGHT)
                .setPadding(14).setMarginTop(16);

        bank.add(sectionLabel("PAYMENT DETAILS", bold));
        bank.add(metaLine("Bank:",           tenant.bankName(),    regular, bold));
        bank.add(metaLine("Account number:", tenant.bankAccount(), regular, bold));
        bank.add(metaLine("Branch code:",    tenant.bankBranch(),  regular, bold));
        bank.add(metaLine("Reference:",      invoice.getInvoiceNumber(), regular, bold));

        // Terms
        Cell terms = new Cell().setBorder(Border.NO_BORDER)
                .setPaddingLeft(24).setPaddingTop(14).setMarginTop(16);
        terms.add(sectionLabel("PAYMENT TERMS", bold));
        String t = tenant.paymentTerms() != null
                ? tenant.paymentTerms()
                : "Payment due within 30 days of invoice date. EFT preferred.";
        terms.add(new Paragraph(t).setFont(regular).setFontSize(9).setFontColor(TEXT_GRAY));

        payment.addCell(bank);
        payment.addCell(terms);
        doc.add(payment);
    }

    // ── SECTION 6: Footer ─────────────────────────────────────────────────────

    private void addFooter(Document doc, TenantDetails tenant, PdfFont regular) {
        doc.add(new Paragraph().setMarginTop(24));
        addFullWidthLine(doc);
        doc.add(new Paragraph(
                "This is a computer-generated tax invoice. Valid without a signature. "
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
        return new Cell()
                .setBackgroundColor(bg)
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(lastRow ? Border.NO_BORDER : new SolidBorder(MID_GRAY, 0.5f))
                .setPadding(7)
                .setTextAlignment(align)
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

    private String primaryVatRate(Invoice invoice) {
        return invoice.getLineItems().stream()
                .filter(li -> li.getVatRate() != null)
                .map(li -> li.getVatRate().stripTrailingZeros().toPlainString())
                .findFirst().orElse("15");
    }

    private String resolveCustomerName(Invoice invoice, TenantId tenantId) {
        if (invoice.getCustomerId() == null) {
            return invoice.getWalkinClientName() != null
                    ? invoice.getWalkinClientName() : "Walk-in Client";
        }
        try {
            return crmFacade.findCustomerById(tenantId, invoice.getCustomerId())
                    .map(c -> c.name()).orElse("Customer");
        } catch (Exception e) {
            return "Customer";
        }
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
