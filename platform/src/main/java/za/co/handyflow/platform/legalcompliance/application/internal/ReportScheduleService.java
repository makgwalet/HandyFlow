package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.handyflow.platform.legalcompliance.application.internal.ReportService.Audience;
import za.co.handyflow.platform.legalcompliance.domain.model.ReportSchedule;
import za.co.handyflow.platform.legalcompliance.domain.repository.ReportScheduleRepository;
import za.co.handyflow.platform.legalcompliance.dto.ReportDtos.ReportInfo;
import za.co.handyflow.platform.legalcompliance.dto.ReportDtos.ReportResult;
import za.co.handyflow.platform.legalcompliance.dto.ReportScheduleDtos.*;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Supplier;

/**
 * Scheduled report delivery. When a schedule is due the report is run (as the audience it was created for) and the chosen users get an in-app and email notice
 * saying it is ready, with how many rows it has. The notice never contains the report's data and no file is attached: the report stays behind sign-in and the
 * person's own export permission, and nothing can be sent to an address outside the tenant.
 *
 * A user may only see, change or delete a schedule whose audience is no higher than their own, so staff cannot touch an administrator's schedules. Recipients are
 * tenant users only; each run re-resolves them, so a user who has left is quietly dropped, and a run with nobody left to tell counts as failed.
 */
@Slf4j
@Service("legalComplianceReportScheduleService")
@RequiredArgsConstructor
public class ReportScheduleService {

    private static final String ENTITY = "REPORT_SCHEDULE";

    private final ReportScheduleRepository schedules;
    private final ReportService reports;
    private final RegulatoryAuditService trail;
    private final TenantAdminRecipients recipients;
    private final NotificationService notifications;
    private final PlatformTransactionManager txManager;

    // ── Management ──

    @Transactional(readOnly = true)
    public List<ScheduleResponse> list(TenantId t, Audience who) {
        List<ReportSchedule> all = schedules.findAllForTenant(t).stream().filter(s -> mayAccess(s, who)).toList();
        Map<UUID, String> names = names(t, all.stream().flatMap(s -> s.recipients().stream()).collect(java.util.stream.Collectors.toSet()));
        Map<String, String> titles = titles(who);
        return all.stream().map(s -> toResponse(s, names, titles)).toList();
    }

    @Transactional
    public ScheduleResponse create(TenantId t, CreateRequest r, Audience who, UUID uid, String uname) {
        if (!titles(who).containsKey(r.reportKey())) throw new ResourceNotFoundException("Report", r.reportKey());
        List<UUID> ids = guarded(() -> ScheduleRules.recipients(r.recipientIds()));
        Map<UUID, String> names = requireTenantUsers(t, ids);
        ReportSchedule s = schedules.save(guarded(() -> ReportSchedule.create(t, r.name(), r.reportKey(), r.frequency(), r.runDay(), who.name(), ids, today(), uid, uname)));
        trail.record(t.getValue(), ENTITY, s.getId(), "SCHEDULE_CREATED", s.getName() + " — " + s.getReportKey() + ", " + s.getFrequency() + ", " + ids.size() + " recipient(s)", uid, uname);
        return toResponse(s, names, titles(who));
    }

    @Transactional
    public ScheduleResponse update(TenantId t, UUID id, UpdateRequest r, Audience who, UUID uid, String uname) {
        ReportSchedule s = find(t, id, who);
        List<UUID> ids = guarded(() -> ScheduleRules.recipients(r.recipientIds()));
        Map<UUID, String> names = requireTenantUsers(t, ids);
        guarded(() -> { s.edit(r.name(), r.frequency(), r.runDay(), ids, today()); return null; });
        boolean was = s.isActive();
        if (r.active() != null && r.active() != was) s.setActive(r.active(), today());
        schedules.save(s);
        trail.record(t.getValue(), ENTITY, s.getId(), "SCHEDULE_UPDATED", s.getName() + " — " + s.getFrequency() + ", " + ids.size() + " recipient(s)" + (s.isActive() ? "" : ", paused"), uid, uname);
        return toResponse(s, names, titles(who));
    }

    @Transactional
    public void delete(TenantId t, UUID id, Audience who, UUID uid, String uname) {
        ReportSchedule s = find(t, id, who);
        schedules.delete(s);
        trail.record(t.getValue(), ENTITY, s.getId(), "SCHEDULE_DELETED", s.getName(), uid, uname);
    }

    /** "Send now": runs the schedule immediately without moving its next date. */
    public ScheduleResponse runNow(TenantId t, UUID id, Audience who, UUID uid, String uname) {
        ReportSchedule s = new TransactionTemplate(txManager).execute(st -> find(t, id, who));
        Outcome o = deliver(t, id);
        trail.record(t.getValue(), ENTITY, id, "SCHEDULE_RUN_NOW", s.getName() + ": " + o.summary(), uid, uname);
        return list(t, who).stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow();
    }

    // ── The daily sweep ──

    /** Runs every schedule that is due. Each is claimed, then run, in its own transaction, so one failure never stops the rest and two instances never both send. */
    public int sweep() { return sweep(today()); }

    int sweep(LocalDate today) {
        int ran = 0;
        TransactionTemplate tx = new TransactionTemplate(txManager);
        for (UUID id : schedules.findDueIdsAcrossTenants(today)) {
            try {
                TenantId t = tx.execute(st -> {
                    ReportSchedule s = schedules.findById(id).orElse(null);
                    if (s == null || !s.isDue(today)) return null;
                    s.claim(today);
                    schedules.saveAndFlush(s);
                    return s.getTenantId();
                });
                if (t == null) continue;
                deliver(t, id);
                ran++;
            } catch (OptimisticLockingFailureException e) {
                log.debug("[Legal/Compliance] Schedule {} was claimed by another instance", id);
            } catch (Exception e) {
                log.error("[Legal/Compliance] Report schedule {} failed: {}", id, e.getMessage(), e);
            }
        }
        return ran;
    }

    public record Outcome(boolean ok, int rows, String summary) {}

    /** Runs the report, tells the recipients, and records the outcome. Never throws for a report or delivery problem: that is recorded as a failed run. */
    Outcome deliver(TenantId t, UUID id) {
        ReportSchedule s = schedules.findForTenant(t, id).orElse(null);
        if (s == null) return new Outcome(false, 0, "Schedule no longer exists");
        LocalDate today = today();
        Audience audience = Audience.valueOf(s.getAudience());
        String error = null;
        ReportResult r = null;
        List<za.co.handyflow.platform.notifications.application.Recipient> to = List.of();
        try {
            r = reports.run(t, s.getReportKey(), null, "Scheduled report", audience);
            to = recipients.resolveUsers(t, s.recipients());
            if (to.isEmpty()) error = "None of the chosen people are active users any more";
        } catch (Exception e) {
            error = "Could not run the report: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        if (error == null) {
            try {
                notifications.send(NotificationRequest.builder().tenantId(t).type(NotificationType.LEGALCOMPLIANCE_REPORT_SCHEDULED).title("Scheduled report ready: " + r.title())
                        .message(ScheduleRules.message(s.getName(), r.title(), r.rowCount(), r.truncated())).actionUrl("/legalcompliance/reports").sourceModule("legalcompliance").recipients(to).build());
            } catch (Exception e) {
                error = "Could not send the notification: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
        }
        final String err = error;
        final ReportResult res = r;
        boolean alert = Boolean.TRUE.equals(new TransactionTemplate(txManager).execute(st -> {
            ReportSchedule f = schedules.findForTenant(t, id).orElse(null);
            if (f == null) return false;
            if (err == null) f.recordSuccess(today, res.rowCount()); else f.recordFailure(today, err);
            schedules.save(f);
            return f.justBecameAlertable();
        }));
        if (alert && s.getCreatedBy() != null) tellCreator(t, s, err);
        return err == null ? new Outcome(true, r.rowCount(), r.rowCount() + " row(s), " + to.size() + " notified") : new Outcome(false, 0, "Failed: " + err);
    }

    private void tellCreator(TenantId t, ReportSchedule s, String error) {
        try {
            var to = recipients.resolveUsers(t, List.of(s.getCreatedBy()));
            if (to.isEmpty()) return;
            notifications.send(NotificationRequest.builder().tenantId(t).type(NotificationType.LEGALCOMPLIANCE_REPORT_SCHEDULE_FAILED).title("Scheduled report is failing: " + s.getName())
                    .message("It has failed " + ReportSchedule.FAILURES_BEFORE_ALERT + " times in a row. Last error: " + error).actionUrl("/legalcompliance/reports").sourceModule("legalcompliance").recipients(to).build());
        } catch (Exception e) {
            log.warn("[Legal/Compliance] Could not tell the creator of schedule {}: {}", s.getId(), e.getMessage());
        }
    }

    // ── Helpers ──

    /** A user sees a schedule only if its audience is no higher than theirs. */
    static boolean mayAccess(ReportSchedule s, Audience who) { return Audience.valueOf(s.getAudience()).ordinal() <= who.ordinal(); }

    private ReportSchedule find(TenantId t, UUID id, Audience who) {
        ReportSchedule s = schedules.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("Report schedule", id.toString()));
        if (!mayAccess(s, who)) throw new ResourceNotFoundException("Report schedule", id.toString());
        return s;
    }

    private Map<String, String> titles(Audience who) {
        Map<String, String> m = new HashMap<>();
        for (ReportInfo i : reports.catalogue(who)) m.put(i.key(), i.title());
        return m;
    }

    private Map<UUID, String> names(TenantId t, Collection<UUID> ids) {
        Map<UUID, String> m = new HashMap<>();
        if (ids.isEmpty()) return m;
        for (var r : recipients.resolveUsers(t, ids)) if (r.userId() != null) m.put(r.userId(), r.displayName());
        return m;
    }

    private Map<UUID, String> requireTenantUsers(TenantId t, List<UUID> ids) {
        Map<UUID, String> found = names(t, ids);
        if (!found.keySet().containsAll(ids)) throw new HandyFlowException("Every person must be an active user of this organisation", HttpStatus.BAD_REQUEST, "VALIDATION");
        return found;
    }

    private ScheduleResponse toResponse(ReportSchedule s, Map<UUID, String> names, Map<String, String> titles) {
        List<Recipient> rs = s.recipients().stream().map(u -> new Recipient(u, names.getOrDefault(u, "Former user"))).toList();
        return new ScheduleResponse(s.getId(), s.getName(), s.getReportKey(), titles.getOrDefault(s.getReportKey(), s.getReportKey()), s.getFrequency(), s.getRunDay(), s.getAudience(), rs,
                s.isActive(), s.getNextRunOn(), s.getLastRunOn(), s.getLastStatus(), s.getLastRowCount(), s.getLastError(), s.getCreatedByName());
    }

    private static LocalDate today() { return LocalDate.now(ZoneId.of("Africa/Johannesburg")); }

    private static <T> T guarded(Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalArgumentException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "VALIDATION"); }
        catch (IllegalStateException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATE"); }
    }
}
