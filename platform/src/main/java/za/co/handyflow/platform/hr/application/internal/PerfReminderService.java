package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.HrPerfCheckin;
import za.co.handyflow.platform.hr.domain.model.HrPerfPlan;
import za.co.handyflow.platform.hr.domain.model.HrPerfCycle;
import za.co.handyflow.platform.hr.domain.model.HrPerfReview;
import za.co.handyflow.platform.hr.domain.model.PerfReminderRules;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.domain.repository.HrPerfCheckinRepository;
import za.co.handyflow.platform.hr.domain.repository.HrPerfCycleRepository;
import za.co.handyflow.platform.hr.domain.repository.HrPerfPlanRepository;
import za.co.handyflow.platform.hr.domain.repository.HrPerfReviewRepository;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Daily reminders for reviews that are past their dates. An employee who has not written their self-review is told once the day
 * after it was due and then weekly. A reviewer gets one message per cycle that says how many of their reviews are waiting for a
 * rating; when a review has no reviewer the company's administrators are told instead. Which day a message goes is decided by
 * {@link PerfReminderRules}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerfReminderService {

    private final HrPerfCycleRepository cycleRepo;
    private final HrPerfReviewRepository reviewRepo;
    private final HrEmployeeRepository employeeRepo;
    private final PerfNotifier notifier;
    private final TenantAdminRecipients adminRecipients;
    private final HrPerfPlanRepository planRepo;
    private final HrPerfCheckinRepository checkinRepo;

    /** Sends today's reminders for every company. Returns how many messages went out. */
    @Transactional(readOnly = true)
    public int remindAll(LocalDate today) {
        List<HrPerfCycle> open = cycleRepo.allOpen();
        if (open.isEmpty()) return 0;
        Map<UUID, List<HrPerfReview>> byCycle = reviewRepo.forCycles(open.stream().map(HrPerfCycle::getId).toList()).stream()
                .collect(Collectors.groupingBy(HrPerfReview::getCycleId));
        int sent = 0;
        for (HrPerfCycle c : open) {
            try {
                sent += remind(c, byCycle.getOrDefault(c.getId(), List.of()), today);
            } catch (RuntimeException e) {
                log.error("Performance reminder failed for cycle {}: {}", c.getId(), e.getMessage());
            }
        }
        log.info("Performance reminders: {} message(s) for {} open cycle(s)", sent, open.size());
        return sent;
    }

    /**
     * Reminders for running improvement plans: a check-in is due when none has been recorded for two weeks, and a plan still running after its
     * end date has to be closed. They go to the plan's owner, or to the administrators when the owner has left. Returns how many went out.
     */
    @Transactional(readOnly = true)
    public int remindPlans(LocalDate today) {
        int sent = 0;
        for (HrPerfPlan p : planRepo.allActive()) {
            try {
                sent += remindPlan(p, today);
            } catch (RuntimeException e) {
                log.error("Plan reminder failed for plan {}: {}", p.getId(), e.getMessage());
            }
        }
        log.info("Plan reminders: {} message(s)", sent);
        return sent;
    }

    private int remindPlan(HrPerfPlan plan, LocalDate today) {
        TenantId tenant = TenantId.of(plan.getTenantId());
        LocalDate last = checkinRepo.forPlan(plan.getId()).stream().map(HrPerfCheckin::getOnDate).max(Comparator.naturalOrder()).orElse(null);
        boolean checkin = PerfReminderRules.checkinReminderToday(plan.getStatus(), plan.getCommunicatedOn(), plan.getStartDate(), last, today);
        boolean ended = PerfReminderRules.endedReminderToday(plan.getStatus(), plan.getEndDate(), today);
        if (!checkin && !ended) return 0;
        Optional<HrEmployee> employee = employeeRepo.findActiveById(tenant, plan.getEmployeeId()).filter(x -> !"TERMINATED".equals(x.getStatus()));
        if (employee.isEmpty()) return 0;
        String name = employee.get().getFullName();
        List<Recipient> to = plan.getOwnerId() == null ? adminRecipients.resolveTenantAdmins(tenant) : reviewer(tenant, plan.getOwnerId(), adminRecipients);
        if (to.isEmpty()) return 0;
        int sent = 0;
        if (checkin) {
            notifier.send(tenant, NotificationType.PERF_PLAN_CHECKIN_DUE, PerfReminderRules.checkinTitle(name),
                    PerfReminderRules.checkinMessage(name, last, today), plan.getId(), "/hr/performance", to);
            sent++;
        }
        if (ended) {
            notifier.send(tenant, NotificationType.PERF_PLAN_ENDED, PerfReminderRules.planEndedTitle(name),
                    PerfReminderRules.planEndedMessage(name, plan.getEndDate(), today), plan.getId(), "/hr/performance", to);
            sent++;
        }
        return sent;
    }

    private int remind(HrPerfCycle cycle, List<HrPerfReview> reviews, LocalDate today) {
        TenantId tenant = TenantId.of(cycle.getTenantId());
        int sent = 0;
        Map<UUID, Integer> waitingFor = new LinkedHashMap<>();   // reviewer (null = nobody chosen) -> reviews waiting
        for (HrPerfReview r : reviews) {
            if (PerfReminderRules.selfReminderToday(r.getStatus(), cycle.getSelfDue(), today)) {
                Optional<HrEmployee> e = employeeRepo.findActiveById(tenant, r.getEmployeeId());
                Recipient to = e.filter(x -> !"TERMINATED".equals(x.getStatus())).map(PerfNotifier::contactOf).orElse(null);
                if (to != null) {
                    notifier.send(tenant, NotificationType.PERF_SELF_REVIEW_DUE, PerfReminderRules.selfLateTitle(cycle.getName()),
                            PerfReminderRules.selfLateMessage(cycle.getName(), cycle.getSelfDue(), today), r.getId(), "/hr/performance", to);
                    sent++;
                }
            }
            if (PerfReminderRules.managerReminderToday(r.getStatus(), cycle.getManagerDue(), today))
                waitingFor.merge(r.getReviewerId(), 1, Integer::sum);
        }
        for (Map.Entry<UUID, Integer> w : waitingFor.entrySet()) {
            List<Recipient> to = w.getKey() == null ? adminRecipients.resolveTenantAdmins(tenant) : reviewer(tenant, w.getKey(), adminRecipients);
            if (to.isEmpty()) continue;
            notifier.send(tenant, NotificationType.PERF_REVIEWS_OVERDUE, PerfReminderRules.overdueTitle(w.getValue()),
                    PerfReminderRules.overdueMessage(cycle.getName(), w.getValue(), cycle.getManagerDue(), today), cycle.getId(), "/hr/performance", to);
            sent++;
        }
        return sent;
    }

    /** The reviewer's own contact, or the administrators when the reviewer has left or has no email. */
    private List<Recipient> reviewer(TenantId tenant, UUID reviewerId, TenantAdminRecipients admins) {
        Recipient r = employeeRepo.findActiveById(tenant, reviewerId)
                .filter(x -> !"TERMINATED".equals(x.getStatus())).map(PerfNotifier::contactOf).orElse(null);
        return r != null ? List.of(r) : admins.resolveTenantAdmins(tenant);
    }
}
