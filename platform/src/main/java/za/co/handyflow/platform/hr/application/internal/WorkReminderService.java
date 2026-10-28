package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.HrLetter;
import za.co.handyflow.platform.hr.domain.model.HrPerfGoal;
import za.co.handyflow.platform.hr.domain.model.WorkReminderRules;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.domain.repository.HrLetterRepository;
import za.co.handyflow.platform.hr.domain.repository.HrPerfGoalRepository;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Daily reminders for letters nobody has acknowledged, goals past their date, and goals waiting for approval. A letter reminder goes
 * to the employee and says only the letter's number and date. A goal proposal goes to the employee's manager, or to the
 * administrators when there is no manager. One failing item never stops the others. Which day each goes is decided by
 * {@link WorkReminderRules}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkReminderService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final HrLetterRepository letterRepo;
    private final HrPerfGoalRepository goalRepo;
    private final HrEmployeeRepository employeeRepo;
    private final PerfNotifier notifier;
    private final TenantAdminRecipients adminRecipients;

    /** Sends today's reminders for every company. Returns how many messages went out. */
    @Transactional(readOnly = true)
    public int remindAll(LocalDate today) {
        int sent = 0;
        for (HrLetter l : letterRepo.awaitingAcknowledgement()) {
            try { sent += remindLetter(l, today); }
            catch (RuntimeException e) { log.error("Letter reminder failed for letter {}: {}", l.getId(), e.getMessage()); }
        }
        for (HrPerfGoal g : goalRepo.allOpen()) {
            try { sent += remindGoal(g, today); }
            catch (RuntimeException e) { log.error("Goal reminder failed for goal {}: {}", g.getId(), e.getMessage()); }
        }
        log.info("Letter and goal reminders: {} message(s)", sent);
        return sent;
    }

    private int remindLetter(HrLetter l, LocalDate today) {
        if (!WorkReminderRules.letterReminderToday(l.getStatus(), l.isRequiresAck(), l.getAcknowledgedAt(), l.getIssuedOn(), today)) return 0;
        TenantId tenant = TenantId.of(l.getTenantId());
        Recipient to = active(tenant, l.getEmployeeId()).map(PerfNotifier::contactOf).orElse(null);
        if (to == null) return 0;
        notifier.send(tenant, NotificationType.LETTER_UNACKNOWLEDGED, WorkReminderRules.letterTitle(),
                WorkReminderRules.letterMessage(l.getNumber(), l.getIssuedOn()), l.getId(), "/hr/my-letters", to);
        return 1;
    }

    private int remindGoal(HrPerfGoal g, LocalDate today) {
        TenantId tenant = TenantId.of(g.getTenantId());
        if (WorkReminderRules.goalOverdueToday(g.getStatus(), g.getDueDate(), today)) {
            Recipient to = active(tenant, g.getEmployeeId()).map(PerfNotifier::contactOf).orElse(null);
            if (to == null) return 0;
            notifier.send(tenant, NotificationType.PERF_GOAL_OVERDUE, WorkReminderRules.goalOverdueTitle(),
                    WorkReminderRules.goalOverdueMessage(g.getTitle(), g.getDueDate(), today), g.getId(), "/hr/performance", to);
            return 1;
        }
        LocalDate proposedOn = g.getCreatedAt() == null ? null : g.getCreatedAt().atZone(SAST).toLocalDate();
        if (!WorkReminderRules.proposalReminderToday(g.getStatus(), proposedOn, today)) return 0;
        Optional<HrEmployee> subject = active(tenant, g.getEmployeeId());
        if (subject.isEmpty()) return 0;
        List<Recipient> to = managerOrAdmins(tenant, subject.get());
        if (to.isEmpty()) return 0;
        notifier.send(tenant, NotificationType.PERF_GOAL_WAITING, WorkReminderRules.proposalTitle(subject.get().getFullName()),
                WorkReminderRules.proposalMessage(subject.get().getFullName(), g.getTitle(), proposedOn), g.getId(), "/hr/performance", to);
        return 1;
    }

    private Optional<HrEmployee> active(TenantId tenant, java.util.UUID employeeId) {
        return employeeRepo.findActiveById(tenant, employeeId).filter(x -> !"TERMINATED".equals(x.getStatus()));
    }

    private List<Recipient> managerOrAdmins(TenantId tenant, HrEmployee subject) {
        if (subject.getManagerId() != null) {
            Recipient m = active(tenant, subject.getManagerId()).map(PerfNotifier::contactOf).orElse(null);
            if (m != null) return List.of(m);
        }
        return adminRecipients.resolveTenantAdmins(tenant);
    }
}
