package za.co.handyflow.platform.accountant.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accountant.domain.model.AccClient;
import za.co.handyflow.platform.accountant.domain.model.AccFicaDocument;
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
    // NEW: backs processFicaDocumentReminders() — closes the "FICA
    // expiry reminders" gap.
    private final AccFicaDocumentRepository ficaDocRepo;

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

    /**
     * NEW: closes the accountant module audit's "TCS PIN expiry
     * reminders" quick-win gap — tcsPinExpiry was already captured and
     * shown in the UI but nothing in the scheduler ever watched it.
     * Same tiered D-30/D-7/D-1 structure as processDeadlines(), offset
     * 15 minutes later (06:15 vs 06:00 SAST) so the two jobs don't fire
     * at the exact same instant — same reasoning already used for SCM's
     * BBBEE-expiry check relative to its own low-stock digest.
     */
    @Scheduled(cron = "0 15 6 * * *", zone = "Africa/Johannesburg")
    @Transactional
    public void processTcsPinReminders() {
        LocalDate today = LocalDate.now();
        log.info("AccountantScheduler: processing TCS PIN expiry reminders for {}", today);

        sendTcsPinReminders(clientRepo.findTcsPinPendingReminder30(today.plusDays(30)), "30-day", 30);
        sendTcsPinReminders(clientRepo.findTcsPinPendingReminder7(today.plusDays(7)),   "7-day",  7);
        sendTcsPinReminders(clientRepo.findTcsPinPendingReminder1(today.plusDays(1)),   "1-day",  1);
    }

    private void sendTcsPinReminders(List<AccClient> clients, String window, int days) {
        for (AccClient c : clients) {
            String firmEmail = lookupFirmEmail(c.getTenantId().getValue());
            if (firmEmail != null) {
                emailService.send(firmEmail,
                        String.format("[ACTION] TCS PIN expires in %d day%s — %s",
                                days, days == 1 ? "" : "s", c.getTradingName()),
                        EmailTemplates.tcsPinExpiryReminder(
                                c.getTradingName(), c.getTcsPinExpiry().toString(), days));
            }
            switch (window) {
                case "30-day" -> c.markTcsPinReminder30Sent();
                case "7-day"  -> c.markTcsPinReminder7Sent();
                case "1-day"  -> c.markTcsPinReminder1Sent();
            }
            clientRepo.save(c);
        }
        if (!clients.isEmpty())
            log.info("AccountantScheduler: sent {} TCS PIN {} reminders", clients.size(), window);
    }

    /**
     * NEW: closes the accountant module audit's "FICA expiry reminders"
     * gap — acc_fica_documents.expiry_date was captured on upload but
     * nothing in the scheduler ever watched it. Same tiered D-30/D-7/
     * D-1 structure as processTcsPinReminders() just above, offset
     * another 15 minutes later (06:45 vs 06:30 SAST) continuing the
     * established stagger so none of these jobs fire at the exact same
     * instant.
     */
    @Scheduled(cron = "0 45 6 * * *", zone = "Africa/Johannesburg")
    @Transactional
    public void processFicaDocumentReminders() {
        LocalDate today = LocalDate.now();
        log.info("AccountantScheduler: processing FICA document expiry reminders for {}", today);

        sendFicaDocumentReminders(ficaDocRepo.findPendingReminder30(today.plusDays(30)), "30-day", 30);
        sendFicaDocumentReminders(ficaDocRepo.findPendingReminder7(today.plusDays(7)),   "7-day",  7);
        sendFicaDocumentReminders(ficaDocRepo.findPendingReminder1(today.plusDays(1)),   "1-day",  1);
    }

    private void sendFicaDocumentReminders(List<AccFicaDocument> docs, String window, int days) {
        for (AccFicaDocument d : docs) {
            clientRepo.findById(d.getClientId()).ifPresent(client -> {
                String firmEmail = lookupFirmEmail(d.getTenantId());
                if (firmEmail != null) {
                    emailService.send(firmEmail,
                            String.format("[ACTION] FICA document expires in %d day%s — %s",
                                    days, days == 1 ? "" : "s", client.getTradingName()),
                            EmailTemplates.ficaDocumentExpiryReminder(
                                    client.getTradingName(), d.getDocType(), d.getFileName(),
                                    d.getExpiryDate().toString(), days));
                }
            });
            switch (window) {
                case "30-day" -> d.markReminder30Sent();
                case "7-day"  -> d.markReminder7Sent();
                case "1-day"  -> d.markReminder1Sent();
            }
            ficaDocRepo.save(d);
        }
        if (!docs.isEmpty())
            log.info("AccountantScheduler: sent {} FICA document {} reminders", docs.size(), window);
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
                // NEW: closes the audit's "client-facing deadline
                // reminder emails" gap. Deliberately a separate email
                // (own subject line, own client-appropriate template),
                // not a CC of the firm's internal one — see
                // EmailTemplates.clientDeadlineReminder()'s own comment
                // for why. Respects the per-client opt-out and requires
                // an actual contact email, same "only if present" guard
                // every other client-facing email in this module
                // already uses (e.g. sendFeeNote()).
                if (client.isClientDeadlineRemindersEnabled() && client.getContactEmail() != null) {
                    String firmName = lookupFirmName(d.getTenantId());
                    emailService.send(client.getContactEmail(),
                            String.format("Reminder: your %s is due in %d day%s",
                                    d.getDeadlineType(), days, days == 1 ? "" : "s"),
                            EmailTemplates.clientDeadlineReminder(
                                    firmName, d.getDeadlineType(), d.getAdjustedDueDate().toString(), days));
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

    /**
     * NEW: backs clientDeadlineReminder()'s firm-name placeholder. Same
     * structure as lookupFirmEmail() just above, but falls back to a
     * generic string rather than null — a null email correctly means
     * "don't send this", but a null firm name inside an email that's
     * already being sent to the client would just show up as a broken
     * sentence in their inbox.
     */
    private String lookupFirmName(UUID tenantId) {
        try {
            return jdbc.queryForObject(
                    "SELECT firm_name FROM accountant_profiles WHERE tenant_id = ?",
                    String.class, tenantId);
        } catch (Exception e) {
            log.warn("Could not look up firm name for tenant={}", tenantId);
            return "your accountant";
        }
    }
}