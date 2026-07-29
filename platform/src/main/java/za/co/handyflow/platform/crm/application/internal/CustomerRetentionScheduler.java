package za.co.handyflow.platform.crm.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.domain.model.Customer;
import za.co.handyflow.platform.crm.domain.repository.CustomerActivityRepository;
import za.co.handyflow.platform.crm.domain.repository.CustomerConsentRepository;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * CustomerRetentionScheduler — nightly POPIA retention policy review.
 *
 * WHY no TenantRepository dependency?
 * Same reason as CustomerInactivityScheduler — we derive tenant IDs
 * from the customers table instead of importing a cross-module repository.
 *
 * WHAT IT DOES:
 * Finds all consent records whose retention_expires_at has passed and
 * records a RETENTION_REVIEW_REQUIRED activity on each customer's timeline.
 * Staff see this in the CRM and decide: extend retention or delete.
 *
 * WHY not auto-delete?
 * POPIA requires judgment — an outstanding invoice means retention is still
 * legally necessary even after the consent period expires. Auto-deleting
 * would destroy data you're legally required to keep.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerRetentionScheduler {

    /** How far ahead of retentionExpiresAt to send the proactive reminder. */
    private static final int EXPIRY_WARNING_DAYS = 30;

    private final CustomerConsentRepository  consentRepository;
    private final CustomerActivityRepository activityRepository;
    private final CustomerRepository         customerRepository;
    private final EmailService               emailService;
    private final TenantAdminRecipients      tenantAdminRecipients;

    @Scheduled(cron = "0 0 3 * * *")  // 3:00 AM daily
    public void reviewExpiredRetentionRecords() {
        log.info("[CRM] Retention review starting");

        var tenantIds  = customerRepository.findDistinctActiveTenantIds();
        int totalFlags = 0;

        for (UUID tenantId : tenantIds) {
            try {
                int flagged = reviewForTenant(TenantId.of(tenantId));
                totalFlags += flagged;
            } catch (Exception ex) {
                log.error("[CRM] Retention review failed for tenant={}: {}",
                        tenantId, ex.getMessage(), ex);
            }
        }

        log.info("[CRM] Retention review complete — {} records flagged for review", totalFlags);
    }

    /**
     * FIX: "retention-review flags are timeline-only" — CustomerRetentionScheduler
     * wrote an activity entry but never told anyone. A passive timeline
     * entry is a weak trigger for a legally-sensitive process — someone
     * has to remember to open each customer's timeline to notice.
     * <p>
     * Deliberately ONE digest email per tenant, not one email per expired
     * customer — if retention lapses for 30 customers around the same
     * time (a very real scenario: a batch of customers imported/consented
     * on the same day years ago all hit their retention date together),
     * a compliance officer getting 30 separate emails would either ignore
     * them or unsubscribe from the whole channel. A single "N customers
     * need review" digest with the list inline stays useful at any scale.
     */
    @Transactional
    public int reviewForTenant(TenantId tenantId) {
        var expired = consentRepository.findExpiredForTenant(tenantId, Instant.now());
        int count   = 0;
        var reviewList = new java.util.ArrayList<ReviewItem>();

        for (var consent : expired) {
            var activity = za.co.handyflow.platform.crm.domain.model.CustomerActivity
                    .systemEvent(
                            tenantId,
                            consent.getCustomerId(),
                            za.co.handyflow.platform.crm.domain.model.ActivityType.RETENTION_REVIEW_REQUIRED,
                            "Retention period expired. Review required: extend retention or delete customer record."
                    );
            activityRepository.save(activity);
            count++;

            String customerName = customerRepository.findActiveById(tenantId, consent.getCustomerId())
                    .map(Customer::getName)
                    .orElse("Customer " + consent.getCustomerId());
            reviewList.add(new ReviewItem(customerName, consent.getRetentionExpiresAt()));

            log.info("[CRM] Retention expired: customer={} tenant={} expired={}",
                    consent.getCustomerId(), tenantId, consent.getRetentionExpiresAt());
        }

        if (!reviewList.isEmpty()) {
            sendRetentionDigest(tenantId, reviewList);
        }

        return count;
    }

    private void sendRetentionDigest(TenantId tenantId, List<ReviewItem> items) {
        String subject = items.size() == 1
                ? "POPIA retention review needed for 1 customer"
                : "POPIA retention review needed for " + items.size() + " customers";
        sendDigestToTenantAdmins(tenantId, subject, buildExpiredDigestHtml(items), items.size(), "retention review");
    }

    /**
     * FIX: "no consent-expiring-soon reminder" gap — the retention
     * scheduler only fired after expiry; nothing proactively warned staff
     * with enough runway to renew consent before it became a compliance
     * gap. Runs right after the expired-records job, same per-tenant
     * structure, same edge-triggered "fire once" guard
     * (expiryReminderSentAt) as everywhere else this pattern is used in
     * this codebase.
     */
    @Scheduled(cron = "0 30 3 * * *")  // 3:30 AM daily — right after reviewExpiredRetentionRecords
    public void sendExpiringSoonReminders() {
        log.info("[CRM] Consent expiry reminder sweep starting");

        var tenantIds = customerRepository.findDistinctActiveTenantIds();
        int totalReminded = 0;

        for (UUID tenantId : tenantIds) {
            try {
                totalReminded += sendExpiryRemindersForTenant(TenantId.of(tenantId));
            } catch (Exception ex) {
                log.error("[CRM] Consent expiry reminder sweep failed for tenant={}: {}",
                        tenantId, ex.getMessage(), ex);
            }
        }

        log.info("[CRM] Consent expiry reminder sweep complete — {} reminders sent", totalReminded);
    }

    @Transactional
    public int sendExpiryRemindersForTenant(TenantId tenantId) {
        Instant now       = Instant.now();
        Instant threshold = now.plus(EXPIRY_WARNING_DAYS, java.time.temporal.ChronoUnit.DAYS);
        var expiringSoon  = consentRepository.findExpiringSoonForTenant(tenantId, now, threshold);
        if (expiringSoon.isEmpty()) return 0;

        var items = new java.util.ArrayList<ReviewItem>();
        for (var consent : expiringSoon) {
            String customerName = customerRepository.findActiveById(tenantId, consent.getCustomerId())
                    .map(Customer::getName)
                    .orElse("Customer " + consent.getCustomerId());
            items.add(new ReviewItem(customerName, consent.getRetentionExpiresAt()));

            // Mark-before-send, same pattern used elsewhere in this codebase
            // for this exact kind of idempotency guard: a send failure
            // should not cause the reminder to be re-attempted (and
            // potentially re-sent) on tomorrow's run.
            consent.markExpiryReminderSent();
            consentRepository.save(consent);

            log.info("[CRM] Consent expiring soon: customer={} tenant={} expires={}",
                    consent.getCustomerId(), tenantId, consent.getRetentionExpiresAt());
        }

        String subject = items.size() == 1
                ? "POPIA consent expiring soon for 1 customer"
                : "POPIA consent expiring soon for " + items.size() + " customers";
        sendDigestToTenantAdmins(tenantId, subject, buildExpiringSoonDigestHtml(items), items.size(), "expiry reminder");

        return items.size();
    }

    /** Shared by both digests — resolves tenant admins once and sends the same email to each. */
    private void sendDigestToTenantAdmins(TenantId tenantId, String subject, String html, int itemCount, String digestKind) {
        try {
            List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
            if (admins.isEmpty()) {
                log.warn("[CRM] {} customer(s) flagged for {} on tenant={} but no admin recipients could be resolved — email not sent",
                        itemCount, digestKind, tenantId);
                return;
            }

            for (Recipient admin : admins) {
                if (admin.email() == null || admin.email().isBlank()) continue;
                try {
                    emailService.send(admin.email(), subject, html);
                } catch (Exception e) {
                    log.warn("[CRM] {} digest not sent to={} tenant={}: {}",
                            digestKind, admin.email(), tenantId, e.getMessage());
                }
            }
        } catch (Exception e) {
            // Same principle as every other notification hookup in this
            // codebase: the actual work that produced this digest (timeline
            // flags, or marking the expiry reminder sent) already succeeded
            // above and must not be undone by an email failure.
            log.warn("[CRM] Failed to send {} digest for tenant={}: {}", digestKind, tenantId, e.getMessage());
        }
    }

    private String buildExpiredDigestHtml(List<ReviewItem> items) {
        String rows = buildTableRows(items, "Retention expired");
        return "<p>" + items.size() + " customer" + (items.size() == 1 ? "" : "s")
                + " " + (items.size() == 1 ? "has" : "have") + " passed their POPIA data retention period "
                + "and need review — extend retention (e.g. an outstanding invoice makes continued retention "
                + "legally necessary) or delete the record.</p>"
                + rows
                + "<p>Open each customer's record in the CRM to mark it reviewed and decide next steps.</p>";
    }

    private String buildExpiringSoonDigestHtml(List<ReviewItem> items) {
        String rows = buildTableRows(items, "Retention expires");
        return "<p>" + items.size() + " customer" + (items.size() == 1 ? "'s" : "s'")
                + " POPIA data retention period will expire within the next " + EXPIRY_WARNING_DAYS + " days.</p>"
                + "<p>Renew consent now if you still need to process this data, so it doesn't lapse into a "
                + "compliance gap — once it expires, it moves to the retention-review queue instead.</p>"
                + rows
                + "<p>Open each customer's record in the CRM to record renewed consent.</p>";
    }

    private String buildTableRows(List<ReviewItem> items, String dateColumnLabel) {
        var dateFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH).withZone(ZoneId.of("Africa/Johannesburg"));
        StringBuilder rows = new StringBuilder();
        for (ReviewItem item : items) {
            String dateStr = item.retentionExpiresAt() != null ? dateFmt.format(item.retentionExpiresAt()) : "—";
            rows.append("<tr><td style=\"padding:6px 10px;border-bottom:1px solid #E2E8F0;\">")
                    .append(escapeHtml(item.customerName()))
                    .append("</td><td style=\"padding:6px 10px;border-bottom:1px solid #E2E8F0;\">")
                    .append(dateStr)
                    .append("</td></tr>");
        }
        return "<table style=\"border-collapse:collapse;width:100%;max-width:500px;\">"
                + "<tr><th style=\"text-align:left;padding:6px 10px;border-bottom:2px solid #1B3A6B;\">Customer</th>"
                + "<th style=\"text-align:left;padding:6px 10px;border-bottom:2px solid #1B3A6B;\">" + dateColumnLabel + "</th></tr>"
                + rows
                + "</table>";
    }

    private String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private record ReviewItem(String customerName, Instant retentionExpiresAt) {}
}