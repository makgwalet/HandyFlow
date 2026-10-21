package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.ErDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static za.co.handyflow.platform.hr.application.internal.ErCaseSupport.*;

/**
 * Opening and running employee relations cases: the parts every kind of case shares. Discipline, grievances and incapacity each add their
 * own steps in {@link DisciplineService}, {@link GrievanceService} and {@link IncapacityService}.
 * <p>
 * The rules live in {@link ErRules} and its siblings. This class checks who is asking, loads the case, applies the rule and writes down
 * what happened. Nobody works a case about themselves, and everything that happens is added to a timeline that cannot be edited.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErCaseService {

    private final HrErCaseRepository caseRepo;
    private final HrErChargeRepository chargeRepo;
    private final HrErStepRepository stepRepo;
    private final ErCaseSupport support;
    private final ErCaseViews views;
    private final ErPolicyService policyService;

    // ── Opening ───────────────────────────────────────────────────────────────────────────────────

    @Transactional
    public Detail open(TenantId tenantId, OpenRequest req, UUID userId, Set<String> held) {
        requireManage(held);
        HrEmployee emp = support.employee(tenantId, req.employeeId());
        fail(ErRules.categoryProblem(req.type(), req.category()));
        fail(ErRules.textProblem("The title", req.title(), 200));
        fail(ErRules.textProblem("The description", req.description(), 4000));
        LocalDate today = support.today();
        if (req.incidentDate() != null && req.incidentDate().isAfter(today)) throw new IllegalArgumentException("The date of the matter cannot be in the future.");
        if (req.initiatorId() != null) {
            support.employee(tenantId, req.initiatorId());
            if (req.initiatorId().equals(emp.getId())) throw new IllegalArgumentException("The employee cannot bring charges against themselves.");
        }
        UUID respondent = ErRules.GRIEVANCE.equals(req.type()) ? req.respondentId() : null;
        if (respondent != null) {
            support.employee(tenantId, respondent);
            if (respondent.equals(emp.getId())) throw new IllegalArgumentException("A grievance cannot be about the person lodging it.");
        }
        boolean confidential = ErRules.confidentialByDefault(req.type(), req.category()) || Boolean.TRUE.equals(req.confidential());
        HrErCase c = HrErCase.open(tenantId.getValue(), support.nextNumber(tenantId), req.type(), req.category(), emp.getId(), req.title().trim(),
                req.description().trim(), req.incidentDate(), today, confidential, userId, userId);
        c.setParties(req.initiatorId(), null, respondent);
        caseRepo.save(c);
        addSteps(c);
        support.event(c, "OPENED", req.title().trim(), false, userId);
        log.info("ER case {} ({}) opened for employee={}", c.getCaseNumber(), c.getCaseType(), emp.getEmployeeNumber());
        return views.detail(tenantId, c, held, userId, today);
    }

    /**
     * A disciplinary case reported from outside HR: the Security referral of a substantiated complaint, or the earlier HR endpoint. The
     * caller has already checked who may do this. Nothing is decided; HR takes it from here.
     */
    @Transactional
    public HrErCase openDisciplinary(TenantId tenantId, UUID employeeId, String title, String description, LocalDate incidentDate, UUID openedBy) {
        HrEmployee emp = support.employee(tenantId, employeeId);
        String t = title == null || title.isBlank() ? "Reported matter" : title.trim();
        if (t.length() > 200) t = t.substring(0, 200);
        String d = description == null || description.isBlank() ? t : description.trim();
        if (d.length() > 4000) d = d.substring(0, 4000);
        HrErCase c = HrErCase.open(tenantId.getValue(), support.nextNumber(tenantId), ErRules.DISCIPLINARY, "OTHER", emp.getId(), t, d,
                incidentDate, support.today(), false, openedBy, null);
        caseRepo.save(c);
        support.event(c, "OPENED", "Reported: " + t, false, openedBy);
        return c;
    }

    /** The disciplinary cases of one employee, newest first. */
    @Transactional(readOnly = true)
    public List<HrErCase> disciplinaryCases(TenantId tenantId, UUID employeeId) {
        return caseRepo.forEmployee(tenantId, employeeId).stream().filter(c -> ErRules.DISCIPLINARY.equals(c.getCaseType())).toList();
    }

    /** An employee lodges their own grievance. It is HR's to assign; the employee follows it through {@link #mine}. */
    @Transactional
    public Mine lodgeGrievance(TenantId tenantId, LodgeGrievanceRequest req, UUID userId) {
        HrEmployee me = support.myEmployee(tenantId, userId);
        LocalDate today = support.today();
        LocalDate on = req.incidentDate() == null ? today : req.incidentDate();
        fail(GrievanceRules.lodgeProblem(req.category(), req.description(), on, today, me.getId(), req.respondentId()));
        if (req.respondentId() != null) support.employee(tenantId, req.respondentId());
        String category = req.category();
        String title = "Grievance: " + category.toLowerCase().replace('_', ' ');
        HrErCase c = HrErCase.open(tenantId.getValue(), support.nextNumber(tenantId), ErRules.GRIEVANCE, category, me.getId(), title,
                req.description().trim(), on, today, ErRules.confidentialByDefault(ErRules.GRIEVANCE, category), userId, null);
        c.setParties(null, null, req.respondentId());
        caseRepo.save(c);
        support.event(c, "LODGED", "Lodged by the employee", true, userId);
        return views.mine(tenantId, c, today);
    }

    private void addSteps(HrErCase c) {
        if (!ErRules.INCAPACITY.equals(c.getCaseType())) return;
        int seq = 1;
        for (IncapacityRules.Step s : IncapacityRules.stepsFor(c.getCategory())) stepRepo.save(HrErStep.of(c.getTenantId(), c.getId(), seq++, s));
    }

    // ── Reading ───────────────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Row> list(TenantId tenantId, String type, String status, Set<String> held, UUID userId) {
        if (!ErRules.canRead(held)) throw new AccessDeniedException("You do not have permission to see employee relations cases.");
        List<HrErCase> visible = caseRepo.forTenant(tenantId).stream()
                .filter(c -> type == null || type.isBlank() || type.equals(c.getCaseType()))
                .filter(c -> status == null || status.isBlank() || ("OPEN".equals(status) ? ErRules.isOpen(c.getStatus()) : status.equals(c.getStatus())))
                .filter(c -> ErRules.canSee(held, userId, c.isConfidential(), c.getOwnerUserId()))
                .toList();
        return views.rows(tenantId, visible, support.today());
    }

    @Transactional(readOnly = true)
    public Detail get(TenantId tenantId, UUID id, Set<String> held, UUID userId) {
        return views.detail(tenantId, support.find(tenantId, id, held, userId), held, userId, support.today());
    }

    /** The employee's own cases: grievances they raised, and matters against them once they have been given notice or an outcome. */
    @Transactional(readOnly = true)
    public List<Mine> mine(TenantId tenantId, UUID userId) {
        HrEmployee me = support.myEmployee(tenantId, userId);
        LocalDate today = support.today();
        return caseRepo.forEmployee(tenantId, me.getId()).stream().filter(ErCaseViews::employeeMaySee).map(c -> views.mine(tenantId, c, today)).toList();
    }

    @Transactional(readOnly = true)
    public Mine mineOne(TenantId tenantId, UUID id, UUID userId) {
        return views.mine(tenantId, ownCase(tenantId, id, userId), support.today());
    }

    /** The employee confirms they received the outcome. Receiving is not agreeing: it does not remove the right to appeal. */
    @Transactional
    public Mine acknowledge(TenantId tenantId, UUID id, UUID userId) {
        HrErCase c = ownCase(tenantId, id, userId);
        if (c.getCommunicatedOn() == null) throw new IllegalStateException("There is no outcome to acknowledge yet.");
        if (!c.isAcknowledged()) {
            c.acknowledge();
            caseRepo.save(c);
            support.event(c, "ACKNOWLEDGED", "The employee confirmed they received the outcome. This is not agreement with it.", true, userId);
        }
        return views.mine(tenantId, c, support.today());
    }

    /** The employee appeals, or takes a grievance to the next level, within the company's appeal period. */
    @Transactional
    public Mine appealOwn(TenantId tenantId, UUID id, AppealRequest req, UUID userId) {
        HrErCase c = ownCase(tenantId, id, userId);
        LocalDate today = support.today();
        applyAppeal(c, today, req == null ? null : req.grounds(), false, userId, tenantId);
        return views.mine(tenantId, c, today);
    }

    private HrErCase ownCase(TenantId tenantId, UUID id, UUID userId) {
        HrEmployee me = support.myEmployee(tenantId, userId);
        HrErCase c = caseRepo.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Case", String.valueOf(id)));
        if (!me.getId().equals(c.getEmployeeId()) || !ErCaseViews.employeeMaySee(c)) throw new ResourceNotFoundException("Case", String.valueOf(id));
        return c;
    }

    // ── Running a case ────────────────────────────────────────────────────────────────────────────

    @Transactional
    public Detail investigate(TenantId tenantId, UUID id, UUID userId, Set<String> held) {
        requireManage(held);
        HrErCase c = support.findToAct(tenantId, id, held, userId);
        support.move(c, ErRules.INVESTIGATING);
        c.moveTo(ErRules.INVESTIGATING);
        caseRepo.save(c);
        support.event(c, "INVESTIGATING", null, false, userId);
        return views.detail(tenantId, c, held, userId, support.today());
    }

    @Transactional
    public Detail assign(TenantId tenantId, UUID id, AssignRequest req, UUID userId, Set<String> held) {
        requireManage(held);
        HrErCase c = support.findToAct(tenantId, id, held, userId);
        requireOpen(c);
        c.setOwner(req.ownerUserId());
        caseRepo.save(c);
        support.event(c, "ASSIGNED", null, false, userId);
        return views.detail(tenantId, c, held, userId, support.today());
    }

    @Transactional
    public Detail setParties(TenantId tenantId, UUID id, PartiesRequest req, UUID userId, Set<String> held) {
        requireManage(held);
        HrErCase c = support.findToAct(tenantId, id, held, userId);
        requireOpen(c);
        for (UUID p : new UUID[]{req.initiatorId(), req.chairId(), req.respondentId()}) if (p != null) support.employee(tenantId, p);
        if (req.initiatorId() != null && req.initiatorId().equals(c.getEmployeeId())) throw new IllegalArgumentException("The employee cannot bring charges against themselves.");
        if (req.respondentId() != null && req.respondentId().equals(c.getEmployeeId())) throw new IllegalArgumentException("A grievance cannot be about the person lodging it.");
        if (req.chairId() != null) {
            if (ErRules.GRIEVANCE.equals(c.getCaseType())) fail(GrievanceRules.deciderProblem(req.chairId(), c.getEmployeeId(), req.respondentId()));
            else fail(DisciplineRules.chairProblem(req.chairId(), c.getEmployeeId(), req.initiatorId()));
        }
        c.setParties(req.initiatorId(), req.chairId(), req.respondentId());
        caseRepo.save(c);
        support.event(c, "PARTIES_SET", null, false, userId);
        return views.detail(tenantId, c, held, userId, support.today());
    }

    @Transactional
    public Detail setConfidential(TenantId tenantId, UUID id, FlagRequest req, UUID userId, Set<String> held) {
        requireManage(held);
        HrErCase c = support.findToAct(tenantId, id, held, userId);
        if (!req.confidential() && ErRules.confidentialByDefault(c.getCaseType(), c.getCategory()))
            throw new IllegalStateException("This kind of case is always confidential: it holds medical details or a complaint of harassment or discrimination.");
        c.setConfidential(req.confidential());
        caseRepo.save(c);
        support.event(c, req.confidential() ? "MADE_CONFIDENTIAL" : "MADE_NOT_CONFIDENTIAL", null, false, userId);
        return views.detail(tenantId, c, held, userId, support.today());
    }

    // ── Notice and hearing (discipline and the incapacity meeting) ────────────────────────────────

    /**
     * Gives the employee notice of a hearing or meeting. For discipline the charges are written in the notice. Giving notice again, for
     * a postponement or a corrected charge, replaces the first one as long as the hearing has not been held.
     */
    @Transactional
    public Detail issueNotice(TenantId tenantId, UUID id, NoticeRequest req, UUID userId, Set<String> held) {
        requireManage(held);
        HrErCase c = support.findToAct(tenantId, id, held, userId);
        boolean disc = ErRules.DISCIPLINARY.equals(c.getCaseType());
        if (!disc && !ErRules.INCAPACITY.equals(c.getCaseType())) throw new IllegalStateException("A grievance does not have a hearing.");
        support.move(c, ErRules.NOTICE_ISSUED);
        UUID chair = req.chairId() != null ? req.chairId() : c.getChairId();
        UUID initiator = req.initiatorId() != null ? req.initiatorId() : c.getInitiatorId();
        if (disc) fail(DisciplineRules.chairProblem(chair, c.getEmployeeId(), initiator));
        else {
            if (chair == null) throw new IllegalArgumentException("Choose who will hold the meeting.");
            if (chair.equals(c.getEmployeeId())) throw new IllegalArgumentException("The employee cannot hold their own meeting.");
        }
        support.employee(tenantId, chair);
        if (initiator != null) support.employee(tenantId, initiator);
        List<String> charges = disc ? trimmed(req.charges()) : List.of();
        ErPolicy policy = policyService.get(tenantId);
        Instant now = Instant.now();
        fail(DisciplineRules.noticeProblem(now, req.hearingDate(), req.hearingTime(), req.venue(), charges, disc, policy));
        List<HrErCharge> existing = chargeRepo.forCase(c.getId());
        if (disc) replaceCharges(c, existing, charges);
        c.issueNotice(now, req.hearingDate(), req.hearingTime(), req.venue().trim(), chair, initiator);
        caseRepo.save(c);
        support.event(c, "NOTICE_ISSUED", (disc ? "Notice of a disciplinary hearing" : "Invitation to a meeting") + " on " + req.hearingDate() + " at "
                + req.hearingTime() + ", " + req.venue().trim(), true, userId);
        return views.detail(tenantId, c, held, userId, support.today());
    }

    private void replaceCharges(HrErCase c, List<HrErCharge> existing, List<String> charges) {
        for (HrErCharge old : existing) if (old.getFinding() != null)
            throw new IllegalStateException("A finding has already been given on a charge, so the notice cannot be changed.");
        for (int i = 0; i < charges.size(); i++) {
            if (i < existing.size()) chargeRepo.save(setText(existing.get(i), charges.get(i)));
            else chargeRepo.save(HrErCharge.of(c.getTenantId(), c.getId(), i + 1, charges.get(i)));
        }
        if (existing.size() > charges.size()) chargeRepo.deleteAll(existing.subList(charges.size(), existing.size()));
    }

    private static HrErCharge setText(HrErCharge c, String text) { c.setText(text); return c; }

    private static List<String> trimmed(List<String> in) {
        if (in == null) return List.of();
        List<String> out = new ArrayList<>();
        for (String s : in) out.add(s == null ? null : s.trim());
        return out;
    }

    /** Writes down that the hearing or meeting took place, who came and what was said. */
    @Transactional
    public Detail recordHearing(TenantId tenantId, UUID id, HearingRequest req, UUID userId, Set<String> held) {
        requireManage(held);
        HrErCase c = support.findToAct(tenantId, id, held, userId);
        support.move(c, ErRules.HEARING_HELD);
        LocalDate noticeDate = c.getNoticeIssuedAt() == null ? null : c.getNoticeIssuedAt().atZone(DisciplineRules.ZONE).toLocalDate();
        fail(DisciplineRules.hearingProblem(req.heldOn(), noticeDate, support.today(), req.employeeAttended(), req.notes()));
        if (req.representative() != null && req.representative().length() > 200) throw new IllegalArgumentException("The representative's name is too long.");
        String rep = req.representative() == null || req.representative().isBlank() ? null : req.representative().trim();
        c.holdHearing(req.heldOn(), req.employeeAttended(), rep, req.notes().trim());
        caseRepo.save(c);
        support.event(c, "HEARING_HELD", (Boolean.TRUE.equals(req.employeeAttended()) ? "The employee attended" : "The employee did not attend")
                + (rep == null ? "" : ", represented by " + rep), true, userId);
        return views.detail(tenantId, c, held, userId, support.today());
    }

    // ── After a decision ──────────────────────────────────────────────────────────────────────────

    /** Records that the employee was told the outcome, and for a dismissal that they were told of the right to go to the CCMA. */
    @Transactional
    public Detail communicate(TenantId tenantId, UUID id, CommunicateRequest req, UUID userId, Set<String> held) {
        requireManage(held);
        HrErCase c = support.findToAct(tenantId, id, held, userId);
        if (!ErRules.DECIDED.equals(c.getStatus())) throw new IllegalStateException("There is no decision to communicate yet.");
        if (c.getCommunicatedOn() != null) throw new IllegalStateException("The employee was already told on " + c.getCommunicatedOn() + ".");
        boolean rights = Boolean.TRUE.equals(req.rightsExplained());
        fail(DisciplineRules.communicationProblem(c.getOutcome(), req.on(), c.getDecidedOn(), support.today(), rights));
        c.communicate(req.on(), rights);
        caseRepo.save(c);
        support.event(c, "COMMUNICATED", "The employee was told the outcome", true, userId);
        return views.detail(tenantId, c, held, userId, support.today());
    }

    /** HR lodges an appeal on the employee's behalf. A late one is only taken by someone who may decide, who accepts it on purpose. */
    @Transactional
    public Detail lodgeAppeal(TenantId tenantId, UUID id, AppealRequest req, UUID userId, Set<String> held) {
        requireManage(held);
        boolean late = Boolean.TRUE.equals(req.acceptLate());
        if (late) requireDecide(held);
        HrErCase c = support.findToAct(tenantId, id, held, userId);
        LocalDate lodgedOn = req.lodgedOn() == null ? support.today() : req.lodgedOn();
        applyAppeal(c, lodgedOn, req.grounds(), late, userId, tenantId);
        return views.detail(tenantId, c, held, userId, support.today());
    }

    private void applyAppeal(HrErCase c, LocalDate lodgedOn, String grounds, boolean acceptLate, UUID userId, TenantId tenantId) {
        if (!ErRules.DECIDED.equals(c.getStatus())) throw new IllegalStateException("Only a decided case can be appealed.");
        if (c.getAppealLodgedOn() != null) throw new IllegalStateException("An appeal was already lodged on " + c.getAppealLodgedOn() + ".");
        fail(DisciplineRules.appealProblem(c.getCommunicatedOn(), lodgedOn, support.today(), grounds, acceptLate, policyService.get(tenantId)));
        c.lodgeAppeal(lodgedOn, grounds.trim());
        caseRepo.save(c);
        support.event(c, "APPEAL_LODGED", acceptLate ? "Accepted out of time" : null, true, userId);
    }

    /**
     * Closes a case. A decided case closes once the appeal period has passed; before then the employee could still appeal. Closing a
     * disciplinary case that has no case to answer needs someone who may decide.
     */
    @Transactional
    public Detail close(TenantId tenantId, UUID id, ReasonRequest req, UUID userId, Set<String> held) {
        requireManage(held);
        HrErCase c = support.findToAct(tenantId, id, held, userId);
        fail(ErRules.textProblem("The reason", req.reason(), 500));
        support.move(c, ErRules.CLOSED);
        LocalDate today = support.today();
        if (ErRules.DECIDED.equals(c.getStatus())) {
            if (c.getCommunicatedOn() == null) throw new IllegalStateException("Tell the employee the outcome before closing the case.");
            LocalDate end = DisciplineRules.appealDeadline(c.getCommunicatedOn(), policyService.get(tenantId));
            if (!today.isAfter(end)) throw new IllegalStateException("The employee can appeal until " + end + ". The case can be closed after that.");
        } else {
            requireDecide(held);
        }
        c.close(today, req.reason().trim());
        caseRepo.save(c);
        support.event(c, "CLOSED", req.reason().trim(), true, userId);
        return views.detail(tenantId, c, held, userId, today);
    }

    @Transactional
    public Detail withdraw(TenantId tenantId, UUID id, ReasonRequest req, UUID userId, Set<String> held) {
        requireManage(held);
        HrErCase c = support.findToAct(tenantId, id, held, userId);
        fail(ErRules.textProblem("The reason", req.reason(), 500));
        support.move(c, ErRules.WITHDRAWN);
        c.withdraw(support.today(), req.reason().trim());
        caseRepo.save(c);
        support.event(c, "WITHDRAWN", req.reason().trim(), c.getNoticeIssuedAt() != null, userId);
        return views.detail(tenantId, c, held, userId, support.today());
    }

    // ── Precautionary suspension (always on full pay) ─────────────────────────────────────────────

    /**
     * Records a precautionary suspension. It is always on full pay, so it does not change the employee's status and payroll carries on as normal.
     * Suspension without pay as a sanction is not offered.
     */
    @Transactional
    public Detail suspend(TenantId tenantId, UUID id, SuspendRequest req, UUID userId, Set<String> held) {
        requireManage(held);
        HrErCase c = support.findToAct(tenantId, id, held, userId);
        requireType(c, ErRules.DISCIPLINARY);
        requireOpen(c);
        fail(DisciplineRules.suspensionProblem(req.from(), req.to(), req.reason(), support.today()));
        c.suspend(req.from(), req.to(), req.reason().trim());
        caseRepo.save(c);
        support.event(c, "SUSPENDED", "Precautionary suspension on full pay from " + req.from() + ". " + req.reason().trim(), true, userId);
        return views.detail(tenantId, c, held, userId, support.today());
    }

    @Transactional
    public Detail endSuspension(TenantId tenantId, UUID id, DateRequest req, UUID userId, Set<String> held) {
        requireManage(held);
        HrErCase c = support.findToAct(tenantId, id, held, userId);
        if (c.getSuspendedFrom() == null) throw new IllegalStateException("The employee is not suspended.");
        LocalDate on = req == null || req.on() == null ? support.today() : req.on();
        if (on.isBefore(c.getSuspendedFrom())) throw new IllegalArgumentException("The suspension cannot end before it started.");
        c.endSuspension(on);
        caseRepo.save(c);
        support.event(c, "SUSPENSION_ENDED", "Suspension ended on " + on, true, userId);
        return views.detail(tenantId, c, held, userId, support.today());
    }

    // ── Warnings for an employee ──────────────────────────────────────────────────────────────────

    /** Every warning an employee has, in force or not, so a chair can see the history before deciding. */
    @Transactional(readOnly = true)
    public List<WarningView> warnings(TenantId tenantId, UUID employeeId, Set<String> held, UUID userId) {
        if (!ErRules.canRead(held)) throw new AccessDeniedException("You do not have permission to see employee relations cases.");
        support.employee(tenantId, employeeId);
        return caseRepo.forEmployee(tenantId, employeeId).stream()
                .filter(c -> ErRules.DISCIPLINARY.equals(c.getCaseType()) && c.getOutcome() != null && c.getDecidedOn() != null
                        && (DisciplineRules.WARNINGS.contains(c.getOutcome()) || DisciplineRules.SUSPENSION.equals(c.getOutcome())))
                .filter(c -> ErRules.canSee(held, userId, c.isConfidential(), c.getOwnerUserId()))
                .map(c -> new WarningView(c.getId(), c.getCaseNumber(), c.getOutcome(), c.getDecidedOn(), c.getWarningValidUntil()))
                .toList();
    }

    private static void requireOpen(HrErCase c) {
        if (!ErRules.isOpen(c.getStatus())) throw new IllegalStateException("This case is " + words(c.getStatus()) + " and can no longer be changed.");
    }
}
