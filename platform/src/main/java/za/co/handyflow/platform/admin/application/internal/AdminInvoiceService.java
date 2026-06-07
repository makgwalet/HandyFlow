package za.co.handyflow.platform.admin.application.internal;

import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.admin.domain.model.AdminAuditLog;
import za.co.handyflow.platform.admin.domain.repository.AdminAuditLogRepository;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * Phase 4 — Admin invoicing to tenants.
 *
 * Responsibilities:
 *  - Generate a structured tax invoice for any tenant for any billing period.
 *  - Render a professional PDF using iText (same library as PayslipPdfGenerator).
 *  - Send the invoice by email via EmailService.
 *  - Persist every invoice to admin_tenant_invoices for history and resend.
 *  - Mark invoices as PAID (resolves PAST_DUE subscription status).
 *  - Auto-generate invoices for all ACTIVE tenants on the 1st of each month.
 *
 * WHY JDBC for reads? admin_tenant_invoices is an admin-only table not
 * mapped to any tenant-scoped JPA repository. JDBC avoids tenant context leakage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminInvoiceService {

    private final JdbcTemplate              jdbc;
    private final EmailService              emailService;
    private final AdminAuditLogRepository   auditRepo;
    private final AdminNotificationService  notificationService;

    private static final BigDecimal VAT_RATE   = new BigDecimal("0.15");
    private static final DeviceRgb  NAVY       = new DeviceRgb(27,  58,  107);
    private static final DeviceRgb  TEAL       = new DeviceRgb(13,  148, 136);
    private static final DeviceRgb  LIGHT_GRAY = new DeviceRgb(248, 250, 252);
    private static final DeviceRgb  MID_GRAY   = new DeviceRgb(100, 116, 139);
    private static final DeviceRgb  DARK       = new DeviceRgb(15,  23,  42);
    private static final NumberFormat ZAR;

    static {
        ZAR = NumberFormat.getInstance(new Locale("en", "ZA"));
        ZAR.setMinimumFractionDigits(2);
        ZAR.setMaximumFractionDigits(2);
    }

    // ── Generate invoice ──────────────────────────────────────────────────────

    /**
     * Generate a DRAFT invoice for a tenant for the given period.
     * Does NOT send email — call sendInvoice() after review.
     */
    @Transactional
    public Map<String, Object> generateInvoice(String tenantSlug, int year, int month,
                                               UUID adminId, String adminEmail) {
        // Resolve tenant
        Map<String, Object> tenant = resolveTenant(tenantSlug);
        UUID   tenantId    = (UUID)   tenant.get("id");
        String tenantName  = (String) tenant.get("name");
        String tenantEmail = (String) tenant.get("email");

        // Collect active/trial modules for this tenant
        List<Map<String, Object>> modules = jdbc.queryForList("""
            SELECT tm.module_key, tm.status, tm.trial_ends_at,
                   mc.name, mc.monthly_price
            FROM tenant_modules tm
            JOIN module_catalogue mc ON mc.key = tm.module_key
            WHERE tm.tenant_id = ?
              AND tm.status IN ('ACTIVE', 'TRIAL')
            ORDER BY mc.sort_order
            """, tenantId);

        if (modules.isEmpty()) throw new HandyFlowException(
                "No active modules for tenant: " + tenantSlug,
                HttpStatus.BAD_REQUEST, "NO_MODULES");

        // Calculate amounts — TRIAL modules billed at R0 (still listed for transparency)
        BigDecimal subtotal = modules.stream()
                .filter(m -> "ACTIVE".equals(m.get("status")))
                .map(m -> (BigDecimal) m.get("monthly_price"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal vat   = subtotal.multiply(VAT_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(vat);

        // Invoice number: HF-INV-2026-0001
        String invoiceNumber = nextInvoiceNumber();
        String periodLabel   = YearMonth.of(year, month)
                .format(DateTimeFormatter.ofPattern("MMMM yyyy"));
        LocalDate dueDate    = LocalDate.of(year, month, 1).plusDays(30);

        // Build line items JSON
        StringBuilder lineItems = new StringBuilder("[");
        for (int i = 0; i < modules.size(); i++) {
            Map<String, Object> m = modules.get(i);
            boolean isActive = "ACTIVE".equals(m.get("status"));
            BigDecimal price = isActive ? (BigDecimal) m.get("monthly_price") : BigDecimal.ZERO;
            lineItems.append(String.format(
                    "{\"moduleKey\":\"%s\",\"name\":\"%s\",\"status\":\"%s\",\"price\":%.2f}",
                    m.get("module_key"), m.get("name"), m.get("status"), price));
            if (i < modules.size() - 1) lineItems.append(",");
        }
        lineItems.append("]");

        // Generate PDF
        byte[] pdf = generatePdf(invoiceNumber, tenantName, tenantEmail,
                periodLabel, dueDate, modules, subtotal, vat, total);
        String pdfBase64 = Base64.getEncoder().encodeToString(pdf);

        // Persist
        UUID invoiceId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO admin_tenant_invoices
            (id, invoice_number, tenant_id, tenant_name, tenant_email,
             period_year, period_month, period_label,
             subtotal, vat_amount, total, vat_rate,
             line_items, status, due_date, pdf_base64,
             generated_by, generated_by_email, created_at, updated_at)
            VALUES (?,?,?,?,?, ?,?,?, ?,?,?,?, ?::jsonb,'DRAFT',?,?, ?,?,NOW(),NOW())
            """,
                invoiceId, invoiceNumber, tenantId, tenantName, tenantEmail,
                year, month, periodLabel,
                subtotal, vat, total, VAT_RATE,
                lineItems.toString(), dueDate, pdfBase64,
                adminId, adminEmail);

        audit(adminId, adminEmail, "GENERATE_INVOICE", "INVOICE",
                invoiceId.toString(), invoiceNumber,
                "{\"tenant\":\"" + tenantSlug + "\",\"period\":\"" + periodLabel + "\",\"total\":" + total + "}");

        log.info("Generated invoice {} for {} period={} total=R{}", invoiceNumber, tenantSlug, periodLabel, total);
        return getInvoice(invoiceId);
    }

    /**
     * Send a DRAFT invoice by email. Moves status to SENT.
     */
    @Transactional
    public void sendInvoice(UUID invoiceId, UUID adminId, String adminEmail) {
        Map<String, Object> inv = jdbc.queryForMap(
                "SELECT * FROM admin_tenant_invoices WHERE id = ?", invoiceId);

        String status = (String) inv.get("status");
        if ("VOID".equals(status)) throw new HandyFlowException(
                "Cannot send a voided invoice", HttpStatus.BAD_REQUEST, "INVOICE_VOIDED");

        String email        = (String) inv.get("tenant_email");
        String tenantName   = (String) inv.get("tenant_name");
        String invoiceNum   = (String) inv.get("invoice_number");
        String periodLabel  = (String) inv.get("period_label");
        BigDecimal total    = (BigDecimal) inv.get("total");

        // Send email (PDF is large — link to portal rather than attach for now)
        String html = tenantInvoiceEmail(tenantName, invoiceNum, periodLabel,
                "R " + ZAR.format(total));
        emailService.send(email,
                "HandyFlow Invoice " + invoiceNum + " — " + periodLabel, html);

        // Update status
        jdbc.update("""
            UPDATE admin_tenant_invoices
            SET status = 'SENT', sent_at = NOW(), updated_at = NOW()
            WHERE id = ?
            """, invoiceId);

        audit(adminId, adminEmail, "SEND_INVOICE", "INVOICE",
                invoiceId.toString(), invoiceNum,
                "{\"sentTo\":\"" + email + "\"}");

        log.info("Sent invoice {} to {} for period {}", invoiceNum, email, periodLabel);
    }

    /**
     * Mark invoice as PAID. If subscription was PAST_DUE, restores it to ACTIVE.
     */
    @Transactional
    public void markPaid(UUID invoiceId, UUID adminId, String adminEmail) {
        Map<String, Object> inv = jdbc.queryForMap(
                "SELECT * FROM admin_tenant_invoices WHERE id = ?", invoiceId);

        String invoiceNum = (String) inv.get("invoice_number");
        UUID   tenantId   = (UUID)   inv.get("tenant_id");

        // Fetch tenant name + total for the notification (best-effort)
        String tenantName = fetchTenantName(tenantId);
        String tenantSlug = fetchTenantSlug(tenantId);
        Object totalRaw   = inv.get("total");
        String amountStr  = totalRaw != null
            ? "R " + new java.math.BigDecimal(totalRaw.toString())
                  .setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
            : "unknown";

        jdbc.update("""
            UPDATE admin_tenant_invoices
            SET status = 'PAID', paid_at = NOW(), updated_at = NOW()
            WHERE id = ?
            """, invoiceId);

        // Restore subscription if it was PAST_DUE
        int restored = jdbc.update("""
            UPDATE subscriptions
            SET status = 'ACTIVE', past_due_since = NULL, updated_at = NOW()
            WHERE tenant_id = ? AND status = 'PAST_DUE'
            """, tenantId);

        if (restored > 0) {
            // Also restore tenants.status
            jdbc.update("""
                UPDATE tenants SET status = 'ACTIVE', updated_at = NOW()
                WHERE id = ? AND status IN ('PAST_DUE','SUSPENDED')
                """, tenantId);
            log.info("Restored tenant {} from PAST_DUE to ACTIVE after payment", tenantId);
        }

        audit(adminId, adminEmail, "MARK_INVOICE_PAID", "INVOICE",
                invoiceId.toString(), invoiceNum, null);

        notificationService.notifyInvoicePaid(tenantId, tenantName, tenantSlug, invoiceNum, amountStr);
        log.info("Invoice {} marked PAID", invoiceNum);
    }

    /**
     * Void an invoice — cannot be undone.
     */
    @Transactional
    public void voidInvoice(UUID invoiceId, UUID adminId, String adminEmail) {
        Map<String, Object> inv = jdbc.queryForMap(
                "SELECT invoice_number, status FROM admin_tenant_invoices WHERE id = ?", invoiceId);
        if ("PAID".equals(inv.get("status"))) throw new HandyFlowException(
                "Cannot void a PAID invoice", HttpStatus.BAD_REQUEST, "ALREADY_PAID");

        jdbc.update("""
            UPDATE admin_tenant_invoices
            SET status = 'VOID', updated_at = NOW()
            WHERE id = ?
            """, invoiceId);

        audit(adminId, adminEmail, "VOID_INVOICE", "INVOICE",
                invoiceId.toString(), (String) inv.get("invoice_number"), null);
    }

    // ── Query methods ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getInvoices(String tenantSlug, String status,
                                                 int page, int size) {
        StringBuilder sql = new StringBuilder("""
            SELECT i.id, i.invoice_number, i.tenant_name, i.tenant_email,
                   t.slug AS tenant_slug,
                   i.period_label, i.subtotal, i.vat_amount, i.total,
                   i.status, i.sent_at, i.paid_at, i.due_date,
                   i.generated_by_email, i.created_at
            FROM admin_tenant_invoices i
            JOIN tenants t ON t.id = i.tenant_id
            WHERE 1=1
            """);
        List<Object> params = new ArrayList<>();

        if (tenantSlug != null && !tenantSlug.isBlank()) {
            sql.append(" AND t.slug = ?");
            params.add(tenantSlug);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND i.status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY i.created_at DESC LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);

        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getInvoice(UUID id) {
        return jdbc.queryForMap("""
            SELECT i.*, t.slug AS tenant_slug
            FROM admin_tenant_invoices i
            JOIN tenants t ON t.id = i.tenant_id
            WHERE i.id = ?
            """, id);
    }

    @Transactional(readOnly = true)
    public byte[] getInvoicePdf(UUID id) {
        String base64 = jdbc.queryForObject(
                "SELECT pdf_base64 FROM admin_tenant_invoices WHERE id = ?",
                String.class, id);
        if (base64 == null) throw new HandyFlowException(
                "PDF not found for invoice " + id, HttpStatus.NOT_FOUND, "NOT_FOUND");
        return Base64.getDecoder().decode(base64);
    }

    // ── Automated monthly billing ─────────────────────────────────────────────

    /**
     * Runs at 06:00 SAST on the 1st of every month.
     * Generates invoices for all ACTIVE tenants with at least one active module.
     * Marks them SENT immediately (auto-invoicing).
     */
    @Scheduled(cron = "0 0 6 1 * *", zone = "Africa/Johannesburg")
    public void runMonthlyBilling() {
        LocalDate now       = LocalDate.now();
        int       year      = now.getYear();
        int       month     = now.getMonthValue();
        String    period    = YearMonth.of(year, month)
                .format(DateTimeFormatter.ofPattern("MMMM yyyy"));

        log.info("[BILLING-SCHEDULER] Starting monthly billing run for {}", period);

        List<Map<String, Object>> tenants = jdbc.queryForList("""
            SELECT DISTINCT t.slug
            FROM tenants t
            JOIN subscriptions s ON s.tenant_id = t.id
            JOIN tenant_modules tm ON tm.tenant_id = t.id AND tm.status = 'ACTIVE'
            WHERE s.status = 'ACTIVE'
            """);

        int generated = 0, failed = 0;
        for (Map<String, Object> t : tenants) {
            String slug = (String) t.get("slug");
            try {
                // Use system admin UUID for scheduled runs
                UUID sysAdmin = UUID.fromString("00000000-0000-0000-0000-000000000001");
                Map<String, Object> inv = generateInvoice(slug, year, month,
                        sysAdmin, "billing-scheduler@handyflow.co.za");
                UUID invoiceId = (UUID) inv.get("id");
                sendInvoice(invoiceId, sysAdmin, "billing-scheduler@handyflow.co.za");
                generated++;
            } catch (Exception e) {
                failed++;
                log.error("[BILLING-SCHEDULER] Failed for tenant {}: {}", slug, e.getMessage());
            }
        }

        log.info("[BILLING-SCHEDULER] Completed: {} generated, {} failed", generated, failed);
    }

    // ── PDF generation (iText 7) ──────────────────────────────────────────────

    private byte[] generatePdf(String invoiceNumber, String tenantName, String tenantEmail,
                               String periodLabel, LocalDate dueDate,
                               List<Map<String, Object>> modules,
                               BigDecimal subtotal, BigDecimal vat, BigDecimal total) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
            Document    doc = new Document(pdf, PageSize.A4);
            doc.setMargins(50, 50, 50, 50);

            // ── Header ────────────────────────────────────────────────────────
            Table header = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                    .setWidth(UnitValue.createPercentValue(100));

            // Left: HandyFlow branding
            Cell left = new Cell().setBorder(null)
                    .add(new Paragraph("HandyFlow")
                            .setFontSize(22).setBold().setFontColor(NAVY))
                    .add(new Paragraph("Tax Invoice")
                            .setFontSize(12).setFontColor(TEAL))
                    .add(new Paragraph("VAT Reg: 4560000001")
                            .setFontSize(9).setFontColor(MID_GRAY))
                    .add(new Paragraph("handyflow.co.za")
                            .setFontSize(9).setFontColor(MID_GRAY));

            // Right: Invoice metadata
            String issued = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
            String due    = dueDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
            Cell right = new Cell().setBorder(null).setTextAlignment(TextAlignment.RIGHT)
                    .add(new Paragraph(invoiceNumber).setFontSize(16).setBold().setFontColor(NAVY))
                    .add(new Paragraph("Period: " + periodLabel).setFontSize(10).setFontColor(MID_GRAY))
                    .add(new Paragraph("Issued: " + issued).setFontSize(10).setFontColor(MID_GRAY))
                    .add(new Paragraph("Due: " + due).setFontSize(10).setBold().setFontColor(DARK));

            header.addCell(left).addCell(right);
            doc.add(header);

            // ── Divider ────────────────────────────────────────────────────────
            doc.add(new LineSeparator(
                    new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(1f))
                    .setMarginTop(10).setMarginBottom(16));

            // ── Bill to ───────────────────────────────────────────────────────
            doc.add(new Paragraph("BILL TO").setFontSize(8).setBold()
                    .setFontColor(MID_GRAY).setCharacterSpacing(1));
            doc.add(new Paragraph(tenantName).setFontSize(13).setBold().setFontColor(DARK)
                    .setMarginTop(4));
            doc.add(new Paragraph(tenantEmail).setFontSize(10).setFontColor(MID_GRAY)
                    .setMarginBottom(20));

            // ── Line items table ──────────────────────────────────────────────
            Table table = new Table(UnitValue.createPercentArray(new float[]{4, 1.5f, 1.5f}))
                    .setWidth(UnitValue.createPercentValue(100))
                    .setMarginBottom(8);

            // Table header
            for (String h : new String[]{"Module", "Status", "Monthly Price"}) {
                table.addHeaderCell(new Cell()
                        .setBackgroundColor(NAVY)
                        .add(new Paragraph(h).setFontSize(10).setBold()
                                .setFontColor(com.itextpdf.kernel.colors.ColorConstants.WHITE))
                        .setPadding(8));
            }

            // Module rows
            for (int i = 0; i < modules.size(); i++) {
                Map<String, Object> m = modules.get(i);
                boolean isActive = "ACTIVE".equals(m.get("status"));
                BigDecimal price = isActive ? (BigDecimal) m.get("monthly_price") : BigDecimal.ZERO;
                DeviceRgb rowBg  = i % 2 == 0
                        ? new DeviceRgb(255, 255, 255)
                        : new DeviceRgb(248, 250, 252);

                table.addCell(new Cell().setBackgroundColor(rowBg)
                        .add(new Paragraph((String) m.get("name"))
                                .setFontSize(10).setFontColor(DARK)).setPadding(7));

                String statusLabel = isActive ? "Active" : "Trial";
                DeviceRgb statusColor = isActive ? TEAL : new DeviceRgb(217, 119, 6);
                table.addCell(new Cell().setBackgroundColor(rowBg)
                        .add(new Paragraph(statusLabel)
                                .setFontSize(10).setFontColor(statusColor)).setPadding(7));

                table.addCell(new Cell().setBackgroundColor(rowBg)
                        .setTextAlignment(TextAlignment.RIGHT)
                        .add(new Paragraph(isActive ? "R " + ZAR.format(price) : "FREE (Trial)")
                                .setFontSize(10).setFontColor(isActive ? DARK : MID_GRAY))
                        .setPadding(7));
            }

            doc.add(table);

            // ── Totals ────────────────────────────────────────────────────────
            Table totals = new Table(UnitValue.createPercentArray(new float[]{3, 1.5f}))
                    .setWidth(UnitValue.createPercentValue(50))
                    .setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.RIGHT)
                    .setMarginTop(8);

            addTotalRow(totals, "Subtotal (excl. VAT)", "R " + ZAR.format(subtotal), false);
            addTotalRow(totals, "VAT (15%)", "R " + ZAR.format(vat), false);

            // Total row — navy background
            Cell totalLbl = new Cell().setBackgroundColor(NAVY).setPadding(8)
                    .add(new Paragraph("TOTAL DUE")
                            .setFontSize(11).setBold()
                            .setFontColor(com.itextpdf.kernel.colors.ColorConstants.WHITE));
            Cell totalVal = new Cell().setBackgroundColor(NAVY).setPadding(8)
                    .setTextAlignment(TextAlignment.RIGHT)
                    .add(new Paragraph("R " + ZAR.format(total))
                            .setFontSize(11).setBold()
                            .setFontColor(com.itextpdf.kernel.colors.ColorConstants.WHITE));
            totals.addCell(totalLbl).addCell(totalVal);
            doc.add(totals);

            // ── Payment details ───────────────────────────────────────────────
            doc.add(new Paragraph("\nPayment Details").setFontSize(10).setBold()
                    .setFontColor(NAVY).setMarginTop(24));
            doc.add(new LineSeparator(
                    new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(0.5f))
                    .setMarginBottom(8));

            String[][] banking = {
                    {"Bank", "First National Bank"},
                    {"Account name", "HandyFlow (Pty) Ltd"},
                    {"Account number", "62012345678"},
                    {"Branch code", "250655"},
                    {"Reference", invoiceNumber},
            };
            Table bankTable = new Table(UnitValue.createPercentArray(new float[]{1, 2}))
                    .setWidth(UnitValue.createPercentValue(60));
            for (String[] row : banking) {
                bankTable.addCell(new Cell().setBorder(null)
                        .add(new Paragraph(row[0]).setFontSize(9)
                                .setFontColor(MID_GRAY).setBold()));
                bankTable.addCell(new Cell().setBorder(null)
                        .add(new Paragraph(row[1]).setFontSize(9).setFontColor(DARK)));
            }
            doc.add(bankTable);

            // ── Legal footer ──────────────────────────────────────────────────
            doc.add(new Paragraph(
                    "\nThis is a tax invoice issued in accordance with South African VAT Act No. 89 of 1991. " +
                            "HandyFlow (Pty) Ltd — Reg No. 2024/000001/07 — VAT Reg No. 4560000001. " +
                            "Payment due within 30 days of invoice date. Queries: billing@handyflow.co.za")
                    .setFontSize(8).setFontColor(MID_GRAY).setMarginTop(24));

            doc.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("PDF generation failed for invoice: {}", e.getMessage(), e);
            throw new HandyFlowException("PDF generation failed", HttpStatus.INTERNAL_SERVER_ERROR, "PDF_ERROR");
        }
    }

    private void addTotalRow(Table t, String label, String value, boolean bold) {
        // iText 7: setBold() takes no arguments — apply conditionally
        Paragraph lp = new Paragraph(label).setFontSize(10).setFontColor(MID_GRAY);
        Paragraph vp = new Paragraph(value).setFontSize(10).setFontColor(DARK);
        if (bold) { lp.setBold(); vp.setBold(); }

        Cell lCell = new Cell().setBorderLeft(null).setBorderRight(null)
                .add(lp).setPadding(6);
        Cell vCell = new Cell().setBorderLeft(null).setBorderRight(null)
                .setTextAlignment(TextAlignment.RIGHT)
                .add(vp).setPadding(6);
        t.addCell(lCell).addCell(vCell);
    }

    // ── Email template ────────────────────────────────────────────────────────

    private String tenantInvoiceEmail(String tenantName, String invoiceNumber,
                                      String periodLabel, String totalAmount) {
        return """
            <!DOCTYPE html><html><head><meta charset="UTF-8">
            <style>
              body { font-family: 'Inter', Arial, sans-serif; background: #F1F5F9; margin: 0; padding: 0; }
              .container { max-width: 560px; margin: 40px auto; background: white;
                           border-radius: 12px; overflow: hidden;
                           box-shadow: 0 4px 20px rgba(0,0,0,0.08); }
              .header { background: #1B3A6B; padding: 28px 32px; }
              .header h1 { color: white; margin: 0; font-size: 20px; font-weight: 700; }
              .header p { color: rgba(255,255,255,0.7); margin: 4px 0 0; font-size: 13px; }
              .body { padding: 32px; }
              .body p { color: #374151; line-height: 1.6; font-size: 14px; margin: 0 0 16px; }
              .invoice-box { background: #F8FAFC; border: 1px solid #E2E8F0; border-radius: 10px;
                             padding: 20px 24px; margin: 20px 0; }
              .invoice-box .row { display: flex; justify-content: space-between;
                                   padding: 6px 0; font-size: 13px; }
              .invoice-box .row.total { border-top: 1px solid #E2E8F0; margin-top: 8px;
                                        padding-top: 12px; font-weight: 700; font-size: 15px;
                                        color: #1B3A6B; }
              .footer { background: #F8FAFC; padding: 20px 32px; border-top: 1px solid #E2E8F0; }
              .footer p { color: #94A3B8; font-size: 12px; margin: 0; }
            </style></head><body>
            <div class="container">
              <div class="header">
                <h1>HandyFlow</h1>
                <p>Tax Invoice — %s</p>
              </div>
              <div class="body">
                <p>Hi %s,</p>
                <p>Please find your HandyFlow subscription invoice for <strong>%s</strong> below.</p>
                <div class="invoice-box">
                  <div class="row"><span>Invoice number</span><strong>%s</strong></div>
                  <div class="row"><span>Billing period</span><span>%s</span></div>
                  <div class="row"><span>Payment terms</span><span>30 days from invoice date</span></div>
                  <div class="row total"><span>Total due (incl. VAT)</span><span>%s</span></div>
                </div>
                <p>Please use your invoice number <strong>%s</strong> as the payment reference when making your EFT payment.</p>
                <p><strong>Banking details:</strong><br>
                   Bank: First National Bank<br>
                   Account: HandyFlow (Pty) Ltd<br>
                   Acc No: 62012345678 &nbsp;|&nbsp; Branch: 250655<br>
                   Reference: %s</p>
                <p>Questions? Reply to this email or contact <a href="mailto:billing@handyflow.co.za" style="color:#0D9488;">billing@handyflow.co.za</a></p>
              </div>
              <div class="footer">
                <p>HandyFlow (Pty) Ltd &middot; VAT Reg 4560000001 &middot;
                   <a href="https://handyflow.co.za" style="color:#0D9488;">handyflow.co.za</a></p>
              </div>
            </div>
            </body></html>
            """.formatted(
                periodLabel, tenantName, periodLabel,
                invoiceNumber, periodLabel, totalAmount,
                invoiceNumber, invoiceNumber);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> resolveTenant(String slug) {
        try {
            return jdbc.queryForMap("""
                SELECT t.id, t.name, t.email
                FROM tenants t WHERE t.slug = ?
                """, slug);
        } catch (Exception e) {
            throw new HandyFlowException("Tenant not found: " + slug,
                    HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
    }

    private String nextInvoiceNumber() {
        Long seq = jdbc.queryForObject("SELECT nextval('admin_invoice_seq')", Long.class);
        return String.format("HF-INV-%d-%04d", LocalDate.now().getYear(), seq);
    }

    private String fetchTenantName(UUID tenantId) {
        try {
            String name = jdbc.queryForObject(
                    "SELECT name FROM tenants WHERE id = ?", String.class, tenantId);
            return name != null ? name : tenantId.toString();
        } catch (Exception e) { return tenantId.toString(); }
    }

    private String fetchTenantSlug(UUID tenantId) {
        try {
            String slug = jdbc.queryForObject(
                    "SELECT slug FROM tenants WHERE id = ?", String.class, tenantId);
            return slug != null ? slug : "";
        } catch (Exception e) { return ""; }
    }

    private void audit(UUID adminId, String adminEmail, String action,
                       String targetType, String targetId, String targetName,
                       String details) {
        auditRepo.save(AdminAuditLog.create(adminId, adminEmail, action,
                targetType, targetId, targetName, details, null));
    }
}
