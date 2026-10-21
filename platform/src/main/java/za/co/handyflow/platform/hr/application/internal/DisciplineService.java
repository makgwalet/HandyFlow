package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.ErDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;

import static za.co.handyflow.platform.hr.application.internal.ErCaseSupport.*;

/**
 * Deciding a disciplinary case: an informal sanction after the employee has been heard, or findings on each charge after a hearing, and
 * the decision on an appeal. Whether and how to discipline stays with the person deciding; the rules only check that the process was fair
 * and that the findings and sanction fit together.
 */
@Service
@RequiredArgsConstructor
public class DisciplineService {

    private final HrErCaseRepository caseRepo;
    private final HrErChargeRepository chargeRepo;
    private final ErCaseSupport support;
    private final ErCaseViews views;
    private final ErPolicyService policyService;

    /**
     * A counselling, verbal warning or written warning given after the employee has had the chance to respond, without a formal hearing.
     * A final written warning or a dismissal always goes through a notice and a hearing.
     */
    @Transactional
    public Detail informal(TenantId tenantId, UUID id, InformalRequest req, UUID userId, Set<String> held) {
        requireDecide(held);
        HrErCase c = support.findToAct(tenantId, id, held, userId);
        requireType(c, ErRules.DISCIPLINARY);
        support.move(c, ErRules.DECIDED);
        fail(DisciplineRules.informalProblem(req.sanction(), req.employeeResponse(), req.reasons()));
        Integer months = warningMonths(req.sanction(), req.warningMonths(), policyService.get(tenantId));
        LocalDate on = decisionDate(req.decidedOn(), c.getReportedOn());
        c.decide(req.sanction(), on, req.reasons().trim(), userId, months, req.employeeResponse().trim());
        caseRepo.save(c);
        support.event(c, "DECIDED_INFORMALLY", label(req.sanction()) + (months == null ? "" : " valid for " + months + " months"), false, userId);
        return views.detail(tenantId, c, held, userId, support.today());
    }

    /** Findings on every charge, and the sanction, after the hearing. */
    @Transactional
    public Detail decide(TenantId tenantId, UUID id, DecisionRequest req, UUID userId, Set<String> held) {
        requireDecide(held);
        HrErCase c = support.findToAct(tenantId, id, held, userId);
        requireType(c, ErRules.DISCIPLINARY);
        support.move(c, ErRules.DECIDED);
        if (c.getChairId() == null) throw new IllegalStateException("The hearing has no chair on record.");
        List<HrErCharge> charges = chargeRepo.forCase(c.getId());
        Map<UUID, ChargeFinding> byCharge = new HashMap<>();
        if (req.findings() != null) for (ChargeFinding f : req.findings()) if (f != null && f.chargeId() != null) byCharge.put(f.chargeId(), f);
        List<String> findings = new ArrayList<>();
        for (HrErCharge ch : charges) {
            ChargeFinding f = byCharge.get(ch.getId());
            findings.add(f == null ? null : f.finding());
        }
        boolean guilty = findings.contains("GUILTY");
        ErPolicy policy = policyService.get(tenantId);
        String sanction = guilty ? req.sanction() : DisciplineRules.NOT_GUILTY;
        Integer months = guilty ? warningMonths(req.sanction(), req.warningMonths(), policy) : null;
        fail(DisciplineRules.decisionProblem(findings, sanction, months, req.reasons()));
        LocalDate on = decisionDate(req.decidedOn(), c.getHearingHeldOn());
        for (int i = 0; i < charges.size(); i++) {
            ChargeFinding f = byCharge.get(charges.get(i).getId());
            String note = f.note() == null || f.note().isBlank() ? null : f.note().trim();
            if (note != null && note.length() > 2000) throw new IllegalArgumentException("A note on a finding is too long (2000 characters at most).");
            charges.get(i).find(f.finding(), note);
            chargeRepo.save(charges.get(i));
        }
        c.decide(sanction, on, req.reasons().trim(), userId, months, null);
        caseRepo.save(c);
        support.event(c, "DECIDED", label(sanction) + (months == null ? "" : " valid for " + months + " months"), false, userId);
        return views.detail(tenantId, c, held, userId, support.today());
    }

    /** Decides an appeal: confirms the sanction, lowers it, or overturns it. An appeal can never make a sanction heavier. */
    @Transactional
    public Detail decideAppeal(TenantId tenantId, UUID id, AppealDecisionRequest req, UUID userId, Set<String> held) {
        requireDecide(held);
        HrErCase c = support.findToAct(tenantId, id, held, userId);
        requireType(c, ErRules.DISCIPLINARY);
        if (!ErRules.APPEALED.equals(c.getStatus())) throw new IllegalStateException("There is no appeal waiting for a decision.");
        support.employee(tenantId, req.deciderId());
        fail(DisciplineRules.appealDeciderProblem(req.deciderId(), c.getEmployeeId(), c.getChairId(), c.getInitiatorId()));
        String newOutcome = "OVERTURNED".equals(req.outcome()) ? DisciplineRules.NOT_GUILTY : ("VARIED".equals(req.outcome()) ? req.newOutcome() : null);
        Integer months = newOutcome == null ? null : warningMonths(newOutcome, req.warningMonths(), policyService.get(tenantId));
        fail(DisciplineRules.appealOutcomeProblem(req.outcome(), c.getOutcome(), newOutcome, months, req.note()));
        LocalDate on = decisionDate(req.decidedOn(), c.getAppealLodgedOn());
        c.decideAppeal(req.deciderId(), on, req.outcome(), req.note().trim(), newOutcome, months);
        caseRepo.save(c);
        support.event(c, "APPEAL_DECIDED", "Appeal " + req.outcome().toLowerCase() + (newOutcome == null ? "" : ": " + label(newOutcome)), true, userId);
        return views.detail(tenantId, c, held, userId, support.today());
    }

    /** The months a warning lasts: what the chair chose, or the company's default for that level. Null for anything that is not a warning. */
    private static Integer warningMonths(String sanction, Integer chosen, ErPolicy policy) {
        if (sanction == null || !DisciplineRules.WARNINGS.contains(sanction)) return chosen;
        return chosen != null ? chosen : policy.monthsFor(sanction);
    }

    private LocalDate decisionDate(LocalDate given, LocalDate notBefore) {
        LocalDate today = support.today();
        LocalDate on = given == null ? today : given;
        if (on.isAfter(today)) throw new IllegalArgumentException("The decision cannot be dated in the future.");
        if (notBefore != null && on.isBefore(notBefore)) throw new IllegalArgumentException("The decision cannot come before " + notBefore + ".");
        return on;
    }

    private static String label(String sanction) { return sanction == null ? "" : words(sanction); }
}
