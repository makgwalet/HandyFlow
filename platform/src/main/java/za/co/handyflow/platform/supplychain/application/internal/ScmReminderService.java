package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import za.co.handyflow.platform.shared.EmailService;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Hourly reminders for RFQs and quotations (timings in {@link RfqReminderRules}; each reminder is sent once):
 * <ul>
 *   <li>to an invited supplier who has not answered, 48 hours before the RFQ closes;</li>
 *   <li>to the company's admins when an RFQ's closing time passes with the RFQ still open, saying how many quotations came in
 *       (none is its own message: nobody answered);</li>
 *   <li>to the company's admins when a submitted quotation's validity ends within 7 days and the RFQ is still undecided.</li>
 * </ul>
 * A failure on one row is logged and the next row still runs; the marker is only set after the email was handed over.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScmReminderService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy 'at' HH:mm").withZone(ZoneId.of("Africa/Johannesburg"));

    private final JdbcTemplate jdbc;
    private final EmailService emailService;
    private final ScmNotificationService notifications;

    @Scheduled(cron = "0 10 * * * *", zone = "Africa/Johannesburg")
    public void run() {
        Instant now = Instant.now();
        supplierReminders(now);
        buyerCloseNotices(now);
        quotationExpiryNotices(LocalDate.now(ZoneId.of("Africa/Johannesburg")));
    }

    void supplierReminders(Instant now) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT i.id, i.contact_email, i.supplier_name, i.status AS inv_status, i.reminder_sent_at, r.rfq_number, r.title, r.status AS rfq_status, r.closes_at
                  FROM sc_rfq_invitations i JOIN sc_rfqs r ON r.id = i.rfq_id AND r.tenant_id = i.tenant_id
                 WHERE i.status = 'INVITED' AND i.reminder_sent_at IS NULL AND r.status = 'OPEN' AND r.closes_at > ? AND r.closes_at <= ?
                   AND i.contact_email IS NOT NULL AND i.contact_email <> ''""",
                Timestamp.from(now), Timestamp.from(now.plusSeconds(RfqReminderRules.SUPPLIER_HOURS_BEFORE_CLOSE * 3600L)));
        for (Map<String, Object> r : rows) {
            try {
                Instant closes = ((Timestamp) r.get("closes_at")).toInstant();
                if (!RfqReminderRules.supplierReminderDue("INVITED", null, "OPEN", closes, now, RfqReminderRules.SUPPLIER_HOURS_BEFORE_CLOSE)) continue;
                String body = "<p>Dear " + esc(r.get("supplier_name")) + ",</p><p>This is a reminder that the request for quotation <strong>" + esc(r.get("rfq_number")) + "</strong> — "
                        + esc(r.get("title")) + " — closes on <strong>" + WHEN.format(closes) + "</strong>. We have not yet received your quotation.</p>"
                        + "<p>If you cannot quote, please let us know so we can plan.</p>";
                emailService.send((String) r.get("contact_email"), "Reminder: RFQ " + r.get("rfq_number") + " closes " + WHEN.format(closes), body);
                jdbc.update("UPDATE sc_rfq_invitations SET reminder_sent_at = now() WHERE id = ?", r.get("id"));
            } catch (Exception e) {
                log.error("[SCM] RFQ supplier reminder failed for invitation={}: {}", r.get("id"), e.getMessage());
            }
        }
    }

    void buyerCloseNotices(Instant now) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT r.id, r.tenant_id, r.rfq_number, r.title, r.closes_at,
                       (SELECT count(*) FROM sc_quotations q WHERE q.rfq_id = r.id AND q.status = 'SUBMITTED') AS quotes,
                       (SELECT count(*) FROM sc_rfq_invitations i WHERE i.rfq_id = r.id) AS invited
                  FROM sc_rfqs r WHERE r.status = 'OPEN' AND r.closes_at <= ? AND r.buyer_close_notice_sent_at IS NULL""", Timestamp.from(now));
        for (Map<String, Object> r : rows) {
            try {
                long quotes = ((Number) r.get("quotes")).longValue();
                String what = quotes == 0
                        ? "<p>No supplier has submitted a quotation. Extend the closing date or invite more suppliers.</p>"
                        : "<p>" + quotes + " quotation(s) were received from " + r.get("invited") + " invited supplier(s). Close the RFQ to compare and award.</p>";
                notifications.notifyAdmins((UUID) r.get("tenant_id"), "RFQ " + r.get("rfq_number") + " has reached its closing time",
                        "<p>RFQ <strong>" + esc(r.get("rfq_number")) + "</strong> — " + esc(r.get("title")) + " — passed its closing time and is still open.</p>" + what);
                jdbc.update("UPDATE sc_rfqs SET buyer_close_notice_sent_at = now() WHERE id = ?", r.get("id"));
            } catch (Exception e) {
                log.error("[SCM] RFQ close notice failed for rfq={}: {}", r.get("id"), e.getMessage());
            }
        }
    }

    void quotationExpiryNotices(LocalDate today) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT q.id, q.tenant_id, q.supplier_name, q.valid_until, r.rfq_number, r.title
                  FROM sc_quotations q JOIN sc_rfqs r ON r.id = q.rfq_id AND r.tenant_id = q.tenant_id
                 WHERE q.status = 'SUBMITTED' AND q.expiry_notice_sent_at IS NULL AND r.status IN ('OPEN','CLOSED')
                   AND q.valid_until >= ? AND q.valid_until <= ?
                 ORDER BY q.tenant_id, q.valid_until""", Date.valueOf(today), Date.valueOf(today.plusDays(RfqReminderRules.QUOTATION_EXPIRY_DAYS)));
        Map<UUID, List<Map<String, Object>>> byTenant = new LinkedHashMap<>();
        for (Map<String, Object> q : rows) byTenant.computeIfAbsent((UUID) q.get("tenant_id"), k -> new ArrayList<>()).add(q);
        byTenant.forEach((tenantId, quotes) -> {
            try {
                StringBuilder body = new StringBuilder("<p>These quotations stop being valid soon and their RFQs are still undecided. Award, or ask the supplier to extend.</p><ul>");
                for (Map<String, Object> q : quotes)
                    body.append("<li>").append(esc(q.get("supplier_name"))).append(" on RFQ ").append(esc(q.get("rfq_number"))).append(" (").append(esc(q.get("title")))
                            .append(") — valid until ").append(((Date) q.get("valid_until")).toLocalDate()).append("</li>");
                body.append("</ul>");
                notifications.notifyAdmins(tenantId, "Quotations about to expire (" + quotes.size() + ")", body.toString());
                for (Map<String, Object> q : quotes) jdbc.update("UPDATE sc_quotations SET expiry_notice_sent_at = now() WHERE id = ?", q.get("id"));
            } catch (Exception e) {
                log.error("[SCM] Quotation expiry notice failed for tenant={}: {}", tenantId, e.getMessage());
            }
        });
    }

    private static String esc(Object s) { return HtmlUtils.htmlEscape(String.valueOf(s)); }
}
