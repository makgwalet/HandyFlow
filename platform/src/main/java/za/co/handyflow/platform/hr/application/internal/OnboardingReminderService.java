package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.model.OnboardingReminderRules.Late;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.domain.repository.HrOnboardingRepository;
import za.co.handyflow.platform.hr.domain.repository.HrOnboardingTaskRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tells people about onboarding tasks that are past their due date. A task with someone assigned goes to that person; the rest go
 * to the new employee's manager, or to the company's administrators when there is no manager with an email address. Which tasks
 * are mentioned on which day is decided by {@link OnboardingReminderRules}. Each person gets a given message once a day: the
 * {@link ReminderLedger} remembers who has had it, so a second run or a second app instance stays quiet.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingReminderService {

    private final HrOnboardingRepository onboardingRepo;
    private final HrOnboardingTaskRepository taskRepo;
    private final HrEmployeeRepository employeeRepo;
    private final NotificationService notificationService;
    private final TenantAdminRecipients adminRecipients;
    private final UserRecipientResolver userResolver;
    private final ReminderLedger ledger;

    /** Sends today's reminders for every company. Returns how many notifications went out. One bad onboarding does not stop the rest. */
    @Transactional(readOnly = true)
    public int remindAll(LocalDate today) {
        List<HrOnboarding> all = onboardingRepo.allInProgress();
        if (all.isEmpty()) return 0;
        Map<UUID, List<HrOnboardingTask>> tasks = taskRepo.forOnboardings(all.stream().map(HrOnboarding::getId).toList()).stream()
                .collect(Collectors.groupingBy(HrOnboardingTask::getOnboardingId));
        Map<UUID, HrEmployee> emps = employeeRepo.findAllById(all.stream().map(HrOnboarding::getEmployeeId).toList()).stream()
                .collect(Collectors.toMap(HrEmployee::getId, e -> e, (a, b) -> a));
        int sent = 0;
        for (HrOnboarding o : all) {
            try {
                sent += remind(o, emps.get(o.getEmployeeId()), tasks.getOrDefault(o.getId(), List.of()), today);
            } catch (RuntimeException e) {
                log.error("Onboarding reminder failed for onboarding {}: {}", o.getId(), e.getMessage());
            }
        }
        log.info("Onboarding reminders: {} notification(s) for {} onboarding(s)", sent, all.size());
        return sent;
    }

    private int remind(HrOnboarding o, HrEmployee emp, List<HrOnboardingTask> tasks, LocalDate today) {
        if (emp == null || !"ACTIVE".equals(emp.getStatus())) return 0;
        TenantId tenant = TenantId.of(o.getTenantId());
        OnboardingRules.Facts facts = OnboardingService.facts(emp, today);

        Map<UUID, List<Late>> byAssignee = new LinkedHashMap<>();
        List<Late> unassigned = new ArrayList<>();
        for (HrOnboardingTask t : tasks) {
            String eff = OnboardingRules.effective(t.getStatus(), t.getCheckKey(), facts);
            if (!OnboardingRules.overdue(eff, t.getDueDate(), today)) continue;
            Late late = new Late(t.getTitle(), t.isRequired(), t.getDueDate());
            if (!OnboardingReminderRules.remindToday(late.required(), late.due(), today)) continue;
            if (t.getAssigneeId() != null) byAssignee.computeIfAbsent(t.getAssigneeId(), k -> new ArrayList<>()).add(late);
            else unassigned.add(late);
        }

        int sent = 0;
        for (Map.Entry<UUID, List<Late>> e : byAssignee.entrySet()) {
            Optional<Recipient> who = userResolver.resolveUser(tenant, e.getKey());
            if (who.isPresent()) sent += send(tenant, o, emp, e.getValue(), List.of(who.get()), today);
            else unassigned.addAll(e.getValue());   // the assignee can no longer be reached, so the manager hears of it instead
        }
        if (!unassigned.isEmpty()) {
            List<Recipient> to = managerOf(tenant, emp);
            if (to.isEmpty()) to = adminRecipients.resolveTenantAdmins(tenant);
            if (!to.isEmpty()) sent += send(tenant, o, emp, sortedByDue(unassigned), to, today);
        }
        return sent;
    }

    private List<Recipient> managerOf(TenantId tenant, HrEmployee emp) {
        if (emp.getManagerId() == null) return List.of();
        return employeeRepo.findActiveById(tenant, emp.getManagerId())
                .filter(m -> m.getEmail() != null && !m.getEmail().isBlank())
                .map(m -> List.of(Recipient.external(m.getFullName(), m.getEmail(), m.getPhone())))
                .orElse(List.of());
    }

    private static List<Late> sortedByDue(List<Late> l) {
        return l.stream().sorted(Comparator.comparing(Late::due)).toList();
    }

    /**
     * One message to these people, unless the ledger shows they already had these exact words today (a second run, a restart or
     * another app instance). If sending fails the claims are given back so the next run can try again.
     */
    private int send(TenantId tenant, HrOnboarding o, HrEmployee emp, List<Late> tasks, List<Recipient> to, LocalDate today) {
        List<Late> ordered = sortedByDue(tasks);
        String title = OnboardingReminderRules.title(emp.getFullName(), ordered.size());
        String message = OnboardingReminderRules.message(emp.getFullName(), ordered, today);
        List<String> claimed = new ArrayList<>();
        List<Recipient> fresh;
        try {
            fresh = ledger.unclaimed(tenant, today, NotificationType.ONBOARDING_TASKS_OVERDUE.name(), o.getId().toString(), title, message, to, claimed);
        } catch (RuntimeException ex) {
            log.warn("Reminder ledger unavailable, sending anyway: {}", ex.getMessage());
            fresh = to;
            claimed.clear();
        }
        if (fresh.isEmpty()) return 0;
        try {
            notificationService.send(NotificationRequest.builder()
                    .tenantId(tenant)
                    .type(NotificationType.ONBOARDING_TASKS_OVERDUE)
                    .title(title)
                    .message(message)
                    .actionUrl("/hr/onboarding")
                    .sourceModule("hr")
                    .sourceEntityId(o.getId().toString())
                    .recipients(fresh)
                    .build());
        } catch (RuntimeException e) {
            claimed.forEach(d -> ledger.release(tenant, today, d));
            throw e;
        }
        return 1;
    }
}
