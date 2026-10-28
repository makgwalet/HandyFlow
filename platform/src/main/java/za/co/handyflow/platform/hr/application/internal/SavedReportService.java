package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.HrSavedReport;
import za.co.handyflow.platform.hr.domain.model.SavedReportRules;
import za.co.handyflow.platform.hr.domain.repository.HrSavedReportRepository;
import za.co.handyflow.platform.hr.dto.SavedReportDtos.SaveRequest;
import za.co.handyflow.platform.hr.dto.SavedReportDtos.View;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Saved report views and their reminders. Everything here belongs to one person: nobody lists, opens or deletes another person's
 * saved reports. A reminder says that a report is ready and for which dates, never what it shows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SavedReportService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final HrSavedReportRepository repo;
    private final NotificationService notifications;

    @Transactional(readOnly = true)
    public List<View> mine(TenantId tenant, UUID owner, LocalDate today) {
        if (owner == null) return List.of();
        return repo.forOwner(tenant, owner).stream().map(r -> view(r, today)).toList();
    }

    @Transactional
    public View save(TenantId tenant, UUID owner, Set<String> held, SaveRequest req, LocalDate today) {
        if (owner == null) throw new AccessDeniedException("Your user could not be identified, so the report cannot be saved.");
        String problem = SavedReportRules.problem(req.name(), req.kind(), req.period(), req.schedule(), req.runDay());
        if (problem != null) throw new IllegalArgumentException(problem);
        if (!SavedReportRules.mayKeep(req.kind(), HrAccess.compensation(held))) throw new AccessDeniedException("You may not keep a payroll report.");
        if (repo.countForOwner(tenant, owner) >= SavedReportRules.MAX_PER_PERSON) {
            throw new IllegalStateException("You can keep " + SavedReportRules.MAX_PER_PERSON + " saved reports. Delete one first.");
        }
        HrSavedReport r = repo.save(HrSavedReport.of(tenant.getValue(), owner, req.name(), req.kind(), req.period(), req.schedule(), req.runDay()));
        return view(r, today);
    }

    @Transactional
    public void delete(TenantId tenant, UUID owner, UUID id) {
        HrSavedReport r = owner == null ? null : repo.findForOwner(tenant, owner, id).orElse(null);
        if (r == null) throw new ResourceNotFoundException("SavedReport", id.toString());
        repo.delete(r);
    }

    /** Sends today's reminders for every company and returns how many went out. One failing report never stops the others. */
    @Transactional
    public int remindAll(LocalDate today) {
        int sent = 0;
        for (HrSavedReport r : repo.scheduled()) {
            if (!SavedReportRules.dueToday(r.getSchedule(), r.getRunDay(), r.getLastNotifiedOn(), today)) continue;
            try {
                LocalDate[] range = SavedReportRules.resolve(r.getPeriod(), today);
                notifications.send(NotificationRequest.builder()
                        .tenantId(TenantId.of(r.getTenantId())).type(NotificationType.HR_REPORT_DUE)
                        .title(SavedReportRules.reminderTitle(r.getName()))
                        .message(SavedReportRules.reminderMessage(r.getKind(), r.getPeriod(), range[0], range[1]))
                        .actionUrl("/hr/hr-reports?saved=" + r.getId())
                        .sourceModule("hr").sourceEntityId(r.getId().toString())
                        .recipients(List.of(Recipient.user(r.getOwnerId(), null, null, null))).build());
                r.notified(today);
                repo.save(r);
                sent++;
            } catch (RuntimeException e) {
                log.error("Saved report reminder failed for report {}: {}", r.getId(), e.getMessage());
            }
        }
        log.info("Saved report reminders: {} message(s)", sent);
        return sent;
    }

    public LocalDate todaySast() { return LocalDate.now(SAST); }

    private static View view(HrSavedReport r, LocalDate today) {
        LocalDate[] range = SavedReportRules.resolve(r.getPeriod(), today);
        return new View(r.getId(), r.getName(), r.getKind(), SavedReportRules.kindLabel(r.getKind()), r.getPeriod(),
                SavedReportRules.periodLabel(r.getPeriod()), r.getSchedule(), r.getRunDay(), range[0], range[1], r.getLastNotifiedOn(), r.getCreatedAt());
    }
}
