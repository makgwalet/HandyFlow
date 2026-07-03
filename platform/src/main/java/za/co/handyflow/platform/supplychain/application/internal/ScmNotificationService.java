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
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.supplychain.domain.repository.ScInventoryRepository;
import za.co.handyflow.platform.supplychain.domain.repository.ScSupplierInvoiceRepository;

import java.math.BigDecimal;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScmNotificationService {

    private final EmailService emailService;
    private final ScSupplierInvoiceRepository invoiceRepo;
    private final ScInventoryRepository       inventoryRepo;

    @PersistenceContext
    private EntityManager em;

    @Value("${app.notifications.enabled:true}")
    private boolean notificationsEnabled;

    // ─── Purchase Order events ────────────────────────────────────────────────

    @Async
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

    @Async
    public void notifyPoRejected(UUID tenantId, String poNumber, String reason) {
        send(tenantId,
                "Purchase Order Returned — " + poNumber,
                html("Purchase Order Returned for Revision",
                        "<p>A purchase order has been returned to draft for revision.</p>" +
                                kv("PO Number", poNumber) +
                                kv("Reason",    reason != null ? reason : "No reason provided") +
                                "<p>Log in to HandyFlow to update and resubmit the order.</p>"));
    }

    // ─── Invoice events ───────────────────────────────────────────────────────

    @Async
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
                String dueDateStr    = row[3].toString();
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

    // ─── Infrastructure ───────────────────────────────────────────────────────

    private void send(UUID tenantId, String subject, String htmlBody) {
        if (!notificationsEnabled) {
            log.debug("[SCM] Notifications disabled — skipping: {}", subject);
            return;
        }
        findAdminEmail(tenantId).ifPresentOrElse(
                email -> emailService.send(email, subject, htmlBody),
                ()    -> log.info("[SCM] No admin email for tenant={} — skipping: {}", tenantId, subject)
        );
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
}