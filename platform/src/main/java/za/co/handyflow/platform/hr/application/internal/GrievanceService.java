package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.HrErCaseRepository;
import za.co.handyflow.platform.hr.dto.ErDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static za.co.handyflow.platform.hr.application.internal.ErCaseSupport.*;

/** Deciding a grievance, and the decision at the next level when the employee is not satisfied. */
@Service
@RequiredArgsConstructor
public class GrievanceService {

    private final HrErCaseRepository caseRepo;
    private final ErCaseSupport support;
    private final ErCaseViews views;

    @Transactional
    public Detail decide(TenantId tenantId, UUID id, GrievanceDecisionRequest req, UUID userId, Set<String> held) {
        requireDecide(held);
        HrErCase c = support.findToAct(tenantId, id, held, userId);
        requireType(c, ErRules.GRIEVANCE);
        support.move(c, ErRules.DECIDED);
        support.employee(tenantId, req.deciderId());
        fail(GrievanceRules.deciderProblem(req.deciderId(), c.getEmployeeId(), c.getRespondentId()));
        fail(GrievanceRules.outcomeProblem(req.outcome(), req.note()));
        LocalDate on = date(req.decidedOn(), c.getReportedOn());
        c.setParties(c.getInitiatorId(), req.deciderId(), c.getRespondentId());
        c.decide(req.outcome(), on, req.note().trim(), userId, null, null);
        caseRepo.save(c);
        support.event(c, "DECIDED", words(req.outcome()), false, userId);
        return views.detail(tenantId, c, held, userId, support.today());
    }

    /** The next level decides afresh. The grievance closes with that outcome, whether it confirms the first or changes it. */
    @Transactional
    public Detail decideEscalation(TenantId tenantId, UUID id, GrievanceDecisionRequest req, UUID userId, Set<String> held) {
        requireDecide(held);
        HrErCase c = support.findToAct(tenantId, id, held, userId);
        requireType(c, ErRules.GRIEVANCE);
        if (!ErRules.APPEALED.equals(c.getStatus())) throw new IllegalStateException("No grievance is waiting at the next level.");
        support.employee(tenantId, req.deciderId());
        fail(GrievanceRules.escalationDeciderProblem(req.deciderId(), c.getEmployeeId(), c.getRespondentId(), c.getChairId()));
        fail(GrievanceRules.outcomeProblem(req.outcome(), req.note()));
        LocalDate on = date(req.decidedOn(), c.getAppealLodgedOn());
        boolean same = req.outcome().equals(c.getOutcome());
        c.decideAppeal(req.deciderId(), on, same ? "CONFIRMED" : "VARIED", req.note().trim(), same ? null : req.outcome(), null);
        caseRepo.save(c);
        support.event(c, "ESCALATION_DECIDED", words(req.outcome()), true, userId);
        return views.detail(tenantId, c, held, userId, support.today());
    }

    private LocalDate date(LocalDate given, LocalDate notBefore) {
        LocalDate today = support.today();
        LocalDate on = given == null ? today : given;
        if (on.isAfter(today)) throw new IllegalArgumentException("The decision cannot be dated in the future.");
        if (notBefore != null && on.isBefore(notBefore)) throw new IllegalArgumentException("The decision cannot come before " + notBefore + ".");
        return on;
    }
}
