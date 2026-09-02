package za.co.handyflow.platform.bookkeeping.application.internal;

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
import za.co.handyflow.platform.bookkeeping.domain.model.*;
import za.co.handyflow.platform.bookkeeping.domain.repository.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client invoice PDF plus a simple per-client, per-period trial balance
 * PDF — OpenPDF, same brand colour and table/footer conventions used by
 * every other module's PDF service in this codebase (mirrors {@code
 * FmPdfService} exactly for the invoice).
 */
@Service
@RequiredArgsConstructor
public class BkPdfService {

    private static final Color BRAND_COLOR = new Color(30, 41, 59);

    private final BkInvoiceRepository invoiceRepository;
    private final BkClientRepository clientRepository;
    private final BkProfileRepository profileRepository;
    private final BkPeriodRepository periodRepository;
    private final BkJournalEntryRepository journalEntryRepository;
    private final BkAccountRepository accountRepository;

    @Transactional(readOnly = true)
    public byte[] generateInvoicePdf(TenantId tenantId, UUID invoiceId) {
        BkInvoice invoice = invoiceRepository.findActiveById(tenantId, invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("BkInvoice", invoiceId.toString()));
        BkClient client = clientRepository.findActiveById(tenantId, invoice.getClientId()).orElse(null);
        BkProfile profile = profileRepository.findByTenant(tenantId).orElse(null);

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

            document.add(new Paragraph(profile != null ? profile.getPracticeName() : "Bookkeeping Practice", companyFont));
            document.add(new Paragraph("Invoice", titleFont));
            document.add(new Paragraph(invoice.getInvoiceNumber(), new Font(Font.HELVETICA, 12, Font.NORMAL, Color.DARK_GRAY)));
            document.add(Chunk.NEWLINE);

            PdfPTable headerTable = new PdfPTable(2);
            headerTable.setWidthPercentage(100);
            addRow(headerTable, "Bill to", client != null ? client.getTradingName() : "-", labelFont, valueFont);
            addRow(headerTable, "Period", invoice.getPeriodStart() + " to " + invoice.getPeriodEnd(), labelFont, valueFont);
            addRow(headerTable, "Issue date", invoice.getIssueDate().toString(), labelFont, valueFont);
            addRow(headerTable, "Due date", invoice.getDueDate().toString(), labelFont, valueFont);
            addRow(headerTable, "Status", invoice.getStatus(), labelFont, valueFont);
            document.add(headerTable);
            document.add(Chunk.NEWLINE);

            PdfPTable lineTable = new PdfPTable(2);
            lineTable.setWidthPercentage(100);
            addRow(lineTable, "Bookkeeping services", "R " + invoice.getSubtotal(), labelFont, valueFont);
            addRow(lineTable, "VAT", "R " + invoice.getVatAmount(), labelFont, valueFont);
            document.add(lineTable);
            document.add(Chunk.NEWLINE);

            PdfPTable totalTable = new PdfPTable(2);
            totalTable.setWidthPercentage(100);
            addRow(totalTable, "Total", "R " + invoice.getTotal(), totalFont, totalFont);
            addRow(totalTable, "Amount paid", "R " + invoice.getAmountPaid(), labelFont, valueFont);
            addRow(totalTable, "Balance due", "R " + invoice.balance(), totalFont, totalFont);
            document.add(totalTable);

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new HandyFlowException("Failed to generate invoice PDF", HttpStatus.INTERNAL_SERVER_ERROR, "PDF_GENERATION_FAILED");
        }
    }

    /**
     * A simple trial balance for one client/period — sums debit and
     * credit across every POSTED journal entry in that period, grouped by
     * account, one row per account that moved.
     */
    @Transactional(readOnly = true)
    public byte[] generateTrialBalancePdf(TenantId tenantId, UUID clientId, UUID periodId) {
        BkClient client = clientRepository.findActiveById(tenantId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("BkClient", clientId.toString()));
        BkPeriod period = periodRepository.findActiveById(tenantId, periodId)
                .filter(p -> p.getClientId().equals(clientId))
                .orElseThrow(() -> new ResourceNotFoundException("BkPeriod", periodId.toString()));

        Map<UUID, BigDecimal[]> totalsByAccount = new LinkedHashMap<>(); // [debit, credit]
        for (BkJournalEntry entry : journalEntryRepository.findAllForPeriod(tenantId, periodId)) {
            if (!"POSTED".equals(entry.getStatus())) continue;
            for (BkJournalLine line : entry.getLines()) {
                BigDecimal[] totals = totalsByAccount.computeIfAbsent(line.getAccountId(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                totals[0] = totals[0].add(line.getDebitAmount());
                totals[1] = totals[1].add(line.getCreditAmount());
            }
        }

        try {
            Document document = new Document(PageSize.A4, 40, 40, 50, 50);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD, BRAND_COLOR);
            Font labelFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
            Font valueFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
            Font totalFont = new Font(Font.HELVETICA, 11, Font.BOLD, BRAND_COLOR);

            document.add(new Paragraph("Trial Balance", titleFont));
            document.add(new Paragraph(client.getTradingName() + " — " + period.getPeriodYear() + "/" + period.getPeriodMonth(),
                    new Font(Font.HELVETICA, 12, Font.NORMAL, Color.DARK_GRAY)));
            document.add(Chunk.NEWLINE);

            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1.5f, 4f, 2f, 2f});
            addHeaderCell(table, "Code", labelFont);
            addHeaderCell(table, "Account", labelFont);
            addHeaderCell(table, "Debit", labelFont);
            addHeaderCell(table, "Credit", labelFont);

            BigDecimal totalDebit = BigDecimal.ZERO, totalCredit = BigDecimal.ZERO;
            for (Map.Entry<UUID, BigDecimal[]> row : totalsByAccount.entrySet()) {
                BkAccount account = accountRepository.findActiveById(tenantId, row.getKey()).orElse(null);
                table.addCell(new PdfPCell(new Phrase(account != null ? account.getAccountCode() : "-", valueFont)));
                table.addCell(new PdfPCell(new Phrase(account != null ? account.getAccountName() : "Unknown account", valueFont)));
                table.addCell(new PdfPCell(new Phrase(row.getValue()[0].toString(), valueFont)));
                table.addCell(new PdfPCell(new Phrase(row.getValue()[1].toString(), valueFont)));
                totalDebit = totalDebit.add(row.getValue()[0]);
                totalCredit = totalCredit.add(row.getValue()[1]);
            }
            document.add(table);
            document.add(Chunk.NEWLINE);

            PdfPTable totalsTable = new PdfPTable(2);
            totalsTable.setWidthPercentage(50);
            totalsTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
            addRow(totalsTable, "Total debit", totalDebit.toString(), totalFont, totalFont);
            addRow(totalsTable, "Total credit", totalCredit.toString(), totalFont, totalFont);
            document.add(totalsTable);

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new HandyFlowException("Failed to generate trial balance PDF", HttpStatus.INTERNAL_SERVER_ERROR, "PDF_GENERATION_FAILED");
        }
    }

    private void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(BRAND_COLOR);
        cell.setPadding(6);
        table.addCell(cell);
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
