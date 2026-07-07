package za.co.handyflow.platform.invoicing.application.internal;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.identity.TenantFacade;

import za.co.handyflow.platform.invoicing.domain.model.Invoice;
import za.co.handyflow.platform.invoicing.domain.repository.InvoiceRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import com.itextpdf.layout.element.Div;

/**
 * Generates a standalone payment-receipt PDF, separate from the tax invoice
 * PDF. WHY separate rather than reusing InvoicePdfService? A receipt
 * documents ONE payment event (amount, date, method, reference, running
 * balance) — it is not a restatement of the invoice's line items. Keeping
 * it as its own small, focused class also means this file can be reviewed
 * and changed without touching the higher-stakes tax-invoice generator.
 *
 * Deliberately self-contained (own header/footer rendering) rather than
 * calling private helpers on InvoicePdfService, so it has no hidden
 * coupling to that class's internals.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReceiptPdfService {

    private static final DeviceRgb BRAND_DARK  = new DeviceRgb(15, 76, 92);
    private static final DeviceRgb BRAND_GREEN = new DeviceRgb(16, 122, 87);
    private static final DeviceRgb TEXT_MUTED  = new DeviceRgb(120, 130, 140);
    private static final DeviceRgb BG_LIGHT    = new DeviceRgb(240, 250, 246);

    private final InvoiceRepository invoiceRepository;
    private final TenantFacade tenantFacade;

    public byte[] generateReceiptPdf(UUID invoiceId, TenantId tenantId,
                                     BigDecimal amountPaid, Instant paidAt,
                                     String paymentMethod, String reference) {

        Invoice invoice = invoiceRepository.findActiveByIdWithLineItems(tenantId, invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId.toString()));

        // FIXED: previously declared `TenantDetails tenant` explicitly and
        // guessed its package as za.co.handyflow.platform.identity.dto —
        // that class doesn't exist at that path (or possibly at all under
        // that name). Every other file in this codebase that calls
        // findTenantDetails() avoids this problem entirely by never naming
        // the return type — it's only ever consumed inline via
        // .ifPresent(tenant -> ...) / .map(tenant -> ...), letting type
        // inference handle it. Extracting just the two String values this
        // class actually needs (company name, logo URL) sidesteps the
        // unknown-type problem completely instead of guessing the import
        // a second time.
        String companyName = tenantFacade.findTenantDetails(tenantId)
                .map(t -> t.companyName())
                .orElse("");
        String logoUrl = tenantFacade.findTenantDetails(tenantId)
                .map(t -> t.logoUrl())
                .orElse(null);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos));
            Document document = new Document(pdfDoc, PageSize.A4);
            document.setMargins(36, 40, 36, 40);

            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

            addHeader(document, companyName, logoUrl, bold, regular);
            addReceiptDetails(document, invoice, amountPaid, paidAt,
                    paymentMethod, reference, bold, regular);
            addFooter(document, regular);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate receipt PDF for invoice={}: {}", invoiceId, e.getMessage(), e);
            throw new RuntimeException("Receipt PDF generation failed", e);
        }
    }

    private void addHeader(Document document, String companyName, String logoUrl,
                           PdfFont bold, PdfFont regular) {
        Table headerTable = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth();

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
                log.warn("Could not load logo for receipt: {}", ex.getMessage());
            }
        }
        if (!logoAdded) {
            left.addCell(cellOf(new Paragraph(companyName).setFont(bold).setFontSize(14)));
        } else {
            left.addCell(cellOf(new Paragraph(companyName).setFont(bold).setFontSize(10)
                    .setFontColor(TEXT_MUTED).setMarginTop(4)));
        }

        Paragraph title = new Paragraph("PAYMENT RECEIPT")
                .setFont(bold).setFontSize(22).setFontColor(BRAND_GREEN)
                .setTextAlignment(TextAlignment.RIGHT);

        headerTable.addCell(cellOf(left));
        headerTable.addCell(cellOf(title));
        document.add(headerTable);

        document.add(new com.itextpdf.layout.element.LineSeparator(
                new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(1f))
                .setMarginTop(10).setMarginBottom(14).setFontColor(BRAND_GREEN));
    }

    private void addReceiptDetails(Document document, Invoice invoice,
                                   BigDecimal amountPaid, Instant paidAt,
                                   String paymentMethod, String reference,
                                   PdfFont bold, PdfFont regular) {

        String clientName = invoice.getWalkinClientName() != null
                ? invoice.getWalkinClientName() : "Customer";

        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
        String paidDateStr = paidAt.atZone(ZoneId.systemDefault()).format(dateFmt);

        Table infoTable = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth()
                .setMarginBottom(16);
        infoTable.addCell(cellOf(new Paragraph("Received from: " + clientName).setFont(regular).setFontSize(10)));
        infoTable.addCell(cellOf(new Paragraph("Receipt date: " + paidDateStr).setFont(regular).setFontSize(10)
                .setTextAlignment(TextAlignment.RIGHT)));
        infoTable.addCell(cellOf(new Paragraph("Invoice: " + invoice.getInvoiceNumber()).setFont(bold).setFontSize(10)));
        infoTable.addCell(cellOf(new Paragraph("Payment method: " + nullSafe(paymentMethod))
                .setFont(regular).setFontSize(10).setTextAlignment(TextAlignment.RIGHT)));
        if (reference != null && !reference.isBlank()) {
            infoTable.addCell(cellOf(new Paragraph("").setFontSize(2)));
            infoTable.addCell(cellOf(new Paragraph("Reference: " + reference)
                    .setFont(regular).setFontSize(10).setTextAlignment(TextAlignment.RIGHT)));
        }
        document.add(infoTable);

        BigDecimal invoiceTotal   = invoice.getTotal();
        BigDecimal totalPaidToDate = invoice.getAmountPaid();
        BigDecimal balanceRemaining = invoiceTotal.subtract(totalPaidToDate).max(BigDecimal.ZERO);

        Table amountBox = new Table(1).useAllAvailableWidth()
                .setBackgroundColor(BG_LIGHT).setPadding(14).setMarginBottom(16);
        amountBox.addCell(cellOf(new Paragraph("AMOUNT RECEIVED")
                .setFont(bold).setFontSize(9).setFontColor(BRAND_GREEN)));
        amountBox.addCell(cellOf(new Paragraph(formatZar(amountPaid))
                .setFont(bold).setFontSize(24).setFontColor(BRAND_DARK).setMarginTop(2)));
        document.add(amountBox);

        Table summary = new Table(UnitValue.createPercentArray(new float[]{2, 1})).useAllAvailableWidth();
        addSummaryRow(summary, "Invoice total", formatZar(invoiceTotal), regular, false);
        addSummaryRow(summary, "Total paid to date", formatZar(totalPaidToDate), regular, false);
        addSummaryRow(summary, "Balance remaining", formatZar(balanceRemaining), bold,
                balanceRemaining.compareTo(BigDecimal.ZERO) > 0);
        document.add(summary);
    }

    private void addSummaryRow(Table table, String label, String value, PdfFont font, boolean highlight) {
        table.addCell(cellOf(new Paragraph(label).setFont(font).setFontSize(10)));
        Paragraph valuePara = new Paragraph(value).setFont(font).setFontSize(10)
                .setTextAlignment(TextAlignment.RIGHT);
        if (highlight) valuePara.setFontColor(new DeviceRgb(200, 80, 40));
        table.addCell(cellOf(valuePara));
    }

    private void addFooter(Document document, PdfFont regular) {
        document.add(new Paragraph("This receipt confirms payment received and does not replace your tax invoice.")
                .setFont(regular).setFontSize(8).setFontColor(TEXT_MUTED)
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(30));
    }

    private com.itextpdf.layout.element.Cell cellOf(com.itextpdf.layout.element.IBlockElement content) {
        var cell = new com.itextpdf.layout.element.Cell();
        cell.add(content);
        cell.setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);
        return cell;
    }

    private com.itextpdf.layout.element.Cell cellOf(Image image) {
        Div wrapper = new Div();
        wrapper.add(image);
        return cellOf(wrapper);
    }

    private String formatZar(BigDecimal amount) {
        return "R " + String.format(Locale.US, "%,.2f", amount);
    }

    private String nullSafe(String s) {
        return (s == null || s.isBlank()) ? "Not specified" : s;
    }

    /** Same fix as InvoicePdfService/QuotePdfService — logoUrl is a data: URI. */
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


}