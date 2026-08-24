package za.co.handyflow.platform.shared;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * FIX: both scheduled jobs in this class were failing on EVERY run —
 * confirmed via real production logs (BadSqlGrammarException,
 * "column u.deleted_at does not exist", firing on both
 * sendQuoteExpiryReminders() and sendPilotCountdownReminders()). Same
 * root cause both times: `users` has no `deleted_at` column at all —
 * users are deactivated via a `status` column (User.UserStatus enum:
 * ACTIVE/INACTIVE/LOCKED, string-mapped), not soft-deleted.
 * <p>
 * This exact bug pattern was already found and fixed once before, in
 * identity.TenantAdminRecipientsImpl — that class's own extensive
 * comment documents the identical root cause and the identical fix
 * (u.status = 'ACTIVE'). This class was evidently never touched by that
 * same pass. Confirmed 'ACTIVE' is the correct literal by checking
 * User.UserStatus's real enum values directly (ACTIVE, INACTIVE,
 * LOCKED) rather than assuming the prior fix's own literal was right —
 * it was.
 * <p>
 * Both queries silently failed on every single scheduled run before
 * this fix — no quote-expiry reminder or pilot-countdown reminder has
 * ever actually been sent, since the exception was thrown before any
 * row could be read, every single time the job ran.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final JdbcTemplate        jdbc;
    private final EmailService         emailService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.notifications.enabled:true}")
    private boolean notificationsEnabled;

    // ── Run daily at 08:00 SAST ────────────────────────────────────────

    @Scheduled(cron = "0 0 8 * * *", zone = "Africa/Johannesburg")
    public void sendQuoteExpiryReminders() {
        if (!notificationsEnabled) return;

        // Quotes created 23 days ago that are still DRAFT or SENT
        // (expires at 30 days, remind at day 23 = 7 days warning)
        String sql = """
            SELECT
                q.id, q.quote_number, q.total, q.status,
                u.email, u.first_name,
                c.name AS customer_name,
                t.slug AS tenant_slug
            FROM quotes q
            JOIN tenants t ON t.id = q.tenant_id
            JOIN users u ON u.tenant_id = t.id AND u.status = 'ACTIVE'
            JOIN crm_customers c ON c.id = q.customer_id
            WHERE q.deleted_at IS NULL
              AND q.status IN ('DRAFT', 'SENT')
              AND q.created_at::date = CURRENT_DATE - INTERVAL '23 days'
            LIMIT 100
            """;

        List<Map<String, Object>> rows = jdbc.queryForList(sql);
        log.info("Quote expiry reminders: {} quotes due", rows.size());

        for (Map<String, Object> row : rows) {
            try {
                String email        = (String) row.get("email");
                String firstName    = (String) row.get("first_name");
                String quoteNumber  = (String) row.get("quote_number");
                String customerName = (String) row.get("customer_name");
                BigDecimal total    = (BigDecimal) row.get("total");
                String amount       = "R " + String.format("%,.2f", total);

                emailService.send(
                        email,
                        "Quote " + quoteNumber + " expires in 7 days",
                        EmailTemplates.quoteExpiry(firstName, quoteNumber, customerName, amount, frontendUrl)
                );
            } catch (Exception e) {
                log.error("Failed to send quote expiry email: {}", e.getMessage());
            }
        }
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "Africa/Johannesburg")
    public void sendPilotCountdownReminders() {
        if (!notificationsEnabled) return;

        // Send at 45 days remaining, 14 days remaining, 7 days remaining, 3 days remaining
        String sql = """
            SELECT
                t.id AS tenant_id, t.name AS tenant_name,
                s.pilot_ends_at,
                s.plan_display_name,
                u.email, u.first_name,
                (s.pilot_ends_at::date - CURRENT_DATE) AS days_remaining
            FROM subscriptions s
            JOIN tenants t ON t.id = s.tenant_id
            JOIN users u ON u.tenant_id = t.id
                AND u.status = 'ACTIVE'
            WHERE s.status = 'PILOT'
              AND s.deleted_at IS NULL
              AND (s.pilot_ends_at::date - CURRENT_DATE) IN (45, 14, 7, 3)
            LIMIT 100
            """;

        List<Map<String, Object>> rows = jdbc.queryForList(sql);
        log.info("Pilot countdown reminders: {} tenants", rows.size());

        for (Map<String, Object> row : rows) {
            try {
                String email       = (String) row.get("email");
                String firstName   = (String) row.get("first_name");
                int daysRemaining  = ((Number) row.get("days_remaining")).intValue();
                String planName    = (String) row.get("plan_display_name");

                emailService.send(
                        email,
                        daysRemaining <= 7
                                ? "⚠️ Your HandyFlow pilot ends in " + daysRemaining + " days"
                                : "Your HandyFlow pilot ends in " + daysRemaining + " days",
                        EmailTemplates.pilotCountdown(firstName, daysRemaining, planName, frontendUrl)
                );
            } catch (Exception e) {
                log.error("Failed to send pilot countdown email: {}", e.getMessage());
            }
        }
    }
}