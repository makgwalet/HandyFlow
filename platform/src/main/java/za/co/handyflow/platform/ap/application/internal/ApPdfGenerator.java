package za.co.handyflow.platform.ap.application.internal;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.ap.domain.model.ApBatchItem;
import za.co.handyflow.platform.ap.domain.model.ApBill;
import za.co.handyflow.platform.ap.domain.model.ApEftBatch;
import za.co.handyflow.platform.ap.domain.repository.ApBatchItemRepository;
import za.co.handyflow.platform.ap.domain.repository.ApBillRepository;
import za.co.handyflow.platform.ap.domain.repository.ApEftBatchRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * AP module's own PDF generator — batch payment advice (internal
 * sign-off), remittance advice (to a supplier, either for a single bill
 * paid directly or grouped by supplier within a batch), and supplier
 * statements. Built on OpenPDF, matching AccFeeNotePdfGenerator,
 * CreativePdfGenerator, and the recruiter module's RecruiterPdfGenerator —
 * same library, same "own repo injections, no shared PDF service"
 * architecture, not reinvented here.
 * <p>
 * WHY MATCH BY supplierName, NOT supplierId, FOR STATEMENTS AND BATCH
 * REMITTANCE GROUPING? supplierId is nullable on ApBill and frequently
 * absent in practice (confirmed directly against real data this session —
 * multiple real test bills have supplierId: null). supplierName is
 * NOT NULL and always populated. Matching by ID would make these features
 * silently unusable for a large fraction of real bills; matching by name
 * is a real limitation (two different suppliers with the same name would
 * collide) but is the field that's actually reliable today. If a proper
 * Supplier entity with a real ID ever exists, this should be revisited.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApPdfGenerator {

    private static final Color BRAND_NAVY  = new Color(27, 58, 107);
    private static final Color MID_GRAY    = new Color(100, 116, 139);
    private static final Color BORDER_GRAY = new Color(226, 232, 240);
    private static final Color LIGHT_GRAY  = new Color(248, 250, 252);

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter
            .ofPattern("dd MMM yyyy, HH:mm").withZone(SAST);

    private static final Font TITLE_FONT    = new Font(Font.HELVETICA, 16, Font.BOLD, BRAND_NAVY);
    private static final Font META_FONT     = new Font(Font.HELVETICA, 9, Font.NORMAL, MID_GRAY);
    private static final Font BODY_FONT     = new Font(Font.HELVETICA, 10, Font.NORMAL);
    private static final Font BOLD_FONT     = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Font LABEL_FONT    = new Font(Font.HELVETICA, 10, Font.NORMAL, MID_GRAY);
    private static final Font HEADING_FONT  = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Font CELL_FONT     = new Font(Font.HELVETICA, 9, Font.NORMAL);
    private static final Font CELL_MUTED    = new Font(Font.HELVETICA, 9, Font.NORMAL, MID_GRAY);
    private static final Font TOTAL_FONT    = new Font(Font.HELVETICA, 12, Font.BOLD, BRAND_NAVY);

    private final ApBillRepository      billRepo;
    private final ApEftBatchRepository  batchRepo;
    private final ApBatchItemRepository batchItemRepo;
    private final JdbcTemplate          jdbc;

    // SA currency formatting — "R 51 750,00", not raw BigDecimal.toString()
    // ("R 51750.00", no thousands separator, comma/period reversed, and
    // inconsistent decimal places depending on scale). Same known bug
    // already flagged elsewhere in this codebase (the accountant module's
    // paymentReceived()) — matching what AccountsPayablePage.tsx's own
    // fmtR() already does correctly on the frontend via
    // toLocaleString("en-ZA", ...), so these PDFs don't become a third,
    // inconsistent instance of the same problem.
    private static String fmtR(BigDecimal amount) {
        if (amount == null) amount = BigDecimal.ZERO;
        java.text.NumberFormat nf = java.text.NumberFormat.getInstance(new java.util.Locale("en", "ZA"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return "R " + nf.format(amount);
    }

    // ── Batch payment advice — internal sign-off record ─────────────────────

    @Transactional(readOnly = true)
    public byte[] generateBatchAdvice(TenantId tenantId, UUID batchId) {
        ApEftBatch batch = batchRepo.findByIdAndTenantId(batchId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("EFT Batch", batchId.toString()));
        List<ApBill> bills = billsInBatch(batchId);
        String tenantName = fetchTenantName(tenantId);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Document doc = new Document(PageSize.A4, 44, 44, 50, 44);
            PdfWriter.getInstance(doc, out);
            doc.open();

            doc.add(titled(tenantName, "EFT Batch Payment Advice — " + batch.getBatchNumber()));

            PdfPTable meta = new PdfPTable(2);
            meta.setWidthPercentage(100);
            meta.setWidths(new float[]{1, 2});
            addLabelRow(meta, "Status", batch.getStatus());
            addLabelRow(meta, "Payment date", batch.getPaymentDate() != null ? DATE_FMT.format(batch.getPaymentDate()) : "—");
            addLabelRow(meta, "Payment reference", batch.getPaymentRef() != null ? batch.getPaymentRef() : "—");
            addLabelRow(meta, "Bills included", String.valueOf(bills.size()));
            meta.setSpacingAfter(16);
            doc.add(meta);

            PdfPTable table = billTable();
            for (ApBill b : bills) {
                addBillRow(table, b);
            }
            doc.add(table);

            Paragraph total = new Paragraph("Total: " + fmtR(batch.getTotalAmount()), TOTAL_FONT);
            total.setSpacingBefore(10);
            doc.add(total);

            Paragraph footer = new Paragraph(
                    "Generated " + DATETIME_FMT.format(Instant.now()) + " — for internal sign-off records.",
                    META_FONT);
            footer.setSpacingBefore(20);
            doc.add(footer);

            doc.close();
        } catch (DocumentException e) {
            log.error("Batch advice PDF generation failed for batch={}: {}", batchId, e.getMessage());
            throw new IllegalStateException("Failed to generate batch advice", e);
        }

        log.info("Generated batch advice PDF for batch={}", batchId);
        return out.toByteArray();
    }

    // ── Remittance advice — to a supplier ────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] generateBillRemittance(TenantId tenantId, UUID billId) {
        ApBill bill = billRepo.findByIdAndTenantId(billId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill", billId.toString()));
        if (!"PAID".equals(bill.getStatus())) {
            throw new HandyFlowException("Only paid bills have a remittance advice",
                    HttpStatus.BAD_REQUEST, "BILL_NOT_PAID");
        }
        String tenantName = fetchTenantName(tenantId);
        return renderRemittance(tenantName, bill.getSupplierName(), bill.getPaymentRef(),
                bill.getPaidAt(), List.of(bill));
    }

    @Transactional(readOnly = true)
    public byte[] generateBatchRemittance(TenantId tenantId, UUID batchId, String supplierName) {
        ApEftBatch batch = batchRepo.findByIdAndTenantId(batchId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("EFT Batch", batchId.toString()));
        List<ApBill> supplierBills = billsInBatch(batchId).stream()
                .filter(b -> supplierName.equalsIgnoreCase(b.getSupplierName()))
                .toList();
        if (supplierBills.isEmpty()) {
            throw new HandyFlowException(
                    "No bills for supplier '" + supplierName + "' in batch " + batch.getBatchNumber(),
                    HttpStatus.BAD_REQUEST, "SUPPLIER_NOT_IN_BATCH");
        }
        String tenantName = fetchTenantName(tenantId);
        return renderRemittance(tenantName, supplierName, batch.getPaymentRef(),
                batch.getPaidAt(), supplierBills);
    }

    private byte[] renderRemittance(String tenantName, String supplierName, String paymentRef,
                                    Instant paidAt, List<ApBill> bills) {
        BigDecimal total = bills.stream()
                .map(ApBill::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Document doc = new Document(PageSize.A4, 54, 54, 60, 54);
            PdfWriter.getInstance(doc, out);
            doc.open();

            doc.add(titled(tenantName, "Remittance Advice"));

            Paragraph to = new Paragraph("To: " + supplierName, BOLD_FONT);
            to.setSpacingAfter(10);
            doc.add(to);

            PdfPTable meta = new PdfPTable(2);
            meta.setWidthPercentage(100);
            meta.setWidths(new float[]{1, 2});
            addLabelRow(meta, "Payment date", paidAt != null ? DATETIME_FMT.format(paidAt) : "—");
            addLabelRow(meta, "Payment reference", paymentRef != null ? paymentRef : "—");
            meta.setSpacingAfter(16);
            doc.add(meta);

            PdfPTable table = billTable();
            for (ApBill b : bills) {
                addBillRow(table, b);
            }
            doc.add(table);

            Paragraph total_ = new Paragraph("Total paid: " + fmtR(total), TOTAL_FONT);
            total_.setSpacingBefore(10);
            doc.add(total_);

            Paragraph note = new Paragraph(
                    "This advice confirms payment has been made for the item(s) listed above.",
                    META_FONT);
            note.setSpacingBefore(20);
            doc.add(note);

            doc.close();
        } catch (DocumentException e) {
            log.error("Remittance PDF generation failed for supplier={}: {}", supplierName, e.getMessage());
            throw new IllegalStateException("Failed to generate remittance advice", e);
        }

        log.info("Generated remittance advice for supplier={} bills={}", supplierName, bills.size());
        return out.toByteArray();
    }

    // ── Supplier statement ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] generateSupplierStatement(TenantId tenantId, String supplierName) {
        List<ApBill> bills = billRepo.findAll(tenantId, null, Pageable.unpaged()).getContent().stream()
                .filter(b -> supplierName.equalsIgnoreCase(b.getSupplierName()))
                .sorted(Comparator.comparing(ApBill::getBillDate))
                .toList();
        if (bills.isEmpty()) {
            throw new ResourceNotFoundException("Supplier", supplierName);
        }

        BigDecimal outstanding = bills.stream()
                .filter(b -> "APPROVED".equals(b.getStatus()) || "OVERDUE".equals(b.getStatus()))
                .map(ApBill::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String tenantName = fetchTenantName(tenantId);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Document doc = new Document(PageSize.A4, 44, 44, 50, 44);
            PdfWriter.getInstance(doc, out);
            doc.open();

            doc.add(titled(tenantName, "Supplier Statement — " + supplierName));

            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1.3f, 1.1f, 1.1f, 1f, 1f});
            for (String h : List.of("Bill #", "Bill date", "Due date", "Amount", "Status")) {
                PdfPCell header = new PdfPCell(new Phrase(h, HEADING_FONT));
                header.setBackgroundColor(BRAND_NAVY);
                header.setBorderColor(BRAND_NAVY);
                header.setPadding(6);
                table.addCell(header);
            }
            for (ApBill b : bills) {
                addCell(table, b.getBillNumber());
                addCell(table, DATE_FMT.format(b.getBillDate()));
                addCell(table, DATE_FMT.format(b.getDueDate()));
                addCell(table, fmtR(b.getTotalAmount()));
                addCell(table, b.getStatus());
            }
            doc.add(table);

            Paragraph outstandingP = new Paragraph("Outstanding balance: " + fmtR(outstanding), TOTAL_FONT);
            outstandingP.setSpacingBefore(14);
            doc.add(outstandingP);

            Paragraph asAt = new Paragraph(
                    "As at " + DATE_FMT.format(java.time.LocalDate.now()) + ". Outstanding balance includes APPROVED and OVERDUE bills only.",
                    META_FONT);
            asAt.setSpacingBefore(6);
            doc.add(asAt);

            doc.close();
        } catch (DocumentException e) {
            log.error("Supplier statement PDF generation failed for supplier={}: {}", supplierName, e.getMessage());
            throw new IllegalStateException("Failed to generate supplier statement", e);
        }

        log.info("Generated supplier statement for supplier={} bills={}", supplierName, bills.size());
        return out.toByteArray();
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private List<ApBill> billsInBatch(UUID batchId) {
        return batchItemRepo.findByBatchId(batchId).stream()
                .map((ApBatchItem item) -> billRepo.findById(item.getBillId()).orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    private Paragraph titled(String tenantName, String subtitle) {
        // Caller adds this single paragraph containing both lines — kept
        // as one method since every document in this class opens the
        // same way (tenant name, then a subtitle describing the document).
        Paragraph p = new Paragraph();
        p.add(new Chunk(tenantName + "\n", TITLE_FONT));
        p.add(new Chunk(subtitle, META_FONT));
        p.setSpacingAfter(20);
        return p;
    }

    private PdfPTable billTable() {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.3f, 1.1f, 2.2f, 1f});
        for (String h : List.of("Bill #", "Bill date", "Description", "Amount")) {
            PdfPCell header = new PdfPCell(new Phrase(h, HEADING_FONT));
            header.setBackgroundColor(BRAND_NAVY);
            header.setBorderColor(BRAND_NAVY);
            header.setPadding(6);
            table.addCell(header);
        }
        return table;
    }

    private void addBillRow(PdfPTable table, ApBill b) {
        addCell(table, b.getBillNumber());
        addCell(table, DATE_FMT.format(b.getBillDate()));
        addCell(table, b.getDescription());
        addCell(table, fmtR(b.getTotalAmount()));
    }

    private void addCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "—", CELL_FONT));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(BORDER_GRAY);
        cell.setPadding(6);
        table.addCell(cell);
    }

    private void addLabelRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, LABEL_FONT));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPaddingBottom(5);
        table.addCell(labelCell);
        PdfPCell valueCell = new PdfPCell(new Phrase(value, BODY_FONT));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPaddingBottom(5);
        table.addCell(valueCell);
    }

    // Deliberate duplicate of the identically-named helper already used in
    // the recruiter module's RecruiterPdfGenerator — see that class's own
    // Javadoc for why this is a small, deliberate duplication rather than
    // a cross-module dependency.
    private String fetchTenantName(TenantId tenantId) {
        try {
            return jdbc.queryForObject(
                    "SELECT name FROM tenants WHERE id = ?",
                    String.class, tenantId.getValue());
        } catch (Exception e) { return "HandyFlow"; }
    }
}