package za.co.handyflow.platform.accountant.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accountant.domain.model.FeeNote;
import za.co.handyflow.platform.accountant.domain.model.TaxDeadline;
import za.co.handyflow.platform.accountant.domain.repository.*;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.EmailTemplates;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountantScheduler {

    private final TaxDeadlineRepository deadlineRepo;
    private final AccClientRepository   clientRepo;
    private final FeeNoteRepository     feeNoteRepo;
    private final EmailService          emailService;
    private final JdbcTemplate          jdbc;

    @Scheduled(cron = "0 0 6 * * *", zone = "Africa/Johannesburg")
    @Transactional
    public void processDeadlines() {
        LocalDate today = LocalDate.now();
        log.info("AccountantScheduler: processing deadlines for {}", today);

        // Flip to OVERDUE
        List<TaxDeadline> overdue = deadlineRepo.findOverdue(today);
        overdue.forEach(d -> { d.markOverdue(); deadlineRepo.save(d); });

        // Send reminders
        sendReminders(deadlineRepo.findPendingReminder30(today.plusDays(30)), "30-day", 30);
        sendReminders(deadlineRepo.findPendingReminder7(today.plusDays(7)),   "7-day",  7);
        sendReminders(deadlineRepo.findPendingReminder1(today.plusDays(1)),   "1-day",  1);

        log.info("AccountantScheduler: deadline processing complete. Overdue={}", overdue.size());
    }

    @Scheduled(cron = "0 0 7 * * *", zone = "Africa/Johannesburg")
    @Transactional
    public void processOverdueFeeNotes() {
        List<FeeNote> overdue = feeNoteRepo.findOverdue(null, LocalDate.now());
        overdue.forEach(f -> { f.markOverdue(); feeNoteRepo.save(f); });
        if (!overdue.isEmpty())
            log.info("AccountantScheduler: marked {} fee notes OVERDUE", overdue.size());
    }

    private void sendReminders(List<TaxDeadline> deadlines, String window, int days) {
        for (TaxDeadline d : deadlines) {
            clientRepo.findById(d.getClientId()).ifPresent(client -> {
                String firmEmail = lookupFirmEmail(d.getTenantId());
                if (firmEmail != null) {
                    emailService.send(firmEmail,
                            String.format("[ACTION] SARS %s due in %d day%s — %s",
                                    d.getDeadlineType(), days, days == 1 ? "" : "s",
                                    client.getTradingName()),
                            EmailTemplates.taxDeadlineReminder(
                                    client.getTradingName(), d.getDeadlineType(),
                                    d.getAdjustedDueDate().toString(), days,
                                    d.getPeriodYear(), d.getPeriodMonth()));
                }
            });
            switch (window) {
                case "30-day" -> d.markReminder30Sent();
                case "7-day"  -> d.markReminder7Sent();
                case "1-day"  -> d.markReminder1Sent();
            }
            deadlineRepo.save(d);
        }
        if (!deadlines.isEmpty())
            log.info("AccountantScheduler: sent {} {} reminders", deadlines.size(), window);
    }

    private String lookupFirmEmail(UUID tenantId) {
        try {
            return jdbc.queryForObject(
                    "SELECT contact_email FROM accountant_profiles WHERE tenant_id = ?",
                    String.class, tenantId);
        } catch (Exception e) {
            log.warn("Could not look up firm email for tenant={}", tenantId);
            return null;
        }
    }
}
