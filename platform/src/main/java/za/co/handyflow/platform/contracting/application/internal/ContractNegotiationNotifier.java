package za.co.handyflow.platform.contracting.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.contracting.domain.repository.ContractRepository;
import za.co.handyflow.platform.contracting.scheduler.ContractObligationReminderScheduler;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tells staff when a counterparty has proposed or commented, once the transaction has committed (so a rolled-back proposal sends
 * nothing, and a mail failure never undoes one). At most one email per contract in ten minutes, so a counterparty working through a
 * long contract does not flood the inbox; the daily reminder covers whatever is still waiting. The throttle is per instance and
 * in memory: after a restart, or with several instances, an extra email is possible, never a missing reminder.
 */
@Slf4j
@Component
public class ContractNegotiationNotifier {

    static final Duration QUIET = Duration.ofMinutes(10);

    private final ContractRepository contractRepo;
    private final EmailService emailService;
    private final TenantAdminRecipients recipients;
    private final Clock clock;
    private final Map<UUID, Instant> lastSent = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    ContractNegotiationNotifier(ContractRepository contractRepo, EmailService emailService, TenantAdminRecipients recipients) {
        this(contractRepo, emailService, recipients, Clock.systemUTC());
    }

    ContractNegotiationNotifier(ContractRepository contractRepo, EmailService emailService, TenantAdminRecipients recipients, Clock clock) {
        this.contractRepo = contractRepo;
        this.emailService = emailService;
        this.recipients = recipients;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void on(NegotiationActivity a) {
        try {
            Instant now = clock.instant();
            Instant last = lastSent.get(a.contractId());
            if (last != null && Duration.between(last, now).compareTo(QUIET) < 0) return;
            Contract c = contractRepo.findById(a.contractId()).orElse(null);
            if (c == null || !c.getTenantId().equals(a.tenantId())) return;
            List<String> to = ContractObligationReminderScheduler.recipientEmails(null,
                    () -> emails(c.getCreatedBy() == null ? List.of() : recipients.resolveUsers(TenantId.of(a.tenantId()), List.of(c.getCreatedBy()))),
                    () -> emails(recipients.resolveTenantAdmins(a.tenantId())));
            if (to.isEmpty()) return;
            for (String address : to) emailService.send(address, subject(a, c), body(a, c));
            lastSent.put(a.contractId(), now);
        } catch (Exception e) {
            log.error("[NEGOTIATION] Could not notify staff about contract={}: {}", a.contractId(), e.getMessage());
        }
    }

    private static List<String> emails(List<Recipient> rs) {
        return rs.stream().map(Recipient::email).filter(e -> e != null && !e.isBlank()).map(String::trim).distinct().toList();
    }

    static String subject(NegotiationActivity a, Contract c) {
        String lead = NegotiationActivity.PROPOSAL.equals(a.kind()) ? "New proposal on " : NegotiationActivity.DECISION.equals(a.kind()) ? "Counterparty answered a proposal on " : "New comment on ";
        return lead + c.getTitle();
    }

    static String body(NegotiationActivity a, Contract c) {
        String what = NegotiationActivity.PROPOSAL.equals(a.kind()) ? "proposed a change to" : NegotiationActivity.DECISION.equals(a.kind()) ? "answered a proposal on" : "commented on";
        String who = a.partyName() == null || a.partyName().isBlank() ? "A counterparty" : HtmlUtils.htmlEscape(a.partyName());
        return "<p>" + who + " has " + what + " contract " + HtmlUtils.htmlEscape(c.getContractNumber()) + " (" + HtmlUtils.htmlEscape(c.getTitle())
                + ").</p><p>Open the contract in HandyFlow to review it. The contract itself has not changed.</p>";
    }
}
