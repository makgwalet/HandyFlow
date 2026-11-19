package za.co.handyflow.platform.contracting.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.contracting.domain.model.ContractNegotiation;
import za.co.handyflow.platform.contracting.domain.model.ContractNegotiationProposal;
import za.co.handyflow.platform.contracting.domain.repository.ContractNegotiationProposalRepository;
import za.co.handyflow.platform.contracting.domain.repository.ContractNegotiationRepository;
import za.co.handyflow.platform.contracting.domain.repository.ContractNegotiationSessionRepository;
import za.co.handyflow.platform.contracting.domain.repository.ContractRepository;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Housekeeping and reminders for contract negotiation.
 * <p>
 * Sessions: expired counterparty sessions are deleted every night, so they no longer wait for the next sign-in to be cleaned up.
 * <p>
 * Reminders go to staff, never to counterparties: to the user who created the contract, or to the tenant's administrators when that
 * user cannot be resolved. They say that a response deadline is close or has passed, or that proposals are waiting for a decision.
 * Counterparty proposals are not emailed the moment they arrive; the daily reminder picks them up the next morning.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractNegotiationScheduler {

    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy");

    private final ContractNegotiationSessionRepository sessionRepo;
    private final ContractNegotiationRepository negotiationRepo;
    private final ContractNegotiationProposalRepository proposalRepo;
    private final ContractRepository contractRepo;
    private final EmailService emailService;
    private final TenantAdminRecipients recipients;

    /** A session stays valid for an hour; one that expired more than a day ago has no further use, even as evidence of a sign-in. */
    @Scheduled(cron = "0 30 3 * * *", zone = "Africa/Johannesburg")
    public void deleteExpiredSessions() {
        int removed = sessionRepo.deleteExpired(Instant.now().minusSeconds(24 * 3600));
        if (removed > 0) log.info("[SCHEDULER] Expired negotiation sessions removed: {}", removed);
    }

    @Scheduled(cron = "0 15 8 * * *", zone = "Africa/Johannesburg")
    @Transactional
    public void sendReminders() {
        LocalDate today = LocalDate.now(ZONE);
        List<ContractNegotiation> active = negotiationRepo.findByStatusIn(Set.of(ContractNegotiation.OPEN, ContractNegotiation.STAFF_REVIEW, ContractNegotiation.CHANGES_REQUIRED));
        if (active.isEmpty()) return;
        Map<UUID, Contract> contracts = contractRepo.findAllById(active.stream().map(ContractNegotiation::getContractId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Contract::getId, c -> c));
        int sent = 0;
        for (ContractNegotiation n : active) {
            try {
                Contract c = contracts.get(n.getContractId());
                if (c == null) continue;
                List<ContractNegotiationProposal> pending = proposalRepo.findByTenantIdAndContractIdAndStatus(n.getTenantId(), n.getContractId(), ContractNegotiationProposal.PENDING);
                Instant oldest = pending.stream().filter(x -> !x.byStaff()).map(ContractNegotiationProposal::getCreatedAt).min(Comparator.naturalOrder()).orElse(null);
                String reason = n.reminderReason(today, oldest, ZONE);
                if (reason == null) continue;
                List<String> to = ContractObligationReminderScheduler.recipientEmails(null,
                        () -> emails(c.getCreatedBy() == null ? List.of() : recipients.resolveUsers(TenantId.of(n.getTenantId()), List.of(c.getCreatedBy()))),
                        () -> emails(recipients.resolveTenantAdmins(n.getTenantId())));
                if (to.isEmpty()) {
                    log.warn("[SCHEDULER] No one to remind about negotiation={} tenant={}", n.getId(), n.getTenantId());
                    continue;
                }
                for (String address : to) emailService.send(address, subject(reason, c), body(reason, n, c, pending.size()));
                n.markReminded(today);
                negotiationRepo.save(n);
                sent++;
            } catch (Exception e) {
                log.error("[SCHEDULER] Negotiation reminder failed for negotiation={}: {}", n.getId(), e.getMessage());
            }
        }
        if (sent > 0) log.info("[SCHEDULER] Negotiation reminders sent: {}", sent);
    }

    private static List<String> emails(List<Recipient> rs) {
        return rs.stream().map(Recipient::email).filter(e -> e != null && !e.isBlank()).map(String::trim).distinct().toList();
    }

    static String subject(String reason, Contract c) {
        return switch (reason) {
            case ContractNegotiation.REMIND_DEADLINE_PASSED -> "Negotiation deadline passed: " + c.getTitle();
            case ContractNegotiation.REMIND_DEADLINE_SOON -> "Negotiation deadline close: " + c.getTitle();
            default -> "Proposals waiting for a decision: " + c.getTitle();
        };
    }

    static String body(String reason, ContractNegotiation n, Contract c, int pending) {
        String head = "<p>Contract " + HtmlUtils.htmlEscape(c.getContractNumber()) + " (" + HtmlUtils.htmlEscape(c.getTitle()) + ") is being negotiated.</p>";
        String deadline = n.getResponseDeadline() == null ? "" : "<p>Response deadline: " + n.getResponseDeadline().format(DATE_FMT)
                + (ContractNegotiation.REMIND_DEADLINE_PASSED.equals(reason) ? " (passed: counterparties can no longer add proposals until it is extended or the negotiation is reopened)." : ".") + "</p>";
        String waiting = pending == 0 ? "" : "<p>" + pending + " proposal" + (pending == 1 ? " is" : "s are") + " waiting for a decision.</p>";
        return head + deadline + waiting;
    }
}
