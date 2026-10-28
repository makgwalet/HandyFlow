package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.HrEmployeePortalAccessGrant;
import za.co.handyflow.platform.hr.domain.model.NoticeRules;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeePortalAccessGrantRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What has to follow once an employee's employment has ended, however it was ended: leave asked for after the last day is cancelled, the
 * self-service portal is closed, and the notice position is worked out. Called inside the transaction that ends employment, so either
 * all of it happens or none. It never changes pay: notice pay is only worked out and reported.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TerminationFollowUp {

    private final LeaveService leaveService;
    private final HrEmployeePortalAccessGrantRepository grantRepo;
    private final EmployeeAuditService audit;

    /** What was done, in sentences the person ending employment can read. Empty when there was nothing to do. */
    public List<String> run(TenantId tenantId, HrEmployee emp, UUID actor) {
        List<String> done = new ArrayList<>();
        LeaveService.EndOfEmployment leave = leaveService.cancelAfterEmploymentEnds(tenantId, emp.getId(), emp.getEndDate(), actor);
        if (leave.cancelled() > 0) {
            done.add(leave.cancelled() + (leave.cancelled() == 1 ? " leave request after the last day was cancelled." : " leave requests after the last day were cancelled."));
            audit.record(tenantId, emp.getId(), "LEAVE_CANCELLED", leave.cancelled() + " request(s) after " + emp.getEndDate(), actor);
        }
        if (leave.straddling() > 0) {
            done.add(leave.straddling() + (leave.straddling() == 1 ? " leave request runs past the last day and was left as it is. Check it."
                    : " leave requests run past the last day and were left as they are. Check them."));
        }
        int closed = 0;
        for (HrEmployeePortalAccessGrant g : grantRepo.findByTenantAndEmployee(tenantId.getValue(), emp.getId())) {
            if ("ACTIVE".equals(g.getStatus()) || "PENDING".equals(g.getStatus())) { g.revoke(actor); grantRepo.save(g); closed++; }
        }
        if (closed > 0) {
            done.add("Self-service portal access was closed.");
            audit.record(tenantId, emp.getId(), "PORTAL_CLOSED", closed + " grant(s)", actor);
        }
        String notice = NoticeRules.summary(emp.getEndType(), emp.getGrossSalary(), emp.getStartDate(), emp.getNoticeGivenOn(), emp.getEndDate());
        if (notice != null) done.add(notice);
        else if (emp.getEndType() == null) done.add("How employment ended was not recorded, so notice pay was not worked out.");
        log.info("Termination follow-up for employee={}: {}", emp.getEmployeeNumber(), done);
        return done;
    }
}
