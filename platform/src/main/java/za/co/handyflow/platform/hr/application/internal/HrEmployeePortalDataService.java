package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.HrEmployeePortalAccessGrant;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeePortalAccessGrantRepository;
import za.co.handyflow.platform.hr.domain.repository.HrLeaveRequestRepository;
import za.co.handyflow.platform.hr.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * FIX: backlog 3.4. Direct structural mirror of
 * payrollbureau.PayrollBureauPortalDataService's pattern: every method
 * starts with requireAccess(), tenant resolved from the GRANT (not
 * TenantContext — a portal user isn't tied to one tenant the way staff
 * are, see PortalJwtFilter's own Javadoc), 1:1 with that class's own
 * doc comment.
 * <p>
 * DELIBERATELY DOES NOT REIMPLEMENT ANY BUSINESS LOGIC. Profile, leave
 * balances, leave-request submission, and payslip listing all delegate
 * to the exact same HrService/PayrollService methods the staff-facing
 * HrController already calls. This matters most for
 * submitMyLeaveRequest(): it calls HrService.submitLeaveRequest()
 * directly, which means a portal-submitted leave request gets the exact
 * same balance validation AND the exact same notifyApprover() routing
 * (manager if resolvable, else tenant admins) a staff-submitted one
 * already gets — nothing new to build or maintain for that half of the
 * workflow. getMyLeaveRequests() is the one exception, mapping
 * HrLeaveRequest → LeaveRequestResponse locally rather than reusing
 * HrService's own toLeaveResponse() — that method is private, and adding
 * a new public method to HrService just to expose an identical mapping
 * felt like more surface area than the one straightforward mapping below
 * warranted.
 * <p>
 * NOT YET INCLUDED — flagged, not silently dropped: payslip PDF
 * download. HrController's own downloadPayslip() endpoint builds the PDF
 * via a private fetchTenantDetails(tenantId) helper whose implementation
 * wasn't visible this session (only its call site was). Reusing it
 * safely means either exposing it as a public method on PayrollService or
 * independently confirming what it does — guessing at unseen logic for a
 * document with a tenant's name/VAT number on it is exactly the kind of
 * thing worth getting right rather than assumed. The JSON payslip data
 * below (every figure: gross, PAYE, UIF, deductions, net) already covers
 * the real self-service need; PDF download is a fast-follow once that
 * helper's real shape is confirmed.
 */
@Service
@RequiredArgsConstructor
public class HrEmployeePortalDataService {

    private final HrEmployeePortalAccessGrantRepository grantRepo;
    private final HrService hrService;
    private final PayrollService payrollService;
    private final HrLeaveRequestRepository leaveRepo;

    @Transactional(readOnly = true)
    public EmployeeResponse getMyProfile(UUID portalUserId, UUID employeeId) {
        HrEmployeePortalAccessGrant grant = requireAccess(portalUserId, employeeId);
        return hrService.getEmployee(TenantId.of(grant.getTenantId()), employeeId);
    }

    @Transactional(readOnly = true)
    public List<PayslipResponse> getMyPayslips(UUID portalUserId, UUID employeeId) {
        HrEmployeePortalAccessGrant grant = requireAccess(portalUserId, employeeId);
        return payrollService.getEmployeePayslips(TenantId.of(grant.getTenantId()), employeeId);
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceResponse> getMyLeaveBalances(UUID portalUserId, UUID employeeId, int year) {
        HrEmployeePortalAccessGrant grant = requireAccess(portalUserId, employeeId);
        int leaveYear = year == 0 ? LocalDate.now().getYear() : year;
        return hrService.getLeaveBalances(TenantId.of(grant.getTenantId()), employeeId, leaveYear);
    }

    @Transactional(readOnly = true)
    public Page<LeaveRequestResponse> getMyLeaveRequests(UUID portalUserId, UUID employeeId, Pageable pageable) {
        HrEmployeePortalAccessGrant grant = requireAccess(portalUserId, employeeId);
        // NEW: HrLeaveRequestRepository previously had no employee-scoped
        // query — HrService.getLeaveRequests() is all-tenant (the staff
        // admin view). findByEmployee() is a genuinely new method, added
        // for this self-service view specifically — see the patch
        // instructions for HrLeaveRequestRepository.java.
        EmployeeResponse emp = hrService.getEmployee(TenantId.of(grant.getTenantId()), employeeId);
        return leaveRepo.findByEmployee(employeeId, pageable)
                .map(r -> new LeaveRequestResponse(
                        r.getId(), r.getEmployeeId(), emp.fullName(),
                        r.getLeaveType(), r.getStartDate(), r.getEndDate(),
                        r.getDaysRequested(), r.getReason(), r.getStatus(),
                        r.getRejectionReason(), r.getCreatedAt()));
    }

    @Transactional
    public LeaveRequestResponse submitMyLeaveRequest(UUID portalUserId, UUID employeeId, SubmitLeaveRequest req) {
        HrEmployeePortalAccessGrant grant = requireAccess(portalUserId, employeeId);
        return hrService.submitLeaveRequest(TenantId.of(grant.getTenantId()), employeeId, req);
    }

    private HrEmployeePortalAccessGrant requireAccess(UUID portalUserId, UUID employeeId) {
        return grantRepo.findActiveGrant(portalUserId, employeeId)
                .orElseThrow(() -> new HandyFlowException(
                        "You don't have access to this employee record", HttpStatus.FORBIDDEN, "NO_ACCESS"));
    }
}