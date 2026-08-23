package za.co.handyflow.platform.supplychain.application.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.domain.repository.ScInventoryRepository;
import za.co.handyflow.platform.supplychain.domain.repository.ScSupplierInvoiceRepository;
import za.co.handyflow.platform.supplychain.domain.repository.ScSupplierRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Sends transactional and scheduled emails for the Supply Chain module.
 *
 * Design decisions explained for junior devs:
 *
 * 1. WHY @Async?
 *    Email sends are I/O — they block the calling thread while SMTP handshakes.
 *    A PO approval that triggers an email should return to the user immediately,
 *    not make them wait for the SMTP server. @Async runs the method on a separate
 *    thread pool thread. The business transaction commits BEFORE the email goes out.
 *
 * 2. WHY try-catch around every send?
 *    An SMTP failure must NEVER roll back a committed business transaction.
 *    If we let the exception propagate from an @Async method, Spring's async
 *    executor logs it and discards it — but explicit try-catch makes the intent
 *    clear and lets us log a structured error message.
 *
 * 3. WHY EntityManager for tenant email lookup instead of TenantRepository?
 *    TenantRepository's method signatures differ across modules. A native SQL
 *    query on the tenants table is stable and works regardless of how the
 *    Tenant entity is mapped in the identity module.
 *
 * 4. FIX: every @Async here was previously unqualified — @Async with no
 *    argument only binds to a bean literally named "taskExecutor". This
 *    app's bounded pool (see NotificationAsyncConfig) is named
 *    "notificationExecutor", so every method below was silently falling
 *    back to Spring's default SimpleAsyncTaskExecutor — an unbounded new
 *    OS thread per call — instead of using the bounded pool. Confirmed
 *    against EmailService.send()'s own documented history: the exact
 *    same bug, already found and fixed there once. This file just hadn't
 *    been checked against the same fix yet.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScmNotificationService {

    private final EmailService emailService;
    private final ScSupplierInvoiceRepository invoiceRepo;
    private final ScInventoryRepository       inventoryRepo;
    // NEW: needed for the BBBEE-expiry scheduled check below.
    private final ScSupplierRepository supplierRepo;

    // FIX: backlog 9.3 — replaces findAdminEmail()'s raw native SQL
    // query against the tenants table.
    private final TenantAdminRecipients tenantAdminRecipients;

    @PersistenceContext
    private EntityManager em;

    @Value("${app.notifications.enabled:true}")
    private boolean notificationsEnabled;

    // ─── Purchase Order events ────────────────────────────────────────────────

    @Async("notificationExecutor")
    public void notifyPoApproved(UUID tenantId, String poNumber, String supplierName,
                                 BigDecimal totalAmount, String approvedBy) {
        send(tenantId,
                "Purchase Order Approved — " + poNumber,
                html("Purchase Order Approved",
                        "<p>A purchase order has been approved and is ready to send to the supplier.</p>" +
                                kv("PO Number",   poNumber) +
                                kv("Supplier",    supplierName) +
                                kv("Total",       "R " + totalAmount.toPlainString()) +
                                kv("Approved By", approvedBy) +
                                "<p>Log in to HandyFlow to send the PO to the supplier.</p>"));
    }

    @Async("notificationExecutor")
    public void notifyPoRejected(UUID tenantId, String poNumber, String reason) {
        send(tenantId,
                "Purchase Order Returned — " + poNumber,
                html("Purchase Order Returned for Revision",
                        "<p>A purchase order has been returned to draft for revision.</p>" +
                                kv("PO Number", poNumber) +
                                kv("Reason",    reason != null ? reason : "No reason provided") +
                                "<p>Log in to HandyFlow to update and resubmit the order.</p>"));
    }

    // NEW (Tier 1 gap analysis): the first supplier-facing email in this
    // module. Every other notification in this class goes through send(),
    // which resolves the TENANT's own admin via findAdminEmail() — there
    // was no code path anywhere that emailed a supplier's own contactEmail,
    // even though that field is captured on every supplier record.
    // Deliberately bypasses send() (and its admin-only lookup) — this has
    // its own try/catch for the same reason send()'s callers do: an @Async
    // method's exceptions are otherwise silently swallowed and logged
    // without context by Spring's executor.
    @Async("notificationExecutor")
    public void notifyPoSentToSupplier(UUID tenantId, String supplierEmail, String supplierName,
                                       String poNumber, BigDecimal totalAmount, LocalDate requiredByDate,
                                       byte[] pdfBytes) {
        if (!notificationsEnabled) {
            log.debug("[SCM] Notifications disabled — skipping supplier PO email: {}", poNumber);
            return;
        }
        if (supplierEmail == null || supplierEmail.isBlank()) {
            log.info("[SCM] No contact email on file for supplier={} — cannot send PO {} to them",
                    supplierName, poNumber);
            return;
        }
        try {
            String tenantName = findTenantName(tenantId).orElse("HandyFlow");
            String subject = "New Purchase Order " + poNumber + " from " + tenantName;
            String htmlBody = html("New Purchase Order",
                    "<p>You have received a new purchase order from <strong>" + esc(tenantName) + "</strong>.</p>" +
                            kv("PO Number", poNumber) +
                            kv("Total",     "R " + totalAmount.toPlainString()) +
                            (requiredByDate != null ? kv("Required By", formatDate(requiredByDate)) : "") +
                            "<p>Please confirm receipt and delivery timeline with your contact at "
                            + esc(tenantName) + "."
                            + (pdfBytes != null ? " The full purchase order is attached as a PDF." : "") + "</p>");

            // NEW: attaches the actual PO PDF — previously this email went
            // out with only a text summary, even after the PDF generator
            // existed, because the two were never wired together.
            // pdfBytes is nullable — see ScmService.markSent()'s own
            // comment on why: a PDF-generation failure must never block
            // this email (or, further upstream, the PO's own send()
            // transition) from going through at all. Falls back to a
            // plain email over sending nothing.
            if (pdfBytes != null) {
                emailService.sendWithAttachment(supplierEmail, subject, htmlBody, poNumber + ".pdf", pdfBytes);
            } else {
                emailService.send(supplierEmail, subject, htmlBody);
            }
        } catch (Exception e) {
            log.error("[SCM] Failed to send PO {} email to supplier={}: {}", poNumber, supplierEmail, e.getMessage());
        }
    }

    // ─── Invoice events ───────────────────────────────────────────────────────

    @Async("notificationExecutor")
    public void notifyInvoiceDisputed(UUID tenantId, String invoiceNumber,
                                      String supplierName, String reason) {
        send(tenantId,
                "Invoice Dispute — " + invoiceNumber,
                html("Supplier Invoice Under Dispute",
                        "<p>A supplier invoice has been flagged for review due to a 3-way match variance.</p>" +
                                kv("Invoice",  invoiceNumber) +
                                kv("Supplier", supplierName) +
                                kv("Reason",   reason) +
                                "<p>Log in to HandyFlow to review the discrepancy and either approve or dispute with the supplier.</p>"));
    }

    // ─── Scheduled checks ─────────────────────────────────────────────────────

    /**
     * Daily check for overdue supplier invoices.
     * Runs at 07:00 SAST every day.
     *
     * WHY query invoiceRepo directly instead of going through ScmService?
     * The notification job is infrastructure — it doesn't need the full service
     * layer. Calling the repo directly is simpler, avoids circular dependencies,
     * and doesn't require building a fake TenantId for each tenant.
     * The query is tenant-scoped because it joins to each row's tenant_id.
     */
    @Scheduled(cron = "0 0 7 * * *", zone = "Africa/Johannesburg")
    @Transactional(readOnly = true)
    public void checkOverdueInvoices() {
        log.info("[SCM] Running overdue invoice check");
        invoiceRepo.findAllOverdueGroupedByTenant().forEach(row -> {
            try {
                UUID   tenantId      = (UUID) row[0];
                String invoiceNumber = (String) row[1];
                String supplierName  = (String) row[2];
                String dueDateStr    = formatSqlDate(row[3]);
                send(tenantId,
                        "Overdue Supplier Invoice — " + invoiceNumber,
                        html("Supplier Invoice Overdue",
                                "<p>A supplier invoice is past its due date and has not yet been paid.</p>" +
                                        kv("Invoice",  invoiceNumber) +
                                        kv("Supplier", supplierName) +
                                        kv("Due Date", dueDateStr) +
                                        "<p>Log in to HandyFlow to process the payment or dispute the invoice.</p>"));
            } catch (Exception e) {
                log.error("[SCM] Overdue invoice notification error: {}", e.getMessage());
            }
        });
    }

    /**
     * Weekly low-stock digest — every Monday at 06:00 SAST.
     * A daily alert would become noise; weekly ensures buyers review stock
     * before the working week starts.
     */
    @Scheduled(cron = "0 0 6 * * MON", zone = "Africa/Johannesburg")
    @Transactional(readOnly = true)
    public void checkLowStock() {
        log.info("[SCM] Running low-stock digest check");
        // findTenantsWithLowStock lives on ScInventoryRepository — that's where the data is
        inventoryRepo.findTenantsWithLowStock().forEach(tenantId -> {
            try {
                send(tenantId,
                        "Weekly Low Stock Alert",
                        html("Items Require Reordering",
                                "<p>One or more items in your inventory are at or below their reorder point.</p>" +
                                        "<p>Log in to HandyFlow to review the low-stock list and create purchase orders.</p>"));
            } catch (Exception e) {
                log.error("[SCM] Low-stock notification error for tenant={}: {}", tenantId, e.getMessage());
            }
        });
    }

    // NEW (Tier 1 gap analysis): bbbeeExpiry is captured on every supplier
    // record but nothing previously watched it — a certificate could
    // lapse with zero warning, which matters for BBBEE procurement
    // compliance specifically (this platform's real differentiator per
    // the module review). Same weekly Monday cadence as checkLowStock(),
    // offset 30 minutes later so the two jobs don't fire at the exact
    // same instant.
    @Scheduled(cron = "0 30 6 * * MON", zone = "Africa/Johannesburg")
    @Transactional(readOnly = true)
    public void checkBbbeeExpiry() {
        log.info("[SCM] Running BBBEE certificate expiry check");

        Map<UUID, List<String>> byTenant = new LinkedHashMap<>();
        supplierRepo.findExpiringBbbeeCertificates().forEach(row -> {
            try {
                UUID   tenantId = (UUID) row[0];
                String name     = (String) row[2];
                String expiry   = formatSqlDate(row[3]);
                byTenant.computeIfAbsent(tenantId, k -> new java.util.ArrayList<>())
                        .add(esc(name) + " — expires " + expiry);
            } catch (Exception e) {
                log.error("[SCM] BBBEE expiry row parse error: {}", e.getMessage());
            }
        });

        byTenant.forEach((tenantId, supplierLines) -> {
            try {
                StringBuilder items = new StringBuilder();
                for (String line : supplierLines) items.append("<li>").append(line).append("</li>");
                send(tenantId,
                        "BBBEE Certificates Expiring Soon",
                        html("Supplier BBBEE Certificates Expiring",
                                "<p>The following suppliers' BBBEE certificates expire within 30 days.</p>" +
                                        "<ul style='padding-left:18px;color:#0F172A'>" + items + "</ul>" +
                                        "<p>Log in to HandyFlow to request updated certificates from these suppliers.</p>"));
            } catch (Exception e) {
                log.error("[SCM] BBBEE expiry notification error for tenant={}: {}", tenantId, e.getMessage());
            }
        });
    }

    // ─── Infrastructure ───────────────────────────────────────────────────────

    private void send(UUID tenantId, String subject, String htmlBody) {
        if (!notificationsEnabled) {
            log.debug("[SCM] Notifications disabled — skipping: {}", subject);
            return;
        }
        // FIX: backlog 9.3 — was findAdminEmail(), a raw native SQL
        // query directly against the tenants table (LIMIT 1 — only ever
        // reached ONE admin), bypassing TenantAdminRecipients, the
        // established pattern confirmed correct in CRM/HR/Fuel/POS. Now
        // notifies every resolved tenant admin, matching that pattern
        // fully rather than keeping the "only one admin" limitation.
        List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(TenantId.of(tenantId));
        if (admins.isEmpty()) {
            log.info("[SCM] No admin recipients for tenant={} — skipping: {}", tenantId, subject);
            return;
        }
        for (Recipient admin : admins) {
            if (admin.email() == null || admin.email().isBlank()) continue;
            emailService.send(admin.email(), subject, htmlBody);
        }
    }

    private Optional<String> findAdminEmail(UUID tenantId) {
        try {
            String email = (String) em.createNativeQuery(
                            "SELECT email FROM tenants WHERE tenant_id = :tid AND email IS NOT NULL LIMIT 1")
                    .setParameter("tid", tenantId)
                    .getSingleResult();
            return Optional.ofNullable(email).filter(e -> !e.isBlank());
        } catch (NoResultException e) {
            return Optional.empty();
        } catch (Exception e) {
            log.warn("[SCM] Could not look up admin email for tenant={}: {}", tenantId, e.getMessage());
            return Optional.empty();
        }
    }

    // NEW: same structure as findAdminEmail() just above — needed to put
    // the tenant's own company name in the supplier-facing PO email
    // (notifyPoSentToSupplier()), since a supplier reading "New Purchase
    // Order from HandyFlow" instead of the actual buyer's company name
    // would be meaningless to them.
    private Optional<String> findTenantName(UUID tenantId) {
        try {
            String name = (String) em.createNativeQuery(
                            "SELECT name FROM tenants WHERE tenant_id = :tid AND name IS NOT NULL LIMIT 1")
                    .setParameter("tid", tenantId)
                    .getSingleResult();
            return Optional.ofNullable(name).filter(n -> !n.isBlank());
        } catch (NoResultException e) {
            return Optional.empty();
        } catch (Exception e) {
            log.warn("[SCM] Could not look up tenant name for tenant={}: {}", tenantId, e.getMessage());
            return Optional.empty();
        }
    }

    // ─── HTML helpers (same pattern as PmNotificationService) ────────────────

    private static String html(String heading, String body) {
        return """
            <!DOCTYPE html><html><head><meta charset="UTF-8"/></head>
            <body style="font-family:Inter,Arial,sans-serif;background:#F8FAFC;padding:32px;margin:0">
              <div style="max-width:560px;margin:0 auto;background:#fff;border-radius:12px;overflow:hidden;border:1px solid #E2E8F0">
                <div style="background:#D97706;padding:20px 28px">
                  <div style="color:#fff;font-size:11px;letter-spacing:0.08em;text-transform:uppercase;opacity:.8;margin-bottom:4px">HandyFlow · Supply Chain</div>
                  <div style="color:#fff;font-size:20px;font-weight:800">""" + heading + """
</div>
                </div>
                <div style="padding:24px 28px;color:#374151;font-size:14px;line-height:1.7">
                  """ + body + """
                  <hr style="border:none;border-top:1px solid #E2E8F0;margin:24px 0 16px"/>
                  <p style="font-size:12px;color:#94A3B8;margin:0">
                    Automated notification from HandyFlow. Do not reply.
                  </p>
                </div>
              </div>
            </body></html>""";
    }

    private static String kv(String key, String value) {
        return "<table style='width:100%;border-collapse:collapse;margin:10px 0'><tr>" +
                "<td style='font-size:12px;color:#94A3B8;font-weight:600;text-transform:uppercase;" +
                "letter-spacing:0.04em;padding:5px 0;width:130px;vertical-align:top'>" + esc(key) + "</td>" +
                "<td style='font-size:14px;color:#0F172A;font-weight:600;padding:5px 0 5px 12px'>" + value + "</td>" +
                "</tr></table>";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }

    // NEW: confirmed via a real sent email (screenshot) that "REQUIRED BY
    // 2026-06-29" was going out to suppliers — raw ISO format, while the
    // PDF attached to that exact same email showed "03 Jul 2026" for the
    // identical field. Same underlying bug found in two other places in
    // this file too (checkOverdueInvoices()'s dueDateStr, checkBbbeeExpiry
    // ()'s expiry) — both were feeding row[N].toString() straight into
    // supplier/tenant-facing email HTML. All three now go through one of
    // these two helpers instead. dd MMM yyyy matches ScPoPdfGenerator's
    // own date format exactly, so the same date reads identically in an
    // email and whatever PDF is attached to it.
    private static final DateTimeFormatter EMAIL_DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private static String formatDate(LocalDate date) {
        return date != null ? date.format(EMAIL_DATE_FMT) : null;
    }

    /**
     * Native SQL query results come back as Object[] rows — a DATE column
     * typically maps to java.sql.Date, but some Hibernate configurations
     * map it straight to LocalDate. Handles either rather than assuming
     * one, falling back to the raw value's own toString() (today's
     * behavior) only if neither type matches, so this can never throw on
     * an unexpected type.
     */
    private static String formatSqlDate(Object dateObj) {
        if (dateObj == null) return "unknown";
        LocalDate date = dateObj instanceof java.sql.Date sqlDate ? sqlDate.toLocalDate()
                : dateObj instanceof LocalDate localDate ? localDate
                : null;
        return date != null ? date.format(EMAIL_DATE_FMT) : dateObj.toString();
    }
}