package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.*;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

/**
 * Printable job card for the technician/vendor assigned to a work order, plus a client invoice PDF —
 * OpenPDF, same brand colour and table/footer conventions as every other module's PDF service.
 * <p>
 * A VAT vendor's invoice is titled "Tax Invoice" and shows both parties' name, address and VAT number, a line-by-line
 * description of the services and the VAT charged; credit notes carry the same details plus the original invoice
 * and the reason. Otherwise the document is a plain "Invoice".
 */
@Service
@RequiredArgsConstructor
public class FmPdfService {

    private static final Color BRAND_COLOR = new Color(30, 41, 59);

    private final FmWorkOrderRepository workOrderRepository;
    private final FmSiteRepository siteRepository;
    private final FmAssetRepository assetRepository;
    private final FmClientRepository clientRepository;
    private final FmInvoiceRepository invoiceRepository;
    private final FmProfileRepository profileRepository;
    private final FmCreditNoteRepository creditNoteRepository;
    private final FmServiceAgreementRepository agreementRepository;

    @Transactional(readOnly = true)
    public byte[] generateJobCard(TenantId tenantId, UUID workOrderId) {
        FmWorkOrder wo = workOrderRepository.findActiveById(tenantId, workOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("FmWorkOrder", workOrderId.toString()));
        FmSite site = siteRepository.findActiveById(tenantId, wo.getSiteId()).orElse(null);
        FmAsset asset = wo.getAssetId() != null
                ? assetRepository.findActiveById(tenantId, wo.getAssetId()).orElse(null) : null;
        FmClient client = clientRepository.findActiveById(tenantId, wo.getClientId()).orElse(null);

        try {
            Document document = new Document(PageSize.A4, 40, 40, 50, 50);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD, BRAND_COLOR);
            Font labelFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.DARK_GRAY);
            Font valueFont = new Font(Font.HELVETICA, 11, Font.NORMAL, Color.BLACK);

            document.add(new Paragraph("Work Order Job Card", titleFont));
            document.add(new Paragraph(wo.getWorkOrderNumber(), new Font(Font.HELVETICA, 13, Font.NORMAL, BRAND_COLOR)));
            document.add(Chunk.NEWLINE);

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            addRow(table, "Client", client != null ? client.getTradingName() : "-", labelFont, valueFont);
            addRow(table, "Site", site != null ? site.getName() : "-", labelFont, valueFont);
            addRow(table, "Asset", asset != null ? asset.getName() + (asset.getAssetTag() != null ? " (" + asset.getAssetTag() + ")" : "") : "-", labelFont, valueFont);
            addRow(table, "Category", wo.getCategory(), labelFont, valueFont);
            addRow(table, "Priority", wo.getPriority(), labelFont, valueFont);
            addRow(table, "Status", wo.getStatus(), labelFont, valueFont);
            addRow(table, "Scheduled date", wo.getScheduledDate() != null ? wo.getScheduledDate().toString() : "-", labelFont, valueFont);
            addRow(table, "Assigned to", wo.getTechnicianName() != null ? wo.getTechnicianName()
                    : (wo.getVendorName() != null ? wo.getVendorName() + " (vendor)" : "Unassigned"), labelFont, valueFont);
            addRow(table, "Reported by", wo.getReportedBy() != null ? wo.getReportedBy() : "-", labelFont, valueFont);
            document.add(table);

            document.add(Chunk.NEWLINE);
            document.add(new Paragraph("Description", labelFont));
            document.add(new Paragraph(wo.getDescription(), valueFont));

            if (wo.getCompletionNotes() != null) {
                document.add(Chunk.NEWLINE);
                document.add(new Paragraph("Completion notes", labelFont));
                document.add(new Paragraph(wo.getCompletionNotes(), valueFont));
            }

            document.add(Chunk.NEWLINE);
            document.add(Chunk.NEWLINE);
            document.add(new Paragraph("Technician signature: ______________________________     Date: ______________", valueFont));

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new HandyFlowException("Failed to generate job card PDF", HttpStatus.INTERNAL_SERVER_ERROR, "PDF_GENERATION_FAILED");
        }
    }

    /**
     * Invoice PDF. A VAT vendor's invoice is titled "Tax Invoice" and carries what the VAT Act (s20(4)) requires: the
     * supplier's name, address and VAT number, the recipient's name, address and VAT number (where known), a
     * sequential number, the date, a description of the services with the amounts, and the VAT charged. The parties
     * come from the snapshot taken when the invoice was issued, so reprinting later never changes what was issued;
     * drafts and invoices from before the snapshot existed fall back to the current profile and client.
     */
    @Transactional(readOnly = true)
    public byte[] generateInvoicePdf(TenantId tenantId, UUID invoiceId) {
        FmInvoice invoice = invoiceRepository.findActiveById(tenantId, invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("FmInvoice", invoiceId.toString()));
        FmClient client = clientRepository.findActiveById(tenantId, invoice.getClientId()).orElse(null);
        FmProfile profile = profileRepository.findByTenant(tenantId).orElse(null);

        boolean snapshotted = invoice.getSupplierName() != null || invoice.getClientName() != null;
        String supplierName = snapshotted ? invoice.getSupplierName() : profile != null ? profile.getCompanyName() : null;
        String supplierAddress = snapshotted ? invoice.getSupplierAddress() : profile != null ? profile.getAddress() : null;
        String supplierVat = snapshotted ? invoice.getSupplierVatNumber()
                : (invoice.getVatRate() != null && invoice.getVatRate().signum() > 0 && profile != null ? profile.getVatNumber() : null);
        String clientName = snapshotted ? invoice.getClientName() : client != null ? client.getTradingName() : null;
        String clientAddress = snapshotted ? invoice.getClientAddress() : client != null ? client.getAddress() : null;
        String clientVat = snapshotted ? invoice.getClientVatNumber() : client != null ? client.getVatNumber() : null;

        boolean taxInvoice = supplierVat != null && !supplierVat.isBlank();
        String vatLabel = taxInvoice ? "VAT (" + invoice.getVatRate().stripTrailingZeros().toPlainString() + "%)" : "VAT";
        List<FmWorkOrder> billed = invoice.getAgreementId() == null
                ? workOrderRepository.findByInvoiceId(tenantId, invoiceId) : List.of();
        String retainerName = invoice.getAgreementId() == null ? null
                : agreementRepository.findActiveById(tenantId, invoice.getAgreementId())
                        .map(a -> a.getName() != null ? a.getName() : "Service retainer").orElse("Service retainer");

        try {
            Document document = new Document(PageSize.A4, 40, 40, 50, 50);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 20, Font.BOLD, BRAND_COLOR);
            Font companyFont = new Font(Font.HELVETICA, 13, Font.NORMAL, BRAND_COLOR);
            Font labelFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.DARK_GRAY);
            Font valueFont = new Font(Font.HELVETICA, 11, Font.NORMAL, Color.BLACK);
            Font totalFont = new Font(Font.HELVETICA, 13, Font.BOLD, BRAND_COLOR);

            addParty(document, "From", supplierName != null ? supplierName : "Facilities Management Company",
                    supplierAddress, supplierVat, companyFont, valueFont);
            document.add(new Paragraph(taxInvoice ? "Tax Invoice" : "Invoice", titleFont));
            document.add(new Paragraph(invoice.getInvoiceNumber(), new Font(Font.HELVETICA, 12, Font.NORMAL, Color.DARK_GRAY)));
            document.add(Chunk.NEWLINE);
            addParty(document, "Bill to", clientName != null ? clientName : "-", clientAddress, clientVat, labelFont, valueFont);
            document.add(Chunk.NEWLINE);

            PdfPTable headerTable = new PdfPTable(2);
            headerTable.setWidthPercentage(100);
            addRow(headerTable, "Period", invoice.getPeriodStart() + " to " + invoice.getPeriodEnd(), labelFont, valueFont);
            addRow(headerTable, "Issue date", invoice.getIssueDate().toString(), labelFont, valueFont);
            addRow(headerTable, "Due date", invoice.getDueDate().toString(), labelFont, valueFont);
            addRow(headerTable, "Status", invoice.getStatus(), labelFont, valueFont);
            document.add(headerTable);
            document.add(Chunk.NEWLINE);

            // Description of the services supplied, line by line.
            PdfPTable lineTable = new PdfPTable(new float[]{2f, 6f, 2f});
            lineTable.setWidthPercentage(100);
            addHeaderCell(lineTable, "Reference", labelFont);
            addHeaderCell(lineTable, "Description", labelFont);
            addHeaderCell(lineTable, taxInvoice ? "Amount (excl. VAT)" : "Amount", labelFont);
            if (retainerName != null) {
                addCells(lineTable, "-", retainerName + " — facilities management retainer, " + invoice.getPeriodStart()
                        + " to " + invoice.getPeriodEnd(), invoice.getSubtotal(), valueFont);
            } else if (billed.isEmpty()) {
                addCells(lineTable, "-", "Facilities management services", invoice.getSubtotal(), valueFont);
            } else {
                for (FmWorkOrder wo : billed) {
                    String d = wo.getDescription() == null ? "" : wo.getDescription();
                    addCells(lineTable, wo.getWorkOrderNumber(), d.length() > 120 ? d.substring(0, 117) + "..." : d,
                            wo.getCost(), valueFont);
                }
            }
            document.add(lineTable);
            document.add(Chunk.NEWLINE);

            PdfPTable totalTable = new PdfPTable(2);
            totalTable.setWidthPercentage(100);
            addRow(totalTable, taxInvoice ? "Total excl. VAT" : "Subtotal", "R " + invoice.getSubtotal(), labelFont, valueFont);
            addRow(totalTable, vatLabel, "R " + invoice.getVatAmount(), labelFont, valueFont);
            addRow(totalTable, taxInvoice ? "Total (incl. VAT)" : "Total", "R " + invoice.getTotal(), totalFont, totalFont);
            if (invoice.getCreditedAmount().signum() > 0) {
                addRow(totalTable, "Credit notes", "- R " + invoice.getCreditedAmount(), labelFont, valueFont);
            }
            addRow(totalTable, "Amount paid", "R " + invoice.getAmountPaid(), labelFont, valueFont);
            addRow(totalTable, "Balance due", "R " + invoice.balance(), totalFont, totalFont);
            document.add(totalTable);

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new HandyFlowException("Failed to generate invoice PDF", HttpStatus.INTERNAL_SERVER_ERROR, "PDF_GENERATION_FAILED");
        }
    }

    /** Credit note PDF — a "Tax Credit Note" when the supplier is a VAT vendor; names the original invoice and the reason (VAT Act s21). */
    @Transactional(readOnly = true)
    public byte[] generateCreditNotePdf(TenantId tenantId, UUID creditNoteId) {
        FmCreditNote note = creditNoteRepository.findActiveById(tenantId, creditNoteId)
                .orElseThrow(() -> new ResourceNotFoundException("FmCreditNote", creditNoteId.toString()));
        FmInvoice invoice = invoiceRepository.findActiveById(tenantId, note.getInvoiceId())
                .orElseThrow(() -> new ResourceNotFoundException("FmInvoice", note.getInvoiceId().toString()));
        boolean tax = note.isTaxCreditNote();

        try {
            Document document = new Document(PageSize.A4, 40, 40, 50, 50);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 20, Font.BOLD, BRAND_COLOR);
            Font companyFont = new Font(Font.HELVETICA, 13, Font.NORMAL, BRAND_COLOR);
            Font labelFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.DARK_GRAY);
            Font valueFont = new Font(Font.HELVETICA, 11, Font.NORMAL, Color.BLACK);
            Font totalFont = new Font(Font.HELVETICA, 13, Font.BOLD, BRAND_COLOR);

            addParty(document, "From", note.getSupplierName() != null ? note.getSupplierName() : "Facilities Management Company",
                    note.getSupplierAddress(), note.getSupplierVatNumber(), companyFont, valueFont);
            document.add(new Paragraph(tax ? "Tax Credit Note" : "Credit Note", titleFont));
            document.add(new Paragraph(note.getCreditNoteNumber(), new Font(Font.HELVETICA, 12, Font.NORMAL, Color.DARK_GRAY)));
            document.add(Chunk.NEWLINE);
            addParty(document, "Credited to", note.getClientName() != null ? note.getClientName() : "-",
                    note.getClientAddress(), note.getClientVatNumber(), labelFont, valueFont);
            document.add(Chunk.NEWLINE);

            PdfPTable header = new PdfPTable(2);
            header.setWidthPercentage(100);
            addRow(header, "Date", note.getIssueDate().toString(), labelFont, valueFont);
            addRow(header, "Original invoice", invoice.getInvoiceNumber() + " (" + invoice.getIssueDate() + ")", labelFont, valueFont);
            addRow(header, "Reason", note.getReason(), labelFont, valueFont);
            addRow(header, "Status", note.getStatus(), labelFont, valueFont);
            document.add(header);
            document.add(Chunk.NEWLINE);

            PdfPTable totals = new PdfPTable(2);
            totals.setWidthPercentage(100);
            addRow(totals, tax ? "Credit excl. VAT" : "Credit", "R " + note.getSubtotal(), labelFont, valueFont);
            addRow(totals, tax ? "VAT (" + note.getVatRate().stripTrailingZeros().toPlainString() + "%)" : "VAT",
                    "R " + note.getVatAmount(), labelFont, valueFont);
            addRow(totals, tax ? "Total credit (incl. VAT)" : "Total credit", "R " + note.getTotal(), totalFont, totalFont);
            document.add(totals);

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new HandyFlowException("Failed to generate credit note PDF", HttpStatus.INTERNAL_SERVER_ERROR, "PDF_GENERATION_FAILED");
        }
    }

    private void addParty(Document document, String role, String name, String address, String vatNumber,
                          Font nameFont, Font detailFont) throws DocumentException {
        document.add(new Paragraph(role + ": " + name, nameFont));
        if (address != null && !address.isBlank()) document.add(new Paragraph(address, detailFont));
        if (vatNumber != null && !vatNumber.isBlank()) document.add(new Paragraph("VAT No: " + vatNumber, detailFont));
    }

    private void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setPadding(6);
        table.addCell(cell);
    }

    private void addCells(PdfPTable table, String reference, String description, java.math.BigDecimal amount, Font font) {
        for (String text : new String[]{reference, description, "R " + (amount != null ? amount : java.math.BigDecimal.ZERO)}) {
            PdfPCell cell = new PdfPCell(new Phrase(text, font));
            cell.setBorder(Rectangle.BOTTOM);
            cell.setPadding(6);
            table.addCell(cell);
        }
    }

    private void addRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(Rectangle.BOTTOM);
        labelCell.setPadding(6);
        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(Rectangle.BOTTOM);
        valueCell.setPadding(6);
        table.addCell(labelCell);
        table.addCell(valueCell);
    }
}