package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.hr.domain.model.ErPolicy;
import za.co.handyflow.platform.hr.domain.model.ErReminderRules;
import za.co.handyflow.platform.hr.domain.model.ErRules;
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
 * <p>
 * Also: the day before a hearing the chair, the person presenting the case and the employee are told; and the case owner is told when a
 * suspension is about to end or has no end date, or an incapacity review period is about to end or has ended with no outcome.
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
        int sent = remindStep(c, tenant, policy, today);
        sent += remindHearing(c, tenant, today);
        sent += remindSuspension(c, tenant, today);
        sent += remindReview(c, tenant, today);
        return sent;
    }

    private int remindStep(HrErCase c, TenantId tenant, ErPolicy policy, LocalDate today) {
        Row row = views.row(c, null, policy, today);
        // A decision nobody has told the employee about has no date of its own: it is late from the day after it was made.
        LocalDate due = row.dueOn() != null ? row.dueOn() : "Tell the employee".equals(row.dueLabel()) ? c.getDecidedOn() : null;
        When when = ErReminderRules.when(due, today);
        if (when == When.NONE) return 0;
        List<Recipient> to = recipients(tenant, c.getOwnerUserId());
        if (to.isEmpty()) return 0;
        notifier.send(tenant, NotificationType.ER_CASE_DUE, ErReminderRules.title(c.getCaseNumber(), row.dueLabel(), when),
                ErReminderRules.message(c.getCaseNumber(), row.dueLabel(), due, when, today), c.getId(), "/hr/disciplinary", to);
        // A grievance is answered by the person chosen to decide it, who is not always the case owner. The person it is about is not told:
        // they are not asked to do anything, and hearing of it early from a reminder helps nobody.
        if (ErRules.GRIEVANCE.equals(c.getCaseType()) && c.getChairId() != null && "Response due".equals(row.dueLabel())) {
            Recipient decider = employeeRepo.findActiveById(tenant, c.getChairId()).filter(x -> !"TERMINATED".equals(x.getStatus())).map(PerfNotifier::contactOf).orElse(null);
            if (decider != null) notifier.send(tenant, NotificationType.ER_CASE_DUE, ErReminderRules.title(c.getCaseNumber(), row.dueLabel(), when),
                    ErReminderRules.message(c.getCaseNumber(), row.dueLabel(), due, when, today), c.getId(), "/hr/disciplinary", decider);
        }
        return 1;
    }

    /** The day before a noticed hearing: the chair, the person presenting the case and the employee each get their own wording. */
    private int remindHearing(HrErCase c, TenantId tenant, LocalDate today) {
        if (!ErReminderRules.hearingTomorrow(c.getStatus(), c.getHearingDate(), c.getHearingHeldOn(), today)) return 0;
        int sent = 0;
        sent += tellParty(tenant, c, c.getChairId(), ErReminderRules.Role.CHAIR, "/hr/disciplinary");
        sent += tellParty(tenant, c, c.getInitiatorId(), ErReminderRules.Role.INITIATOR, "/hr/disciplinary");
        sent += tellParty(tenant, c, c.getEmployeeId(), ErReminderRules.Role.EMPLOYEE, "/hr");
        return sent;
    }

    private int tellParty(TenantId tenant, HrErCase c, UUID employeeId, ErReminderRules.Role role, String url) {
        if (employeeId == null) return 0;
        Recipient to = employeeRepo.findActiveById(tenant, employeeId).filter(x -> !"TERMINATED".equals(x.getStatus())).map(PerfNotifier::contactOf).orElse(null);
        if (to == null) return 0;
        notifier.send(tenant, NotificationType.ER_CASE_DUE, ErReminderRules.hearingTitle(c.getCaseNumber()),
                ErReminderRules.hearingMessage(role, c.getCaseNumber(), c.getHearingDate(), c.getHearingTime()), c.getId(), url, to);
        return 1;
    }

    /** A suspension that is about to end, or has no end date and has run long: the case owner is told. */
    private int remindSuspension(HrErCase c, TenantId tenant, LocalDate today) {
        ErReminderRules.Suspension s = ErReminderRules.suspension(c.getSuspendedFrom(), c.getSuspendedTo(), today);
        if (s == ErReminderRules.Suspension.NONE) return 0;
        List<Recipient> to = recipients(tenant, c.getOwnerUserId());
        if (to.isEmpty()) return 0;
        notifier.send(tenant, NotificationType.ER_CASE_DUE, ErReminderRules.suspensionTitle(c.getCaseNumber(), s),
                ErReminderRules.suspensionMessage(c.getCaseNumber(), s, c.getSuspendedFrom(), c.getSuspendedTo(), today), c.getId(), "/hr/disciplinary", to);
        return 1;
    }

    /** The end of an incapacity review period: the case owner is told the day before, then when it has ended with no outcome. */
    private int remindReview(HrErCase c, TenantId tenant, LocalDate today) {
        When when = ErReminderRules.review(c.getOutcome(), c.getReviewTo(), today);
        if (when == When.NONE) return 0;
        List<Recipient> to = recipients(tenant, c.getOwnerUserId());
        if (to.isEmpty()) return 0;
        notifier.send(tenant, NotificationType.ER_CASE_DUE, ErReminderRules.reviewTitle(c.getCaseNumber(), when),
                ErReminderRules.reviewMessage(c.getCaseNumber(), c.getReviewTo(), when), c.getId(), "/hr/disciplinary", to);
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
