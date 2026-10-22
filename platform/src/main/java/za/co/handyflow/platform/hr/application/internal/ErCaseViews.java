package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.ErDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Turns cases into what the screens read, and works out what the person looking can do next. */
@Component
@RequiredArgsConstructor
class ErCaseViews {

    private final HrErCaseRepository caseRepo;
    private final HrErChargeRepository chargeRepo;
    private final HrErStepRepository stepRepo;
    private final HrErEventRepository eventRepo;
    private final HrEmployeeRepository employeeRepo;
    private final ErPolicyService policyService;
    private final za.co.handyflow.platform.shared.PublicHolidayRepository holidayRepo;

    /** Working days after the grievance was lodged, skipping weekends and public holidays. */
    LocalDate responseDue(LocalDate lodgedOn, ErPolicy policy) {
        Set<LocalDate> holidays = new HashSet<>(holidayRepo.datesBetween(lodgedOn, lodgedOn.plusDays(policy.grievanceResponseDays() * 2L + 14)));
        return GrievanceRules.responseDue(lodgedOn, policy, holidays);
    }

    // ── The list ──────────────────────────────────────────────────────────────────────────────────

    List<Row> rows(TenantId tenantId, List<HrErCase> cases, LocalDate today) {
        ErPolicy policy = policyService.get(tenantId);
        Map<UUID, String> names = names(cases.stream().map(HrErCase::getEmployeeId).collect(Collectors.toSet()));
        return cases.stream().map(c -> row(c, names.get(c.getEmployeeId()), policy, today)).toList();
    }

    Row row(HrErCase c, String employeeName, ErPolicy policy, LocalDate today) {
        LocalDate due = null;
        String label = null;
        boolean overdue = false;
        String s = c.getStatus();
        if (ErRules.GRIEVANCE.equals(c.getCaseType()) && (ErRules.REPORTED.equals(s) || ErRules.INVESTIGATING.equals(s))) {
            due = responseDue(c.getReportedOn(), policy);
            label = "Response due";
            overdue = today.isAfter(due);
        } else if (ErRules.NOTICE_ISSUED.equals(s)) {
            due = c.getHearingDate();
            label = "Meeting";
            overdue = due != null && due.isBefore(today);
        } else if (ErRules.DECIDED.equals(s)) {
            if (c.getCommunicatedOn() == null) {
                label = "Tell the employee";
                overdue = c.getDecidedOn() != null && c.getDecidedOn().isBefore(today);
            } else {
                due = DisciplineRules.appealDeadline(c.getCommunicatedOn(), policy);
                label = "Appeal period ends";
            }
        }
        return new Row(c.getId(), c.getCaseNumber(), c.getCaseType(), c.getCategory(), c.getTitle(), s, c.getEmployeeId(), employeeName,
                c.isConfidential(), c.isLegacy(), c.getReportedOn(), c.getOutcome(), due, label, overdue);
    }

    // ── One case, for HR ──────────────────────────────────────────────────────────────────────────

    Detail detail(TenantId tenantId, HrErCase c, Set<String> held, UUID userId, LocalDate today) {
        ErPolicy policy = policyService.get(tenantId);
        HrEmployee emp = employeeRepo.findActiveById(tenantId, c.getEmployeeId()).orElse(null);
        Map<UUID, String> names = names(idsOf(c));
        List<HrErCharge> charges = chargeRepo.forCase(c.getId());
        List<HrErStep> steps = stepRepo.forCase(c.getId());
        Set<String> done = steps.stream().filter(HrErStep::isDone).map(HrErStep::getCode).collect(Collectors.toSet());
        Map<UUID, String> users = new HashMap<>();
        List<EventView> events = eventRepo.forCase(c.getId()).stream()
                .map(e -> new EventView(e.getAction(), e.getNote(), e.isVisibleToEmployee(), userName(tenantId, e.getDoneBy(), users), e.getDoneAt()))
                .toList();

        Row row = row(c, emp == null ? "Unknown" : emp.getFullName(), policy, today);
        Advice advice = advice(tenantId, c, policy, today, row);
        return new Detail(c.getId(), c.getCaseNumber(), c.getCaseType(), c.getCategory(), c.getTitle(), c.getDescription(), c.getStatus(),
                c.isConfidential(), c.isLegacy(), party(c.getEmployeeId(), names), emp == null ? null : emp.getJobTitle(), emp == null ? null : emp.getDepartment(),
                c.getIncidentDate(), c.getReportedOn(),
                c.getOwnerUserId() == null ? null : new Party(c.getOwnerUserId(), userName(tenantId, c.getOwnerUserId(), users)),
                party(c.getInitiatorId(), names), party(c.getChairId(), names), party(c.getRespondentId(), names),
                hearing(c), decision(c), appeal(c, policy, names), suspension(c, today), review(c, policy),
                c.getClosedOn(), c.getClosedReason(),
                charges.stream().map(x -> new Charge(x.getId(), x.getSeq(), x.getCharge(), x.getFinding(), x.getFindingNote())).toList(),
                steps.stream().map(x -> new StepView(x.getCode(), x.getLabel(), x.getDoneOn(), x.getNote())).toList(),
                events, advice, actions(c, held, today, policy), policyView(policy), outcomes(c),
                ErRules.INCAPACITY.equals(c.getCaseType()) ? IncapacityRules.missing(c.getCategory(), done) : List.of());
    }

    private Advice advice(TenantId tenantId, HrErCase c, ErPolicy policy, LocalDate today, Row row) {
        if (ErRules.GRIEVANCE.equals(c.getCaseType())) {
            LocalDate due = responseDue(c.getReportedOn(), policy);
            return new Advice(List.of(), List.of(), null, due, row.overdue());
        }
        if (!ErRules.DISCIPLINARY.equals(c.getCaseType())) return null;
        List<HrErCase> others = caseRepo.forEmployee(tenantId, c.getEmployeeId()).stream()
                .filter(x -> !x.getId().equals(c.getId()) && ErRules.DISCIPLINARY.equals(x.getCaseType()) && x.getDecidedOn() != null
                        && !ErRules.WITHDRAWN.equals(x.getStatus()) && x.getOutcome() != null)
                .toList();
        List<WarningView> active = new ArrayList<>();
        List<WarningView> unknown = new ArrayList<>();
        List<DisciplineRules.Warning> activeWarnings = new ArrayList<>();
        for (HrErCase o : others) {
            DisciplineRules.Warning w = new DisciplineRules.Warning(o.getOutcome(), o.getDecidedOn(), o.getWarningValidUntil());
            WarningView v = new WarningView(o.getId(), o.getCaseNumber(), o.getOutcome(), o.getDecidedOn(), o.getWarningValidUntil());
            if (!DisciplineRules.active(List.of(w), today).isEmpty()) {
                active.add(v);
                activeWarnings.add(w);
            } else if (!DisciplineRules.unknownValidity(List.of(w)).isEmpty()) {
                unknown.add(v);
            }
        }
        return new Advice(active, unknown, DisciplineRules.guidance(activeWarnings), null, false);
    }

    private static Hearing hearing(HrErCase c) {
        return new Hearing(c.getNoticeIssuedAt(), c.getHearingDate(), c.getHearingTime(), c.getHearingVenue(), c.getHearingHeldOn(),
                c.getEmployeeAttended(), c.getRepresentative(), c.getHearingNotes());
    }

    private static Decision decision(HrErCase c) {
        LocalDate ccma = c.getDismissalRightsExplainedOn() != null && c.getDecidedOn() != null && DisciplineRules.DISMISSAL.equals(c.getOutcome())
                ? DisciplineRules.ccmaDeadline(c.getCommunicatedOn() != null ? c.getCommunicatedOn() : c.getDecidedOn()) : null;
        return new Decision(c.getOutcome(), c.getDecidedOn(), c.getDecisionNote(), c.getWarningMonths(), c.getWarningValidUntil(),
                c.getEmployeeResponse(), c.getCommunicatedOn(), c.getDismissalRightsExplainedOn(), ccma, c.isAcknowledged(), c.getAcknowledgedAt());
    }

    private Appeal appeal(HrErCase c, ErPolicy policy, Map<UUID, String> names) {
        LocalDate deadline = c.getCommunicatedOn() == null ? null : DisciplineRules.appealDeadline(c.getCommunicatedOn(), policy);
        return new Appeal(deadline, c.getAppealLodgedOn(), c.getAppealGrounds(), party(c.getAppealDeciderId(), names), c.getAppealDecidedOn(),
                c.getAppealOutcome(), c.getAppealNote());
    }

    private static Suspension suspension(HrErCase c, LocalDate today) {
        if (c.getSuspendedFrom() == null) return null;
        boolean active = !c.getSuspendedFrom().isAfter(today) && (c.getSuspendedTo() == null || !c.getSuspendedTo().isBefore(today));
        return new Suspension(c.getSuspendedFrom(), c.getSuspendedTo(), c.getSuspensionReason(), active);
    }

    private static Review review(HrErCase c, ErPolicy policy) {
        if (c.getReviewFrom() == null) return null;
        return new Review(c.getReviewFrom(), c.getReviewTo(), IncapacityRules.reviewShort(c.getReviewFrom(), c.getReviewTo(), policy));
    }

    static PolicyView policyView(ErPolicy p) {
        return new PolicyView(p.noticeHours(), p.verbalMonths(), p.writtenMonths(), p.finalMonths(), p.appealDays(), p.grievanceResponseDays(), p.minReviewDays());
    }

    private static List<String> outcomes(HrErCase c) {
        return switch (c.getCaseType()) {
            case ErRules.DISCIPLINARY -> DisciplineRules.SANCTIONS;
            case ErRules.GRIEVANCE -> List.of(GrievanceRules.UPHELD, GrievanceRules.PARTLY_UPHELD, GrievanceRules.NOT_UPHELD);
            default -> IncapacityRules.outcomes(c.getCategory());
        };
    }

    // ── What the person can do next ───────────────────────────────────────────────────────────────

    /** Action names the screen turns into buttons. The service checks each one again when it is used. */
    List<String> actions(HrErCase c, Set<String> held, LocalDate today, ErPolicy policy) {
        List<String> out = new ArrayList<>();
        // A dismissal ends employment only when someone says so, and that can still be done after the case is closed.
        if (ErRules.canDecide(held) && DisciplineRules.DISMISSAL.equals(c.getOutcome()) && c.getCommunicatedOn() != null
                && (ErRules.DECIDED.equals(c.getStatus()) || ErRules.CLOSED.equals(c.getStatus()))
                && employeeRepo.findActiveById(TenantId.of(c.getTenantId()), c.getEmployeeId()).map(e -> !"TERMINATED".equals(e.getStatus())).orElse(false))
            out.add("END_EMPLOYMENT");
        if (!ErRules.isOpen(c.getStatus())) return out;
        boolean manage = ErRules.canManage(held);
        boolean decide = ErRules.canDecide(held);
        String type = c.getCaseType();
        boolean disc = ErRules.DISCIPLINARY.equals(type), grv = ErRules.GRIEVANCE.equals(type), inc = ErRules.INCAPACITY.equals(type);
        if (manage) { out.add("ASSIGN"); out.add("PARTIES"); out.add("CONFIDENTIAL"); }
        switch (c.getStatus()) {
            case ErRules.REPORTED -> {
                if (manage) { out.add("INVESTIGATE"); out.add("WITHDRAW"); }
                if (disc && decide) { out.add("INFORMAL"); out.add("CLOSE_NO_CASE"); }
            }
            case ErRules.INVESTIGATING -> {
                if (manage && (disc || inc)) out.add("NOTICE");
                if (manage) out.add("WITHDRAW");
                if (disc && decide) { out.add("INFORMAL"); out.add("CLOSE_NO_CASE"); }
                if (grv && decide) out.add("DECIDE");
            }
            case ErRules.NOTICE_ISSUED -> {
                if (manage) { out.add("HEARING"); out.add("NOTICE"); out.add("WITHDRAW"); }
            }
            case ErRules.HEARING_HELD -> {
                if (decide) out.add("DECIDE");
                if (manage && inc) out.add("RETURN_TO_INVESTIGATION");
                if (manage) out.add("WITHDRAW");
            }
            case ErRules.DECIDED -> {
                if (manage && c.getCommunicatedOn() == null) out.add("COMMUNICATE");
                if (manage && c.getCommunicatedOn() != null) {
                    if (c.getAppealLodgedOn() == null) out.add("APPEAL");
                    if (today.isAfter(DisciplineRules.appealDeadline(c.getCommunicatedOn(), policy))) out.add("CLOSE");
                }
            }
            case ErRules.APPEALED -> {
                if (decide) out.add("DECIDE_APPEAL");
            }
            default -> { }
        }
        if (disc && manage) {
            Suspension s = suspension(c, today);
            if (s == null || (s.to() != null && s.to().isBefore(today))) out.add("SUSPEND");
            else out.add("END_SUSPENSION");
        }
        if (inc && manage && Set.of(ErRules.REPORTED, ErRules.INVESTIGATING, ErRules.NOTICE_ISSUED, ErRules.HEARING_HELD).contains(c.getStatus())) {
            out.add("STEP");
            if ("POOR_PERFORMANCE".equals(c.getCategory())) out.add("REVIEW");
        }
        return out;
    }

    // ── One case, for the employee ────────────────────────────────────────────────────────────────

    /** An employee is told about a grievance they raised, and about a matter against them once they have been given notice or an outcome. */
    static boolean employeeMaySee(HrErCase c) {
        return ErRules.GRIEVANCE.equals(c.getCaseType()) || c.getNoticeIssuedAt() != null || c.getCommunicatedOn() != null;
    }

    Mine mine(TenantId tenantId, HrErCase c, LocalDate today) {
        ErPolicy policy = policyService.get(tenantId);
        boolean told = c.getCommunicatedOn() != null;
        List<Charge> charges = chargeRepo.forCase(c.getId()).stream()
                .map(x -> new Charge(x.getId(), x.getSeq(), x.getCharge(), told ? x.getFinding() : null, told ? x.getFindingNote() : null)).toList();
        Hearing hearing = c.getNoticeIssuedAt() == null ? null
                : new Hearing(c.getNoticeIssuedAt(), c.getHearingDate(), c.getHearingTime(), c.getHearingVenue(), c.getHearingHeldOn(),
                        c.getEmployeeAttended(), c.getRepresentative(), null);
        Decision decision = told ? decision(c) : null;
        Appeal appeal = !told ? null : new Appeal(DisciplineRules.appealDeadline(c.getCommunicatedOn(), policy), c.getAppealLodgedOn(), c.getAppealGrounds(),
                null, c.getAppealDecidedOn(), c.getAppealOutcome(), c.getAppealNote());
        boolean canAppeal = told && ErRules.DECIDED.equals(c.getStatus()) && c.getAppealLodgedOn() == null
                && !today.isAfter(DisciplineRules.appealDeadline(c.getCommunicatedOn(), policy));
        List<EventView> events = eventRepo.forCase(c.getId()).stream().filter(HrErEvent::isVisibleToEmployee)
                .map(e -> new EventView(e.getAction(), e.getNote(), true, null, e.getDoneAt())).toList();
        return new Mine(c.getId(), c.getCaseNumber(), c.getCaseType(), c.getCategory(), c.getTitle(),
                ErRules.GRIEVANCE.equals(c.getCaseType()) ? c.getDescription() : null, c.getStatus(), c.getReportedOn(), hearing, charges,
                decision, appeal, told && !c.isAcknowledged(), canAppeal, events);
    }

    // ── Names ─────────────────────────────────────────────────────────────────────────────────────

    private static Set<UUID> idsOf(HrErCase c) {
        Set<UUID> ids = new HashSet<>();
        for (UUID id : new UUID[]{c.getEmployeeId(), c.getInitiatorId(), c.getChairId(), c.getRespondentId(), c.getAppealDeciderId()}) if (id != null) ids.add(id);
        return ids;
    }

    private Map<UUID, String> names(Collection<UUID> ids) {
        Map<UUID, String> out = new HashMap<>();
        if (ids.isEmpty()) return out;
        for (HrEmployee e : employeeRepo.findAllById(ids)) out.put(e.getId(), e.getFullName());
        return out;
    }

    private static Party party(UUID id, Map<UUID, String> names) {
        return id == null ? null : new Party(id, names.getOrDefault(id, "Unknown"));
    }

    private String userName(TenantId tenantId, UUID userId, Map<UUID, String> cache) {
        if (userId == null) return null;
        return cache.computeIfAbsent(userId, id -> employeeRepo.findByUserId(tenantId, id).map(HrEmployee::getFullName).orElse("HR"));
    }
}
