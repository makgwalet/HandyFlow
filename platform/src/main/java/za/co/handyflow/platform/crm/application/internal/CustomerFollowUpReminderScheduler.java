package za.co.handyflow.platform.crm.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.domain.model.Customer;
import za.co.handyflow.platform.crm.domain.model.CustomerFollowUp;
import za.co.handyflow.platform.crm.domain.repository.CustomerFollowUpRepository;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CustomerFollowUpReminderScheduler — "call this lead back in 3 days" needs
 * to actually remind someone, not just sit in the database.
 * <p>
 * WHY once per follow-up, not a daily nag while overdue?
 * A follow-up that's genuinely forgotten and stays overdue for weeks would
 * otherwise generate a fresh email every single day — the kind of thing
 * that trains people to ignore the whole channel. One reminder, fired on
 * (or the first run on/after) the due date, is enough to surface it; after
 * that, staying visibly flagged in the customer's follow-up list (see
 * FollowUpResponse.overdue) is what keeps it in front of whoever's
 * responsible without becoming inbox noise.
 * <p>
 * WHY one digest per assignee, not one email per follow-up?
 * Same reasoning as CustomerRetentionScheduler's digest — five follow-ups
 * due for the same person on the same day should be one email, not five.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerFollowUpReminderScheduler {

    private final CustomerFollowUpRepository followUpRepository;
    private final CustomerRepository         customerRepository;
    private final EmailService               emailService;
    private final JdbcTemplate               jdbc;

    @Scheduled(cron = "0 0 7 * * *")  // 7:00 AM daily
    public void sendDueReminders() {
        log.info("[CRM] Follow-up reminder sweep starting");

        var tenantIds = customerRepository.findDistinctActiveTenantIds();
        int totalReminded = 0;

        for (UUID tenantId : tenantIds) {
            try {
                totalReminded += sendRemindersForTenant(TenantId.of(tenantId));
            } catch (Exception ex) {
                log.error("[CRM] Follow-up reminder sweep failed for tenant={}: {}",
                        tenantId, ex.getMessage(), ex);
            }
        }

        log.info("[CRM] Follow-up reminder sweep complete — {} follow-ups reminded", totalReminded);
    }

    @Transactional
    public int sendRemindersForTenant(TenantId tenantId) {
        var due = followUpRepository.findDueForReminder(tenantId, LocalDate.now());
        if (due.isEmpty()) return 0;

        // Mark-before-send for every item up front — same idempotency
        // reasoning used throughout this codebase: a send failure should
        // not cause tomorrow's run to re-pick-up (and potentially
        // re-send) the same follow-up.
        for (CustomerFollowUp f : due) {
            f.markReminderSent();
            followUpRepository.save(f);
        }

        Map<UUID, List<CustomerFollowUp>> byAssignee = due.stream()
                .filter(f -> f.getAssignedTo() != null)
                .collect(Collectors.groupingBy(CustomerFollowUp::getAssignedTo));

        for (var entry : byAssignee.entrySet()) {
            sendDigestToAssignee(tenantId, entry.getKey(), entry.getValue());
        }

        return due.size();
    }

    private void sendDigestToAssignee(TenantId tenantId, UUID assigneeId, List<CustomerFollowUp> items) {
        try {
            String email;
            String firstName;
            try {
                email     = jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, assigneeId);
                firstName = jdbc.queryForObject("SELECT first_name FROM users WHERE id = ?", String.class, assigneeId);
            } catch (Exception e) {
                log.info("[CRM] Could not resolve email for follow-up assignee={} tenant={} — not notified",
                        assigneeId, tenantId);
                return;
            }
            if (email == null || email.isBlank()) return;

            var dateFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH).withZone(ZoneId.of("Africa/Johannesburg"));
            LocalDate today = LocalDate.now();

            StringBuilder rows = new StringBuilder();
            for (CustomerFollowUp f : items) {
                String customerName = customerRepository.findActiveById(tenantId, f.getCustomerId())
                        .map(Customer::getName).orElse("Customer " + f.getCustomerId());
                boolean overdue = f.getDueDate().isBefore(today);
                String dueLabel = overdue
                        ? "<span style=\"color:#DC2626;font-weight:600;\">Overdue — was due " + f.getDueDate().format(dateFmt) + "</span>"
                        : "Due " + f.getDueDate().format(dateFmt);
                rows.append("<tr><td style=\"padding:6px 10px;border-bottom:1px solid #E2E8F0;\">")
                        .append(escapeHtml(customerName))
                        .append("</td><td style=\"padding:6px 10px;border-bottom:1px solid #E2E8F0;\">")
                        .append(escapeHtml(f.getNote()))
                        .append("</td><td style=\"padding:6px 10px;border-bottom:1px solid #E2E8F0;\">")
                        .append(dueLabel)
                        .append("</td></tr>");
            }

            String subject = items.size() == 1
                    ? "1 follow-up due"
                    : items.size() + " follow-ups due";
            String greetingName = firstName != null ? firstName : "there";
            String html = "<p>Dear " + greetingName + ",</p>"
                    + "<p>You have " + items.size() + " customer follow-up" + (items.size() == 1 ? "" : "s") + " due:</p>"
                    + "<table style=\"border-collapse:collapse;width:100%;max-width:600px;\">"
                    + "<tr><th style=\"text-align:left;padding:6px 10px;border-bottom:2px solid #1B3A6B;\">Customer</th>"
                    + "<th style=\"text-align:left;padding:6px 10px;border-bottom:2px solid #1B3A6B;\">Note</th>"
                    + "<th style=\"text-align:left;padding:6px 10px;border-bottom:2px solid #1B3A6B;\">Due</th></tr>"
                    + rows
                    + "</table>"
                    + "<p>Open each customer's record in the CRM to mark these done.</p>";

            emailService.send(email, subject, html);
            log.info("[CRM] Sent follow-up reminder digest to={} count={} tenant={}", email, items.size(), tenantId);
        } catch (Exception e) {
            // Same principle as every other notification hookup in this
            // codebase: the follow-ups above are already marked reminded
            // and must not be undone by an email failure.
            log.warn("[CRM] Follow-up reminder digest not sent for assignee={} tenant={}: {}",
                    assigneeId, tenantId, e.getMessage());
        }
    }

    private String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}