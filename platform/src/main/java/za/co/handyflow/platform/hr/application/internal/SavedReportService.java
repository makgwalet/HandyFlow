package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.HrSavedReport;
import za.co.handyflow.platform.hr.domain.model.HrSavedReportShare;
import za.co.handyflow.platform.hr.domain.model.SavedReportRules;
import za.co.handyflow.platform.hr.domain.repository.HrSavedReportRepository;
import za.co.handyflow.platform.hr.domain.repository.HrSavedReportShareRepository;
import za.co.handyflow.platform.hr.dto.SavedReportDtos.Colleague;
import za.co.handyflow.platform.hr.dto.SavedReportDtos.SaveRequest;
import za.co.handyflow.platform.hr.dto.SavedReportDtos.View;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.notifications.domain.model.NotificationChannel;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final HrSavedReportShareRepository shares;
    private final ReportAudience audience;
    private final UserRecipientResolver recipients;

    @org.springframework.beans.factory.annotation.Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl = "http://localhost:5173";

    /** My own saved reports, then the ones colleagues have shared with me (a payroll one is left out unless I may see pay). */
    @Transactional(readOnly = true)
    public List<View> mine(TenantId tenant, UUID owner, Set<String> held, LocalDate today) {
        if (owner == null) return List.of();
        List<HrSavedReport> own = repo.forOwner(tenant, owner);
        Map<UUID, List<UUID>> sharedWith = own.isEmpty() ? Map.of() : shares.forReports(own.stream().map(HrSavedReport::getId).toList()).stream()
                .collect(Collectors.groupingBy(HrSavedReportShare::getReportId, Collectors.mapping(HrSavedReportShare::getUserId, Collectors.toList())));
        List<View> out = new ArrayList<>();
        for (HrSavedReport r : own) out.add(view(r, today, false, null, sharedWith.getOrDefault(r.getId(), List.of())));

        List<HrSavedReportShare> toMe = shares.forUser(tenant, owner);
        if (!toMe.isEmpty()) {
            Map<UUID, HrSavedReport> reports = repo.findAllById(toMe.stream().map(HrSavedReportShare::getReportId).toList()).stream()
                    .filter(r -> tenant.getValue().equals(r.getTenantId())).collect(Collectors.toMap(HrSavedReport::getId, r -> r));
            Map<UUID, String> owners = audience.names(tenant, reports.values().stream().map(HrSavedReport::getOwnerId).collect(Collectors.toSet()));
            for (HrSavedReportShare sh : toMe) {
                HrSavedReport r = reports.get(sh.getReportId());
                if (r == null || !SavedReportRules.mayKeep(r.getKind(), HrAccess.compensation(held))) continue;
                out.add(view(r, today, true, owners.get(r.getOwnerId()), List.of()));
            }
        }
        return out;
    }

    /** Colleagues the owner can share with: active users who can open the reports, not the owner. */
    @Transactional(readOnly = true)
    public List<Colleague> colleagues(TenantId tenant, UUID owner) {
        return audience.readers(tenant).stream().filter(c -> !c.id().equals(owner)).toList();
    }

    @Transactional
    public View update(TenantId tenant, UUID owner, Set<String> held, UUID id, SaveRequest req, LocalDate today) {
        HrSavedReport r = owner == null ? null : repo.findForOwner(tenant, owner, id).orElse(null);
        if (r == null) throw new ResourceNotFoundException("SavedReport", id.toString());
        String problem = SavedReportRules.problem(req.name(), req.kind(), req.period(), req.schedule(), req.runDay());
        if (problem != null) throw new IllegalArgumentException(problem);
        if (!SavedReportRules.mayKeep(req.kind(), HrAccess.compensation(held))) throw new AccessDeniedException("You may not keep a payroll report.");
        List<HrSavedReportShare> existing = shares.forReport(r.getId());
        if (SavedReportRules.PAYROLL.equals(req.kind()) && !SavedReportRules.PAYROLL.equals(r.getKind()) && !existing.isEmpty())
            throw new IllegalStateException("Stop sharing this report before making it a payroll report, then share it again with people who may see pay.");
        r.edit(req.name(), req.kind(), req.period(), req.schedule(), req.runDay(), req.email());
        repo.save(r);
        return view(r, today, false, null, existing.stream().map(HrSavedReportShare::getUserId).toList());
    }

    /** Replaces the people one of my reports is shared with. Each must be an active user who may open the reports; for a payroll report, who may see pay. */
    @Transactional
    public View share(TenantId tenant, UUID owner, UUID id, List<UUID> userIds, LocalDate today) {
        HrSavedReport r = owner == null ? null : repo.findForOwner(tenant, owner, id).orElse(null);
        if (r == null) throw new ResourceNotFoundException("SavedReport", id.toString());
        Set<UUID> wanted = new LinkedHashSet<>(userIds == null ? List.<UUID>of() : userIds);
        wanted.remove(owner);
        if (wanted.size() > SavedReportRules.MAX_SHARES) throw new IllegalArgumentException("Share a report with at most " + SavedReportRules.MAX_SHARES + " people.");
        Set<UUID> readers = audience.readers(tenant).stream().map(Colleague::id).collect(Collectors.toSet());
        for (UUID u : wanted) {
            if (!readers.contains(u)) throw new IllegalArgumentException("Someone you chose cannot open the HR reports, so the report cannot be shared with them.");
            if (SavedReportRules.PAYROLL.equals(r.getKind()) && !HrAccess.compensation(audience.held(tenant, u)))
                throw new IllegalArgumentException("Someone you chose may not see pay, so this payroll report cannot be shared with them.");
        }
        List<HrSavedReportShare> existing = shares.forReport(r.getId());
        Set<UUID> have = new HashSet<>();
        for (HrSavedReportShare sh : existing) {
            if (wanted.contains(sh.getUserId())) have.add(sh.getUserId());
            else shares.delete(sh);
        }
        for (UUID u : wanted) if (!have.contains(u)) shares.save(HrSavedReportShare.of(tenant.getValue(), r.getId(), u, owner));
        return view(r, today, false, null, new ArrayList<>(wanted));
    }

    /** A colleague takes a shared report off their own list. The owner's report and its other shares are untouched. */
    @Transactional
    public void leave(TenantId tenant, UUID user, UUID id) {
        HrSavedReportShare sh = user == null ? null : shares.findForUser(tenant, user, id).orElse(null);
        if (sh == null) throw new ResourceNotFoundException("SavedReport", id.toString());
        shares.delete(sh);
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
        HrSavedReport r = repo.save(HrSavedReport.of(tenant.getValue(), owner, req.name(), req.kind(), req.period(), req.schedule(), req.runDay(), req.email()));
        return view(r, today, false, null, List.of());
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
                TenantId tenant = TenantId.of(r.getTenantId());
                List<Recipient> audienceNow = audienceOf(tenant, r);
                if (audienceNow.isEmpty()) continue;
                String title = SavedReportRules.reminderTitle(r.getName());
                String message = SavedReportRules.reminderMessage(r.getKind(), r.getPeriod(), range[0], range[1]);
                notifications.send(NotificationRequest.builder()
                        .tenantId(tenant).type(NotificationType.HR_REPORT_DUE).title(title).message(message)
                        .actionUrl("/hr/hr-reports?saved=" + r.getId())
                        .sourceModule("hr").sourceEntityId(r.getId().toString())
                        .recipients(audienceNow).build());
                // The email is a second, email-only message with a link that works outside the app. Like the one in the app it carries no figures.
                List<Recipient> withEmail = audienceNow.stream().filter(x -> x.email() != null && !x.email().isBlank()).toList();
                if (r.isEmailReminder() && !withEmail.isEmpty()) {
                    notifications.send(NotificationRequest.builder()
                            .tenantId(tenant).type(NotificationType.HR_REPORT_DUE).title(title).message(message)
                            .channels(Set.of(NotificationChannel.EMAIL))
                            .actionUrl(frontendUrl + "/hr/hr-reports?saved=" + r.getId())
                            .sourceModule("hr").sourceEntityId(r.getId().toString())
                            .recipients(withEmail).build());
                }
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

    /** The owner and everyone the report is shared with who still may open it. Someone who has lost the right, or may not see pay for a payroll report, is skipped. */
    private List<Recipient> audienceOf(TenantId tenant, HrSavedReport r) {
        Set<UUID> ids = new LinkedHashSet<>();
        ids.add(r.getOwnerId());
        shares.forReport(r.getId()).forEach(sh -> ids.add(sh.getUserId()));
        List<Recipient> out = new ArrayList<>();
        for (UUID id : ids) {
            Set<String> held = audience.held(tenant, id);
            if (!held.contains("HR_REPORT_READ") || !SavedReportRules.mayKeep(r.getKind(), HrAccess.compensation(held))) continue;
            out.add(recipients.resolveUser(tenant, id).orElse(Recipient.user(id, null, null, null)));
        }
        return out;
    }

    public LocalDate todaySast() { return LocalDate.now(SAST); }

    private static View view(HrSavedReport r, LocalDate today, boolean shared, String ownerName, List<UUID> sharedWith) {
        LocalDate[] range = SavedReportRules.resolve(r.getPeriod(), today);
        return new View(r.getId(), r.getName(), r.getKind(), SavedReportRules.kindLabel(r.getKind()), r.getPeriod(),
                SavedReportRules.periodLabel(r.getPeriod()), shared ? "NONE" : r.getSchedule(), shared ? null : r.getRunDay(), range[0], range[1],
                shared ? null : r.getLastNotifiedOn(), r.getCreatedAt(), !shared && r.isEmailReminder(), shared, ownerName, sharedWith);
    }
}
