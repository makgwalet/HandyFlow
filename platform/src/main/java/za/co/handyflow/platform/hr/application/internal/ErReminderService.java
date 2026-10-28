package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.hr.domain.model.ErPolicy;
import za.co.handyflow.platform.hr.domain.model.ErReminderRules;
import za.co.handyflow.platform.hr.domain.model.ErReminderRules.When;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.HrErCase;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.domain.repository.HrErCaseRepository;
import za.co.handyflow.platform.hr.dto.ErDtos.Row;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Daily reminders for employee relations cases: a meeting, a grievance response, telling the employee the decision, or the end of the appeal
 * period, due tomorrow or late. They go to the person who owns the case, or to the company's administrators when it has no owner or the owner
 * has no email. The dates come from the same code as the case list ({@link ErCaseViews}); which day a message goes is decided by
 * {@link ErReminderRules}. Cases recorded from before the system are left alone.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErReminderService {

    private final HrErCaseRepository caseRepo;
    private final ErCaseViews views;
    private final ErPolicyService policyService;
    private final HrEmployeeRepository employeeRepo;
    private final PerfNotifier notifier;
    private final TenantAdminRecipients adminRecipients;

    /** Sends today's reminders for every company. Returns how many messages went out. */
    public int remindAll(LocalDate today) {
        int sent = 0;
        Map<UUID, ErPolicy> policies = new HashMap<>();
        for (HrErCase c : caseRepo.allOpen()) {
            if (c.isLegacy()) continue;
            try {
                sent += remind(c, policies, today);
            } catch (RuntimeException e) {
                log.error("Case reminder failed for case {}: {}", c.getId(), e.getMessage());
            }
        }
        log.info("Case reminders: {} message(s)", sent);
        return sent;
    }

    private int remind(HrErCase c, Map<UUID, ErPolicy> policies, LocalDate today) {
        TenantId tenant = TenantId.of(c.getTenantId());
        ErPolicy policy = policies.computeIfAbsent(c.getTenantId(), id -> policyService.get(tenant));
        Row row = views.row(c, null, policy, today);
        // A decision nobody has told the employee about has no date of its own: it is late from the day after it was made.
        LocalDate due = row.dueOn() != null ? row.dueOn() : "Tell the employee".equals(row.dueLabel()) ? c.getDecidedOn() : null;
        When when = ErReminderRules.when(due, today);
        if (when == When.NONE) return 0;
        List<Recipient> to = recipients(tenant, c.getOwnerUserId());
        if (to.isEmpty()) return 0;
        notifier.send(tenant, NotificationType.ER_CASE_DUE, ErReminderRules.title(c.getCaseNumber(), row.dueLabel(), when),
                ErReminderRules.message(c.getCaseNumber(), row.dueLabel(), due, when, today), c.getId(), "/hr/disciplinary", to);
        return 1;
    }

    /** The owner's own contact, or the administrators when there is no owner or the owner has left or has no email. */
    private List<Recipient> recipients(TenantId tenant, UUID ownerUserId) {
        if (ownerUserId != null) {
            Recipient r = employeeRepo.findByUserId(tenant, ownerUserId).filter(x -> !"TERMINATED".equals(x.getStatus()))
                    .map(PerfNotifier::contactOf).orElse(null);
            if (r != null) return List.of(r);
        }
        return adminRecipients.resolveTenantAdmins(tenant);
    }
}
