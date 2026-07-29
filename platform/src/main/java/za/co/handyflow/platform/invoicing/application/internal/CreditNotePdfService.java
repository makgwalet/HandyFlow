package za.co.handyflow.platform.invoicing.application.internal;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Div;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.invoicing.domain.model.CreditNote;
import za.co.handyflow.platform.invoicing.domain.model.Invoice;
import za.co.handyflow.platform.invoicing.domain.repository.CreditNoteRepository;
import za.co.handyflow.platform.invoicing.domain.repository.InvoiceRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Generates a standalone credit note PDF, separate from the tax invoice PDF
 * — same rationale ReceiptPdfService already gives for receipts: a credit
 * note documents one correction/refund event against an invoice, it is not
 * a restatement of the invoice's line items. Self-contained (own header/
 * footer) rather than sharing private helpers with InvoicePdfService, for
 * the same "no hidden coupling to a higher-stakes generator" reason.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreditNotePdfService {

    private static final DeviceRgb BRAND_DARK = new DeviceRgb(15, 76, 92);
    private static final DeviceRgb BRAND_RED  = new DeviceRgb(180, 60, 50);
    private static final DeviceRgb TEXT_MUTED = new DeviceRgb(120, 130, 140);
    private static final DeviceRgb BG_LIGHT   = new DeviceRgb(253, 242, 240);

    private final CreditNoteRepository creditNoteRepo;
    private final InvoiceRepository invoiceRepo;
    private final TenantFacade tenantFacade;
    private final CrmFacade crmFacade;

    public byte[] generateCreditNotePdf(UUID creditNoteId, TenantId tenantId) {
        CreditNote creditNote = creditNoteRepo.findActiveById(tenantId, creditNoteId)
                .orElseThrow(() -> new ResourceNotFoundException("CreditNote", creditNoteId.toString()));
        Invoice invoice = invoiceRepo.findActiveByIdWithLineItems(tenantId, creditNote.getInvoiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", creditNote.getInvoiceId().toString()));

        String companyName = tenantFacade.findTenantDetails(tenantId).map(t -> t.companyName()).orElse("");
        String logoUrl = tenantFacade.findTenantDetails(tenantId).map(t -> t.logoUrl()).orElse(null);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos));
            Document document = new Document(pdfDoc, PageSize.A4);
            document.setMargins(36, 40, 36, 40);

            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

            addHeader(document, companyName, logoUrl, bold, regular);
            addCreditNoteDetails(document, creditNote, invoice, tenantId, bold, regular);
            addFooter(document, regular);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate credit note PDF for credit note={}: {}", creditNoteId, e.getMessage(), e);
            throw new RuntimeException("Credit note PDF generation failed", e);
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
                log.warn("Could not load logo for credit note: {}", ex.getMessage());
            }
        }
        if (!logoAdded) {
            left.addCell(cellOf(new Paragraph(companyName).setFont(bold).setFontSize(14)));
        } else {
            left.addCell(cellOf(new Paragraph(companyName).setFont(bold).setFontSize(10)
                    .setFontColor(TEXT_MUTED).setMarginTop(4)));
        }

        Paragraph title = new Paragraph("CREDIT NOTE")
                .setFont(bold).setFontSize(22).setFontColor(BRAND_RED)
                .setTextAlignment(TextAlignment.RIGHT);

        headerTable.addCell(cellOf(left));
        headerTable.addCell(cellOf(title));
        document.add(headerTable);

        document.add(new com.itextpdf.layout.element.LineSeparator(
                new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(1f))
                .setMarginTop(10).setMarginBottom(14).setFontColor(BRAND_RED));
    }

    private void addCreditNoteDetails(Document document, CreditNote creditNote, Invoice invoice, TenantId tenantId,
                                      PdfFont bold, PdfFont regular) {
        // FIX: this previously only checked getWalkinClientName(), falling
        // through to a hardcoded "Customer" for any invoice billed to a
        // real customer (not a walk-in) — confirmed via real testing
        // (PDF said "Issued to: Customer" while the email, which already
        // resolved this correctly via CrmFacade in CreditNoteService, said
        // "Dear Black Flamingo"). Same resolution logic as
        // CreditNoteService.resolveClientName, now actually used here too.
        String clientName = resolveClientName(invoice, tenantId);

        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
        String issuedDateStr = creditNote.getIssuedAt().atZone(ZoneId.systemDefault()).format(dateFmt);

        Table infoTable = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth()
                .setMarginBottom(16);
        infoTable.addCell(cellOf(new Paragraph("Issued to: " + clientName).setFont(regular).setFontSize(10)));
        infoTable.addCell(cellOf(new Paragraph("Credit note date: " + issuedDateStr).setFont(regular).setFontSize(10)
                .setTextAlignment(TextAlignment.RIGHT)));
        infoTable.addCell(cellOf(new Paragraph("Credit note: " + creditNote.getCreditNoteNumber()).setFont(bold).setFontSize(10)));
        infoTable.addCell(cellOf(new Paragraph("Against invoice: " + invoice.getInvoiceNumber())
                .setFont(regular).setFontSize(10).setTextAlignment(TextAlignment.RIGHT)));
        document.add(infoTable);

        if (creditNote.getReason() != null && !creditNote.getReason().isBlank()) {
            document.add(new Paragraph("Reason: " + creditNote.getReason())
                    .setFont(regular).setFontSize(10).setMarginBottom(4));
        }
        if (creditNote.getDescription() != null && !creditNote.getDescription().isBlank()) {
            document.add(new Paragraph(creditNote.getDescription())
                    .setFont(regular).setFontSize(10).setFontColor(TEXT_MUTED).setMarginBottom(16));
        }

        Table amountBox = new Table(1).useAllAvailableWidth()
                .setBackgroundColor(BG_LIGHT).setPadding(14).setMarginBottom(16).setMarginTop(10);
        amountBox.addCell(cellOf(new Paragraph("CREDIT AMOUNT")
                .setFont(bold).setFontSize(9).setFontColor(BRAND_RED)));
        amountBox.addCell(cellOf(new Paragraph(formatZar(creditNote.getTotal()))
                .setFont(bold).setFontSize(24).setFontColor(BRAND_DARK).setMarginTop(2)));
        document.add(amountBox);

        Table summary = new Table(UnitValue.createPercentArray(new float[]{2, 1})).useAllAvailableWidth();
        addSummaryRow(summary, "Subtotal (excl. VAT)", formatZar(creditNote.getSubtotal()), regular);
        addSummaryRow(summary, "VAT", formatZar(creditNote.getVatTotal()), regular);
        addSummaryRow(summary, "Total credited", formatZar(creditNote.getTotal()), bold);
        document.add(summary);
    }

    private void addSummaryRow(Table table, String label, String value, PdfFont font) {
        table.addCell(cellOf(new Paragraph(label).setFont(font).setFontSize(10)));
        table.addCell(cellOf(new Paragraph(value).setFont(font).setFontSize(10).setTextAlignment(TextAlignment.RIGHT)));
    }

    private void addFooter(Document document, PdfFont regular) {
        document.add(new Paragraph("This credit note reduces the amount owed on the referenced invoice. It does not itself constitute a cash refund.")
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

    /** Same resolution pattern as CreditNoteService/InvoiceService/QuoteService. */
    private String resolveClientName(Invoice invoice, TenantId tenantId) {
        if (invoice.getCustomerId() != null) {
            return crmFacade.findCustomerById(tenantId, invoice.getCustomerId())
                    .map(c -> c.name()).orElse("Customer");
        }
        return invoice.getWalkinClientName() != null ? invoice.getWalkinClientName() : "Client";
    }

    /** Same fix as InvoicePdfService/QuotePdfService/ReceiptPdfService — logoUrl is a data: URI. */
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