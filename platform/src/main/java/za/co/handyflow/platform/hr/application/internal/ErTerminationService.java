package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.dto.ErDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static za.co.handyflow.platform.hr.application.internal.ErCaseSupport.*;

/**
 * A dismissal on its own does not end anyone's employment: it is a decision. This ends it, on purpose, by someone who may decide, once the
 * employee has been told and no appeal is waiting. The next pay run then pays them up to the last day, with the annual leave owing; leave after that day is cancelled and portal access is closed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErTerminationService {

    private final ErCaseSupport support;
    private final HrEmployeeRepository employeeRepo;
    private final EmployeeAuditService audit;
    private final ErCaseViews views;
    private final TerminationFollowUp followUp;

    @Transactional
    public Detail endEmployment(TenantId tenantId, UUID id, EndEmploymentRequest req, UUID userId, Set<String> held) {
        requireDecide(held);
        HrErCase c = support.findToAct(tenantId, id, held, userId);
        HrEmployee e = support.employee(tenantId, c.getEmployeeId());
        LocalDate lastDay = req == null ? null : req.lastDay();
        fail(DisciplineRules.endEmploymentProblem(c.getStatus(), c.getOutcome(), c.getCommunicatedOn(), e.getStatus(), lastDay));
        String reason = "Dismissal after case " + c.getCaseNumber();
        fail(EmployeeRules.terminationProblem(e.getStatus(), e.getStartDate(), lastDay, reason, support.today()));
        e.terminate(lastDay, reason, userId);
        employeeRepo.save(e);
        audit.record(tenantId, e.getId(), "TERMINATED", "last day " + lastDay + ", dismissal, case " + c.getCaseNumber(), userId);
        followUp.run(tenantId, e, userId);
        support.event(c, "EMPLOYMENT_ENDED", "Employment ended on " + lastDay, true, userId);
        log.info("Ended employment of employee={} after case={}", e.getEmployeeNumber(), c.getCaseNumber());
        return views.detail(tenantId, c, held, userId, support.today());
    }
}
