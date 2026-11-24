package za.co.handyflow.platform.contracting.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.contracting.domain.model.*;
import za.co.handyflow.platform.contracting.domain.model.EscalationPlanner.Context;
import za.co.handyflow.platform.contracting.domain.model.EscalationPlanner.Plan;
import za.co.handyflow.platform.contracting.domain.model.EscalationPlanner.Waiting;
import za.co.handyflow.platform.contracting.domain.model.EscalationRules.Absence;
import za.co.handyflow.platform.contracting.domain.model.EscalationRules.Stage;
import za.co.handyflow.platform.contracting.domain.repository.*;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.shared.BusinessCalendar;
import za.co.handyflow.platform.shared.BusinessTime;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.PublicHolidayRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Follows up approvals that are waiting: reminds the people who can decide them, escalates them when they have waited too long, lets an
 * administrator reassign them, steps around people who are out of office, and reports how long decisions take.
 *
 * <p><b>It never decides anything.</b> It does not approve, reject, cancel or use up an approval, and it does not ask for one. Its only
 * writes are the escalation columns of a waiting request, audit events, its own settings, and emails. (A test reads this source to keep it so.)
 *
 * <p>The decisions about <i>what</i> is due and <i>who</i> to tell are made by {@link EscalationPlanner}; this class looks things up and carries
 * the plan out.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalEscalationService {

    static final int SWEEP_BATCH = 200;
    static final int MAX_REPORT_ROWS = 5000;
    static final int MAX_REPORT_DAYS = 366;
    static final int MAX_AWAY_DAYS = 366;
    private static final int PEOPLE_LIMIT = 200;
    private static final String ADMIN_AUTHORITY = "CONTRACTS_ADMIN";
    private static final Set<String> ALL_APPROVER_AUTHORITIES = Arrays.stream(ApprovalAction.values())
            .flatMap(a -> a.approverAuthorities().stream()).collect(Collectors.toUnmodifiableSet());

    private final ContractEscalationSettingsRepository settingsRepo;
    private final ContractClosureDayRepository closureRepo;
    private final ContractApproverAbsenceRepository absenceRepo;
    private final ContractApprovalRepository approvalRepo;
    private final ContractApprovalEventRepository eventRepo;
    private final ContractApprovalService approvals;
    private final TenantAdminRecipients recipients;
    private final EmailService emailService;
    private final BusinessCalendar calendar;
    private final PublicHolidayRepository holidays;
    private final Clock clock;

    @Value("${contracting.signing.base-url:http://localhost:5173}")
    private String baseUrl = "http://localhost:5173";

    // ── settings ─────────────────────────────────────────────────────────────

    /** The saved settings, or the defaults (switched off) when none have been saved. */
    @Transactional(readOnly = true)
    public ContractEscalationSettings settings(TenantId tenant) {
        return settingsRepo.findById(tenant.getValue()).orElseGet(() -> ContractEscalationSettings.create(tenant.getValue()));
    }

    public record SettingsInput(boolean enabled, EscalationRules.Thresholds thresholds, String clockMode, LocalTime workStart, LocalTime workEnd,
                                Collection<DayOfWeek> workDays, boolean skipPublicHolidays, Collection<UUID> escalationUserIds) {}

    @Transactional
    public ContractEscalationSettings saveSettings(TenantId tenant, SettingsInput in, UUID by) {
        List<UUID> people = in.escalationUserIds() == null ? List.of() : in.escalationUserIds().stream().filter(Objects::nonNull).distinct().toList();
        requireActiveUsers(tenant, people, "The people to escalate to");
        ContractEscalationSettings s = settingsRepo.findById(tenant.getValue()).orElseGet(() -> ContractEscalationSettings.create(tenant.getValue()));
        s.update(in.enabled(), in.thresholds(), in.clockMode(), in.workStart(), in.workEnd(), in.workDays(), in.skipPublicHolidays(), people, by, clock.instant());
        log.info("[Contracting] Approval escalation settings saved tenant={} enabled={} by={}", tenant.getValue(), s.isEnabled(), by);
        return settingsRepo.save(s);
    }

    // ── closure days ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ContractClosureDay> closures(TenantId tenant) {
        return closureRepo.findFrom(tenant.getValue(), calendar.today(tenant));
    }

    @Transactional
    public ContractClosureDay addClosure(TenantId tenant, LocalDate day, String reason, UUID by) {
        LocalDate today = calendar.today(tenant);
        if (day == null) throw new IllegalArgumentException("Choose the day");
        if (day.isBefore(today)) throw new IllegalArgumentException("Choose today or a later day");
        if (day.isAfter(today.plusYears(3))) throw new IllegalArgumentException("Choose a day within the next three years");
        if (closureRepo.existsOn(tenant.getValue(), day)) throw new IllegalStateException("That day is already marked as closed");
        return closureRepo.save(ContractClosureDay.create(tenant.getValue(), day, reason, by));
    }

    @Transactional
    public void removeClosure(TenantId tenant, UUID id) {
        ContractClosureDay c = closureRepo.findOne(tenant.getValue(), id).orElseThrow(() -> new ResourceNotFoundException("Closure day", id.toString()));
        if (c.getClosedOn().isBefore(calendar.today(tenant)))
            throw new IllegalStateException("A day that has passed stays on record, because it changed how long requests waited");
        closureRepo.delete(c);
    }

    // ── out of office ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ContractApproverAbsence> absences(TenantId tenant) {
        return absenceRepo.findCurrentAndUpcoming(tenant.getValue(), calendar.today(tenant));
    }

    /** Records time away. A person may record their own; recording for someone else needs a contracts administrator. */
    @Transactional
    public ContractApproverAbsence addAbsence(TenantId tenant, UUID userId, LocalDate from, LocalDate to, UUID delegate, String reason, UUID by, boolean isAdmin) {
        if (userId == null) userId = by;
        if (!userId.equals(by) && !isAdmin) throw new AccessDeniedException("You can only record your own time away");
        LocalDate today = calendar.today(tenant);
        if (from == null || to == null) throw new IllegalArgumentException("Give the first and last day away");
        if (to.isBefore(today)) throw new IllegalArgumentException("The last day away is already past");
        if (to.isBefore(from)) throw new IllegalArgumentException("The last day away cannot be before the first");
        if (java.time.temporal.ChronoUnit.DAYS.between(from, to) >= MAX_AWAY_DAYS) throw new IllegalArgumentException("Time away can be at most " + MAX_AWAY_DAYS + " days at a time");
        List<UUID> named = delegate == null ? List.of(userId) : List.of(userId, delegate);
        requireActiveUsers(tenant, named, "The people named");
        if (delegate != null && !pool(tenant, ALL_APPROVER_AUTHORITIES).containsKey(delegate))
            throw new IllegalArgumentException("The person covering must be someone who can approve contracts");
        return absenceRepo.save(ContractApproverAbsence.create(tenant.getValue(), userId, from, to, delegate, reason, by));
    }

    @Transactional
    public void cancelAbsence(TenantId tenant, UUID id, UUID by, boolean isAdmin) {
        ContractApproverAbsence away = absenceRepo.findOne(tenant.getValue(), id).orElseThrow(() -> new ResourceNotFoundException("Time away", id.toString()));
        if (!away.getUserId().equals(by) && !isAdmin) throw new AccessDeniedException("You can only cancel your own time away");
        away.cancel(by);
        absenceRepo.save(away);
    }

    // ── reassignment ─────────────────────────────────────────────────────────

    /**
     * Names who is reminded first (null hands it back to everyone who can approve). It is routing, not a restriction: anyone with the right
     * role can still decide it, and the time it has already waited is not reset. Only a contracts administrator, or the person it is
     * currently assigned to, may reassign it.
     */
    @Transactional
    public ContractApproval reassign(TenantId tenant, UUID approvalId, UUID toUser, UUID by, Collection<String> authorities, String note) {
        ContractApproval a = approvalRepo.findForUpdate(tenant.getValue(), approvalId).orElseThrow(() -> new ResourceNotFoundException("Approval", approvalId.toString()));
        boolean admin = authorities != null && authorities.contains(ADMIN_AUTHORITY);
        if (!admin && !by.equals(a.getAssignedTo()))
            throw new AccessDeniedException("Only a contracts administrator, or the person it is assigned to, can reassign it");
        if (note != null && note.length() > 500) throw new IllegalArgumentException("Keep the note to 500 characters");
        Map<UUID, Recipient> candidates = toUser == null ? Map.of() : pool(tenant, a.getAction().approverAuthorities());
        if (toUser != null && !candidates.containsKey(toUser))
            throw new IllegalArgumentException("That person cannot approve this kind of request");
        UUID from = a.getAssignedTo();
        a.assign(toUser, by);
        approvalRepo.save(a);

        Map<UUID, Recipient> names = byId(tenant, Arrays.asList(from, toUser, by));
        String detail = (from == null ? "Not assigned" : "Was with " + nameOf(names, from)) + ". "
                + (toUser == null ? "Handed back to everyone who can approve." : "Now with " + nameOf(names, toUser) + ".")
                + (note == null || note.isBlank() ? "" : " Note: " + note.trim());
        event(a, "REASSIGNED", by, detail);
        log.info("[Contracting] Approval {} reassigned tenant={} to={} by={}", a.getId(), tenant.getValue(), toUser, by);

        Recipient target = toUser == null ? null : names.get(toUser);
        if (target != null && target.email() != null && !target.email().isBlank()) {
            ContractEscalationSettings s = settings(tenant);
            Instant now = clock.instant();
            ZoneId zone = calendar.zone(tenant);
            long waited = waitedMinutes(tenant, s, a, now, zone);
            var mail = EscalationMessages.assigned(a.getAction(), a.getSubjectLabel(), nameOf(names, by), nameOf(byId(tenant, List.of(a.getRequestedBy())), a.getRequestedBy()),
                    BusinessTime.format(zone, a.getRequestedAt()), waited, s.isBusinessHours(), link(), note);
            try { emailService.send(target.email(), mail.subject(), mail.html()); }
            catch (Exception e) { log.warn("[Contracting] Could not tell {} about an assignment: {}", toUser, e.getMessage()); }
        }
        return a;
    }

    // ── the sweep ────────────────────────────────────────────────────────────

    /** Waiting requests that still have a step left, oldest first (at most {@value #SWEEP_BATCH}). */
    @Transactional(readOnly = true)
    public List<UUID> pendingIds(UUID tenantId) {
        return approvalRepo.findPendingIds(tenantId, PageRequest.of(0, SWEEP_BATCH));
    }

    /**
     * Looks at one waiting request and, if a reminder or the escalation is due, records it and tells the right people. Called once per request
     * by the scheduler, each in its own transaction so one failure cannot hold up the rest.
     *
     * @return true when a step was taken
     */
    @Transactional
    public boolean process(TenantId tenant, UUID approvalId) {
        ContractEscalationSettings s = settingsRepo.findById(tenant.getValue()).filter(ContractEscalationSettings::isEnabled).orElse(null);
        if (s == null) return false;
        // Reading it through the approval service marks it out of date when what it covers has changed, so a stale request is not chased.
        if (!approvals.get(tenant, approvalId).isPending()) return false;
        ContractApproval a = approvalRepo.findForUpdate(tenant.getValue(), approvalId).orElse(null);
        if (a == null || !a.isPending()) return false;

        Instant now = clock.instant();
        ZoneId zone = calendar.zone(tenant);
        ApprovalWorkingTime wt = workingTime(tenant, s, zone, a.getRequestedAt(), now);
        Waiting w = new Waiting(a.getRequestedBy(), a.getAssignedTo(), a.getEscalationStage(), a.getRequestedAt());
        if (EscalationPlanner.stageDue(w, s.thresholds(), wt, s.getEnabledSince(), now).isEmpty()) return false;

        LocalDate today = BusinessTime.today(zone, now);
        Map<UUID, Recipient> pool = pool(tenant, a.getAction().approverAuthorities());
        Map<UUID, Recipient> admins = administrators(tenant);
        List<Absence> away = absenceRepo.findActiveOn(tenant.getValue(), today).stream().map(ContractApproverAbsence::toRule).toList();
        Context ctx = new Context(s.thresholds(), wt, s.getEnabledSince(), new ArrayList<>(pool.keySet()), s.escalationUsers(),
                new ArrayList<>(admins.keySet()), away, today, now);
        Plan plan = EscalationPlanner.plan(w, ctx).orElse(null);
        if (plan == null) return false;

        int before = a.getEscalationStage();
        Set<UUID> wanted = new LinkedHashSet<>(plan.recipients());
        wanted.add(a.getRequestedBy());
        Map<UUID, Recipient> people = byId(tenant, wanted);
        String reminded = before >= 1 && plan.stage() == Stage.ESCALATED
                ? names(EscalationRules.routeReminder(a.getAssignedTo(), pool.keySet(), a.getRequestedBy(), away, today).people(), people, pool)
                : "";

        // Marked before anyone is told: if sending fails, the next sweep must not repeat the step every fifteen minutes.
        a.reachStage(plan.stage(), now);
        approvalRepo.save(a);
        String told = names(plan.recipients(), people, pool);
        event(a, eventType(plan.stage()), null, "Waited " + EscalationMessages.duration(plan.waitedMinutes()) + (s.isBusinessHours() ? " of working time" : "")
                + (plan.fellBackToEscalation() ? ". No approver could be reached, so the escalation contacts were told" : "")
                + (plan.skippedEarlierSteps() ? ". Earlier steps were skipped" : "")
                + (plan.noOneToTell() ? ". No one could be told" : ". Told: " + told));

        String requestedBy = nameOf(people, a.getRequestedBy());
        String requestedWhen = BusinessTime.format(zone, a.getRequestedAt());
        int failed = 0;
        for (UUID id : plan.recipients()) {
            Recipient r = people.get(id);
            if (r == null || r.email() == null || r.email().isBlank()) { failed++; continue; }
            var mail = plan.stage() == Stage.ESCALATED
                    ? EscalationMessages.escalation(a.getAction(), a.getSubjectLabel(), requestedBy, requestedWhen, plan.waitedMinutes(), s.isBusinessHours(), link(), reminded, plan.skippedEarlierSteps())
                    : EscalationMessages.reminder(plan.stage(), a.getAction(), a.getSubjectLabel(), requestedBy, requestedWhen, plan.waitedMinutes(), s.isBusinessHours(), link(), plan.fellBackToEscalation());
            if (!send(r, mail)) failed++;
        }
        if (plan.tellRequester()) {
            Recipient asker = people.get(a.getRequestedBy());
            if (asker != null && asker.email() != null && !asker.email().isBlank())
                send(asker, EscalationMessages.toRequester(a.getAction(), a.getSubjectLabel(), plan.waitedMinutes(), s.isBusinessHours(), link(), !plan.noOneToTell()));
        }
        if (failed > 0) event(a, "NOTIFY_FAILED", null, failed + " of " + Math.max(plan.recipients().size(), failed) + " could not be told (no address, or the email failed)");
        log.info("[Contracting] Approval {} tenant={} reached {} told={} failed={}", a.getId(), tenant.getValue(), plan.stage(), plan.recipients().size(), failed);
        return true;
    }

    // ── response-time report ─────────────────────────────────────────────────

    public record SlaReport(ApprovalSla.Result result, Map<String, String> approverNames, Instant from, Instant to,
                            int targetHours, boolean workingClock) {}

    @Transactional(readOnly = true)
    public SlaReport sla(TenantId tenant, LocalDate fromDay, LocalDate toDay) {
        if (fromDay == null || toDay == null || toDay.isBefore(fromDay)) throw new IllegalArgumentException("Choose a period that ends on or after the day it starts");
        if (java.time.temporal.ChronoUnit.DAYS.between(fromDay, toDay) >= MAX_REPORT_DAYS) throw new IllegalArgumentException("Choose a period of at most " + MAX_REPORT_DAYS + " days");
        ContractEscalationSettings s = settings(tenant);
        ZoneId zone = calendar.zone(tenant);
        Instant from = fromDay.atStartOfDay(zone).toInstant();
        Instant to = toDay.plusDays(1).atStartOfDay(zone).toInstant();
        Instant now = clock.instant();
        List<ContractApproval> rows = approvalRepo.findRequestedBetween(tenant.getValue(), from, to);
        if (rows.size() > MAX_REPORT_ROWS) throw new IllegalArgumentException("That period has too many requests to report on. Choose a shorter one.");
        ApprovalWorkingTime wt = workingTime(tenant, s, zone, from, now.isAfter(to) ? now : to);
        List<ApprovalSla.Sample> samples = rows.stream().map(a -> new ApprovalSla.Sample(a.getId(), a.getAction().name(), a.getStatus(), a.getRequestedBy(),
                a.getRequestedAt(), a.getDecidedAt(), a.getDecidedBy(), a.getEscalationStage(), a.isSoleUserException())).toList();
        int target = s.getEscalateHours();
        ApprovalSla.Result r = ApprovalSla.compute(samples, wt, now, target * 60L);
        Set<UUID> approverIds = r.byApprover().stream().map(g -> UUID.fromString(g.key())).collect(Collectors.toSet());
        Map<String, String> names = new LinkedHashMap<>();
        for (Recipient p : recipients.resolveUsers(tenant, approverIds)) if (p.userId() != null) names.put(p.userId().toString(), displayName(p));
        return new SlaReport(r, names, from, to, target, s.isBusinessHours());
    }

    // ── internals ────────────────────────────────────────────────────────────

    private String link() { return baseUrl.replaceAll("/+$", "") + "/contracts/approvals"; }

    private boolean send(Recipient to, EscalationMessages.Mail mail) {
        try {
            emailService.send(to.email(), mail.subject(), mail.html());
            return true;
        } catch (Exception e) {
            log.warn("[Contracting] Could not email {} about an approval: {}", to.userId(), e.getMessage());
            return false;
        }
    }

    private void event(ContractApproval a, String type, UUID actor, String detail) {
        eventRepo.save(ContractApprovalEvent.of(a.getTenantId(), a.getId(), type, actor, detail));
    }

    private static String eventType(Stage s) {
        return switch (s) {
            case REMINDER_1 -> "REMINDER_1_SENT";
            case REMINDER_2 -> "REMINDER_2_SENT";
            case ESCALATED -> "ESCALATED";
            case NONE -> throw new IllegalArgumentException("Nothing was sent");
        };
    }

    /** The clock this organisation's approvals wait on, with the closed dates (public holidays and closure days) that fall in the window. */
    private ApprovalWorkingTime workingTime(TenantId tenant, ContractEscalationSettings s, ZoneId zone, Instant from, Instant to) {
        if (!s.isBusinessHours()) return ApprovalWorkingTime.calendar(zone);
        LocalDate first = from.atZone(zone).toLocalDate().minusDays(1);
        LocalDate last = to.atZone(zone).toLocalDate().plusDays(1);
        Set<LocalDate> closed = new HashSet<>(closureRepo.datesBetween(tenant.getValue(), first, last));
        if (s.isSkipPublicHolidays()) closed.addAll(holidays.datesBetween(first, last));
        return ApprovalWorkingTime.businessHours(zone, s.getWorkStart(), s.getWorkEnd(), s.workDaySet(), closed);
    }

    private long waitedMinutes(TenantId tenant, ContractEscalationSettings s, ContractApproval a, Instant now, ZoneId zone) {
        ApprovalWorkingTime wt = workingTime(tenant, s, zone, a.getRequestedAt(), now);
        return wt.minutesBetween(a.getRequestedAt(), now);
    }

    /** Active users who hold any of the permissions, by id. Looks at the first {@value #PEOPLE_LIMIT} for each permission. */
    private Map<UUID, Recipient> pool(TenantId tenant, Collection<String> authorities) {
        Map<UUID, Recipient> out = new LinkedHashMap<>();
        for (String authority : new TreeSet<>(authorities))
            for (Recipient r : recipients.resolveByPermission(tenant, authority, PEOPLE_LIMIT))
                if (r.userId() != null) out.putIfAbsent(r.userId(), r);
        return out;
    }

    /** Contracts administrators; the organisation's administrators when none hold the permission. */
    private Map<UUID, Recipient> administrators(TenantId tenant) {
        Map<UUID, Recipient> out = pool(tenant, List.of(ADMIN_AUTHORITY));
        if (!out.isEmpty()) return out;
        for (Recipient r : recipients.resolveTenantAdmins(tenant)) if (r.userId() != null) out.putIfAbsent(r.userId(), r);
        return out;
    }

    private Map<UUID, Recipient> byId(TenantId tenant, Collection<UUID> ids) {
        List<UUID> wanted = ids.stream().filter(Objects::nonNull).distinct().toList();
        Map<UUID, Recipient> out = new LinkedHashMap<>();
        if (wanted.isEmpty()) return out;
        for (Recipient r : recipients.resolveUsers(tenant, wanted)) if (r.userId() != null) out.putIfAbsent(r.userId(), r);
        return out;
    }

    private void requireActiveUsers(TenantId tenant, Collection<UUID> ids, String who) {
        if (ids.isEmpty()) return;
        if (!byId(tenant, ids).keySet().containsAll(ids))
            throw new IllegalArgumentException(who + " must be active users of this organisation who have an email address");
    }

    private static String displayName(Recipient r) {
        String n = r.displayName() == null ? "" : r.displayName().trim();
        return !n.isEmpty() ? n : r.email() == null ? "A colleague" : r.email();
    }

    private static String nameOf(Map<UUID, Recipient> people, UUID id) {
        Recipient r = id == null ? null : people.get(id);
        return r == null ? "a colleague" : displayName(r);
    }

    private static String names(Collection<UUID> ids, Map<UUID, Recipient> primary, Map<UUID, Recipient> fallback) {
        return ids.stream().map(id -> primary.containsKey(id) ? displayName(primary.get(id)) : fallback.containsKey(id) ? displayName(fallback.get(id)) : "a colleague")
                .collect(Collectors.joining(", "));
    }
}
