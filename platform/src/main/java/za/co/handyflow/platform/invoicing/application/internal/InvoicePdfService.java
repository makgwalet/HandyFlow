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
import za.co.handyflow.platform.invoicing.domain.model.Invoice;
import za.co.handyflow.platform.invoicing.domain.model.InvoiceLineItem;
import za.co.handyflow.platform.invoicing.domain.repository.InvoiceRepository;
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
public class InvoicePdfService {

    private final InvoiceRepository invoiceRepository;
    private final TenantFacade      tenantFacade;
    private final CrmFacade crmFacade;

    // ── Brand colours — all DeviceRgb to avoid type mismatches ───────────────
    private static final DeviceRgb NAVY       = new DeviceRgb(0x1B, 0x3A, 0x6B);
    private static final DeviceRgb TEAL       = new DeviceRgb(0x0D, 0x94, 0x88);
    private static final DeviceRgb WHITE      = new DeviceRgb(0xFF, 0xFF, 0xFF);
    private static final DeviceRgb LIGHT_GRAY = new DeviceRgb(0xF8, 0xFA, 0xFC);
    private static final DeviceRgb MID_GRAY   = new DeviceRgb(0xE2, 0xE8, 0xF0);
    private static final DeviceRgb TEXT_GRAY  = new DeviceRgb(0x64, 0x74, 0x8B);
    private static final DeviceRgb TEXT_DARK  = new DeviceRgb(0x0F, 0x17, 0x2A);

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("d MMMM yyyy");

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC ENTRY POINT
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] generateInvoicePdf(UUID invoiceId, TenantId tenantId) {
        Invoice invoice = invoiceRepository
                .findActiveByIdWithLineItems(tenantId, invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Invoice", invoiceId.toString()
                ));

        TenantDetails tenant = tenantFacade
                .findTenantDetails(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant", tenantId.toString()
                ));

        try {
            byte[] pdf = buildPdf(invoice, tenant, tenantId);
            log.info("Generated PDF invoice={} tenant={} bytes={}",
                    invoice.getInvoiceNumber(), tenant.slug(), pdf.length);
            return pdf;
        } catch (Exception e) {
            log.error("PDF generation failed invoice={}: {}",
                    invoiceId, e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF invoice", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PDF BUILDER
    // ─────────────────────────────────────────────────────────────────────────

    private byte[] buildPdf(Invoice invoice, TenantDetails tenant, TenantId tenantId) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter   writer  = new PdfWriter(baos);
        PdfDocument pdf     = new PdfDocument(writer);
        Document    doc     = new Document(pdf, PageSize.A4);
        doc.setMargins(36, 50, 50, 50);

        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

        addHeader(doc, invoice, tenant, regular, bold);
        addBillingSection(doc, invoice, tenant, tenantId, regular, bold);
        addDivider(doc);
        addLineItemsTable(doc, invoice, regular, bold);
        addTotalsBox(doc, invoice, regular, bold);
        addPaymentDetails(doc, tenant, invoice, regular, bold);
        addFooter(doc, tenant, regular);

        doc.close();
        return baos.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 1 — HEADER BAR
    // ─────────────────────────────────────────────────────────────────────────

    private void addHeader(Document doc, Invoice invoice, TenantDetails tenant,
                           PdfFont regular, PdfFont bold) {
        Table header = new Table(UnitValue.createPercentArray(new float[]{55, 45}))
                .setWidth(UnitValue.createPercentValue(100))
                .setBorder(Border.NO_BORDER);

        // Left — company name on navy background
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

        // Right — TAX INVOICE label on teal background
        Cell right = new Cell()
                .setBorder(Border.NO_BORDER)
                .setBackgroundColor(TEAL)
                .setPadding(20)
                .setTextAlignment(TextAlignment.RIGHT);

        right.add(new Paragraph("TAX INVOICE")
                .setFont(bold).setFontSize(22)
                .setFontColor(WHITE).setMarginBottom(6));

        right.add(new Paragraph(invoice.getInvoiceNumber())
                .setFont(bold).setFontSize(14)
                .setFontColor(WHITE).setMarginBottom(2));

        String invoiceDate = invoice.getIssuedAt() != null
                ? invoice.getIssuedAt().atZone(ZoneId.systemDefault())
                .toLocalDate().format(DATE_FMT)
                : LocalDate.now().format(DATE_FMT);

        right.add(new Paragraph(invoiceDate)
                .setFont(regular).setFontSize(9)
                .setFontColor(LIGHT_GRAY).setMarginBottom(0));

        header.addCell(left);
        header.addCell(right);
        doc.add(header);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 2 — FROM / INVOICE DETAILS / BILL TO
    // ─────────────────────────────────────────────────────────────────────────

    private void addBillingSection(Document doc, Invoice invoice, TenantDetails tenant,
                                   TenantId tenantId, PdfFont regular, PdfFont bold) {
        // FROM + INVOICE DETAILS side by side
        Table topRow = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                .setWidth(UnitValue.createPercentValue(100))
                .setBorder(Border.NO_BORDER)
                .setMarginTop(20);

        // FROM — supplier details
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

        // INVOICE DETAILS — dates and status
        Cell details = new Cell().setBorder(Border.NO_BORDER);
        details.add(sectionLabel("INVOICE DETAILS", bold));

        String issuedDate = invoice.getIssuedAt() != null
                ? invoice.getIssuedAt().atZone(ZoneId.systemDefault())
                .toLocalDate().format(DATE_FMT)
                : LocalDate.now().format(DATE_FMT);

        String dueDate = invoice.getDueDate() != null
                ? invoice.getDueDate().format(DATE_FMT)
                : LocalDate.now().plusDays(30).format(DATE_FMT);

        details.add(metaLine("Invoice number:", invoice.getInvoiceNumber(), regular, bold));
        details.add(metaLine("Invoice date:", issuedDate, regular, bold));
        details.add(metaLine("Due date:", dueDate, regular, bold));
        details.add(metaLine("Status:", invoice.getStatus().name(), regular, bold));

        topRow.addCell(from);
        topRow.addCell(details);
        doc.add(topRow);

        // BILL TO — customer section
        doc.add(new Paragraph().setMarginTop(16));

        String customerName = crmFacade
                .findCustomerById(tenantId, invoice.getCustomerId())
                .map(c -> c.name())
                .orElse("Unknown Customer");

        doc.add(sectionLabel("BILL TO", bold));
        // WHY customerId here? The CRM facade cross-module call would be needed
        // to resolve the customer name. For now we show the ID as a placeholder.
        // When CrmFacade is wired in, replace this with customer.name() etc.
        doc.add(new Paragraph(customerName)
                .setFont(regular).setFontSize(10).setFontColor(TEXT_DARK).setMarginBottom(2));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 3 — LINE ITEMS TABLE
    // ─────────────────────────────────────────────────────────────────────────

    private void addLineItemsTable(Document doc, Invoice invoice,
                                   PdfFont regular, PdfFont bold) {
        doc.add(new Paragraph().setMarginTop(20));

        float[] cols = { 5f, 1.5f, 2f, 1.5f, 1.5f, 2f };
        Table table = new Table(UnitValue.createPercentArray(cols))
                .setWidth(UnitValue.createPercentValue(100));

        // Header row
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

        // Data rows — alternate shading
        boolean shade = false;
        for (InvoiceLineItem li : invoice.getLineItems()) {
            DeviceRgb rowBg = shade ? LIGHT_GRAY : WHITE;
            shade = !shade;

            table.addCell(dataCell(li.getDescription(),     rowBg, bold,    true));
            table.addCell(dataCell(li.getUnit(),             rowBg, regular, false));
            table.addCell(dataCell(fmt(li.getUnitPrice()),   rowBg, regular, false));
            table.addCell(dataCell(qty(li.getQuantity()),    rowBg, regular, false));
            table.addCell(dataCell(li.getVatRate().stripTrailingZeros().toPlainString() + "%", rowBg, regular, false));
            table.addCell(dataCell(fmt(li.getLineTotal()),   rowBg, bold,    false));
        }

        doc.add(table);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 4 — TOTALS BOX
    // ─────────────────────────────────────────────────────────────────────────

    private void addTotalsBox(Document doc, Invoice invoice,
                              PdfFont regular, PdfFont bold) {
        // Two-column layout — empty left, totals on right
        Table outer = new Table(UnitValue.createPercentArray(new float[]{ 55, 45 }))
                .setWidth(UnitValue.createPercentValue(100))
                .setBorder(Border.NO_BORDER)
                .setMarginTop(8);

        outer.addCell(new Cell().setBorder(Border.NO_BORDER)); // spacer

        // Inner totals table
        Table inner = new Table(UnitValue.createPercentArray(new float[]{ 60, 40 }))
                .setWidth(UnitValue.createPercentValue(100))
                .setBorder(Border.NO_BORDER);

        // Subtotal
        inner.addCell(totLabel("Subtotal (excl. VAT):", regular));
        inner.addCell(totValue(fmt(invoice.getSubtotal()), regular));

        // VAT
        inner.addCell(totLabel("VAT (15%):", regular));
        inner.addCell(totValue(fmt(invoice.getVatTotal()), regular));

        // Divider
        inner.addCell(new Cell(1, 2)
                .setBorder(Border.NO_BORDER)
                .setBorderTop(new SolidBorder(NAVY, 1.5f))
                .setHeight(4));

        // Total due — navy highlight
        inner.addCell(
                new Cell().setBackgroundColor(NAVY).setBorder(Border.NO_BORDER)
                        .setPadding(10)
                        .add(new Paragraph("TOTAL DUE (ZAR)")
                                .setFont(bold).setFontSize(10).setFontColor(WHITE))
        );
        inner.addCell(
                new Cell().setBackgroundColor(NAVY).setBorder(Border.NO_BORDER)
                        .setPadding(10).setTextAlignment(TextAlignment.RIGHT)
                        .add(new Paragraph(fmt(invoice.getTotal()))
                                .setFont(bold).setFontSize(14).setFontColor(WHITE))
        );

        // Amount paid / balance due (if applicable)
        if (invoice.getAmountPaid() != null &&
                invoice.getAmountPaid().compareTo(BigDecimal.ZERO) > 0) {
            inner.addCell(totLabel("Amount paid:", regular));
            inner.addCell(totValue(fmt(invoice.getAmountPaid()), regular));

            BigDecimal balance = invoice.getTotal()
                    .subtract(invoice.getAmountPaid());
            inner.addCell(totLabel("Balance due:", bold));
            inner.addCell(totValue(fmt(balance), bold));
        }

        Cell rightCell = new Cell().setBorder(Border.NO_BORDER);
        rightCell.add(inner);
        outer.addCell(rightCell);
        doc.add(outer);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 5 — PAYMENT DETAILS
    // ─────────────────────────────────────────────────────────────────────────

    private void addPaymentDetails(Document doc, TenantDetails tenant,
                                   Invoice invoice, PdfFont regular, PdfFont bold) {
        if (tenant.bankName() == null) return;

        doc.add(new Paragraph().setMarginTop(24));

        Table payment = new Table(UnitValue.createPercentArray(new float[]{ 50, 50 }))
                .setWidth(UnitValue.createPercentValue(100))
                .setBorder(Border.NO_BORDER);

        // Banking details — teal left border
        Cell bankCell = new Cell()
                .setBorder(Border.NO_BORDER)
                .setBorderLeft(new SolidBorder(TEAL, 3))
                .setBackgroundColor(LIGHT_GRAY)
                .setPadding(14);

        bankCell.add(sectionLabel("PAYMENT DETAILS", bold));
        bankCell.add(metaLine("Bank:", tenant.bankName(), regular, bold));
        bankCell.add(metaLine("Account number:", tenant.bankAccount(), regular, bold));
        bankCell.add(metaLine("Branch code:", tenant.bankBranch(), regular, bold));
        bankCell.add(metaLine("Reference:", invoice.getInvoiceNumber(), regular, bold));

        // Payment terms
        Cell termsCell = new Cell()
                .setBorder(Border.NO_BORDER)
                .setPaddingLeft(20)
                .setPaddingTop(14);

        termsCell.add(sectionLabel("PAYMENT TERMS", bold));
        String terms = tenant.paymentTerms() != null
                ? tenant.paymentTerms()
                : "Payment due within 30 days of invoice date. EFT payments only.";
        termsCell.add(new Paragraph(terms)
                .setFont(regular).setFontSize(9).setFontColor(TEXT_GRAY));

        payment.addCell(bankCell);
        payment.addCell(termsCell);
        doc.add(payment);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 6 — FOOTER
    // ─────────────────────────────────────────────────────────────────────────

    private void addFooter(Document doc, TenantDetails tenant, PdfFont regular) {
        doc.add(new Paragraph().setMarginTop(32));

        // Divider line
        doc.add(new Paragraph()
                .setBorderTop(new SolidBorder(MID_GRAY, 1))
                .setMarginBottom(8));

        doc.add(new Paragraph(
                "This is a computer-generated tax invoice. Valid without a signature. "
                        + "Issued by HandyFlow Business Operating System."
        ).setFont(regular).setFontSize(8).setFontColor(TEXT_GRAY)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(3));

        if (tenant.vatNumber() != null) {
            doc.add(new Paragraph("Supplier VAT Registration: " + tenant.vatNumber())
                    .setFont(regular).setFontSize(8).setFontColor(TEXT_GRAY)
                    .setTextAlignment(TextAlignment.CENTER));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DIVIDER
    // ─────────────────────────────────────────────────────────────────────────

    private void addDivider(Document doc) {
        doc.add(new Paragraph()
                .setBorderTop(new SolidBorder(MID_GRAY, 1))
                .setMarginTop(16)
                .setMarginBottom(0));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CELL / PARAGRAPH HELPERS
    // ─────────────────────────────────────────────────────────────────────────

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
                        .setFont(font)
                        .setFontSize(9)
                        .setFontColor(TEXT_DARK));
    }

    private Cell totLabel(String text, PdfFont font) {
        return new Cell()
                .setBorder(Border.NO_BORDER)
                .setPadding(5)
                .add(new Paragraph(text)
                        .setFont(font).setFontSize(9).setFontColor(TEXT_GRAY));
    }

    private Cell totValue(String text, PdfFont font) {
        return new Cell()
                .setBorder(Border.NO_BORDER)
                .setPadding(5)
                .setTextAlignment(TextAlignment.RIGHT)
                .add(new Paragraph(text)
                        .setFont(font).setFontSize(9).setFontColor(TEXT_DARK));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FORMATTING HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private String fmt(BigDecimal value) {
        if (value == null) return "R 0.00";
        // Format: R 1,234.56
        return "R " + String.format(java.util.Locale.US, "%,.2f",
                value.setScale(2, RoundingMode.HALF_UP));
    }

    private String qty(BigDecimal value) {
        if (value == null) return "0";
        // Strip trailing zeros: 5.00 → 5, 2.50 → 2.5
        return value.stripTrailingZeros().toPlainString();
    }

    private String nvl(String value) {
        return value != null ? value : "";
    }
}
