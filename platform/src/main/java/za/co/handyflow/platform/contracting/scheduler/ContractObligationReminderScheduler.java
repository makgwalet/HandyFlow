package za.co.handyflow.platform.contracting.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.contracting.domain.model.ContractObligation;
import za.co.handyflow.platform.contracting.domain.repository.ContractObligationRepository;
import za.co.handyflow.platform.contracting.domain.repository.ContractRepository;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Daily reminders for open obligations: 7 days before, 1 day before, on the due date, then weekly while overdue. Goes to the
 * obligation's reminder address when staff entered one. Otherwise it goes to the user who created the contract, and if that user
 * cannot be resolved (no longer active, no email) to the tenant's administrators, so an obligation is not silently skipped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractObligationReminderScheduler {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy");

    private final ContractObligationRepository obligationRepo;
    private final ContractRepository contractRepo;
    private final EmailService emailService;
    private final TenantAdminRecipients recipients;

    @Scheduled(cron = "0 0 8 * * *", zone = "Africa/Johannesburg")
    @Transactional
    public void sendReminders() {
        LocalDate today = LocalDate.now();
        List<ContractObligation> candidates = obligationRepo.findForReminders(today.plusDays(7)).stream()
                .filter(o -> o.reminderDue(today)).toList();
        if (candidates.isEmpty()) return;
        Map<UUID, Contract> contracts = contractRepo.findAllById(candidates.stream().map(ContractObligation::getContractId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Contract::getId, c -> c));
        int sent = 0;
        for (ContractObligation o : candidates) {
            Contract c = contracts.get(o.getContractId());
            if (c == null) continue;
            try {
                List<String> to = recipientEmails(o.getOwnerEmail(),
                        () -> emails(c.getCreatedBy() == null ? List.of() : recipients.resolveUsers(TenantId.of(o.getTenantId()), List.of(c.getCreatedBy()))),
                        () -> emails(recipients.resolveTenantAdmins(o.getTenantId())));
                if (to.isEmpty()) {
                    log.warn("[SCHEDULER] No one to remind for obligation={} tenant={}", o.getId(), o.getTenantId());
                    continue;
                }
                for (String address : to) emailService.send(address, subject(o, today), body(o, c, today));
                o.markReminded(today);
                obligationRepo.save(o);
                sent++;
            } catch (Exception e) {
                log.error("[SCHEDULER] Obligation reminder failed for obligation={}: {}", o.getId(), e.getMessage());
            }
        }
        log.info("[SCHEDULER] Obligation reminders sent: {}", sent);
    }

    /** The entered address wins; otherwise the contract's creator; otherwise the tenant's administrators. */
    static List<String> recipientEmails(String ownerEmail, java.util.function.Supplier<List<String>> creator, java.util.function.Supplier<List<String>> admins) {
        if (ownerEmail != null && !ownerEmail.isBlank()) return List.of(ownerEmail.trim());
        List<String> c = creator.get();
        if (!c.isEmpty()) return c;
        return admins.get();
    }

    private static List<String> emails(List<Recipient> rs) {
        return rs.stream().map(Recipient::email).filter(e -> e != null && !e.isBlank()).map(String::trim).distinct().toList();
    }

    static String subject(ContractObligation o, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, o.getDueDate());
        String when = days < 0 ? "overdue by " + (-days) + " day" + (days == -1 ? "" : "s") : days == 0 ? "due today" : "due in " + days + " day" + (days == 1 ? "" : "s");
        return "Contract obligation " + when + ": " + o.getTitle();
    }

    static String body(ContractObligation o, Contract c, LocalDate today) {
        String side = "OURS".equals(o.getResponsibleSide()) ? "our side" : "the counterparty";
        return "<p>An obligation under contract " + HtmlUtils.htmlEscape(c.getContractNumber()) + " (" + HtmlUtils.htmlEscape(c.getTitle()) + ") needs attention.</p>"
                + "<p><strong>" + HtmlUtils.htmlEscape(o.getTitle()) + "</strong><br/>Due " + o.getDueDate().format(DATE_FMT)
                + ". Responsible: " + side + ".</p>"
                + (o.getDescription() == null || o.getDescription().isBlank() ? "" : "<p>" + HtmlUtils.htmlEscape(o.getDescription()) + "</p>");
    }
}
