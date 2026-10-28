package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.EmployeeRules;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.OrgRules;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.dto.EmployeeViewResponse;
import za.co.handyflow.platform.hr.dto.ForeignIdentity;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

/**
 * Employees as one person may see them. Works on the entity directly, so {@code HrService.getEmployee} and the
 * {@code EmployeeResponse} that other modules use stay as they are.
 */
@Service
@RequiredArgsConstructor
public class EmployeeViewService {

    private final HrEmployeeRepository employeeRepo;
    private final EmployeeAuditService audit;

    @Transactional(readOnly = true)
    public Page<EmployeeViewResponse> list(TenantId tenantId, String status, String search, Pageable pageable,
                                           Set<String> held, UUID actorUserId) {
        if (HrAccess.teamOnly(held)) {
            Set<UUID> team = teamOf(tenantId, actorUserId);
            String q = search == null ? "" : search.toLowerCase();
            List<EmployeeViewResponse> rows = employeeRepo.findNotTerminated(tenantId).stream()
                    .filter(e -> team.contains(e.getId()))
                    .filter(e -> status == null || status.equals(e.getStatus()))
                    .filter(e -> q.isBlank() || e.getFullName().toLowerCase().contains(q) || e.getEmployeeNumber().contains(search))
                    .sorted(Comparator.comparing(HrEmployee::getLastName).thenComparing(HrEmployee::getFirstName))
                    .map(e -> view(e, Map.of(), Set.of()))
                    .toList();
            int from = (int) Math.min(pageable.getOffset(), rows.size());
            int to = Math.min(from + pageable.getPageSize(), rows.size());
            return new PageImpl<>(rows.subList(from, to), pageable, rows.size());
        }
        Map<UUID, String> names = names(tenantId);
        return employeeRepo.findAllActive(tenantId, status, search, pageable).map(e -> view(e, names, held));
    }

    @Transactional
    public EmployeeViewResponse get(TenantId tenantId, UUID id, Set<String> held, UUID actorUserId) {
        HrEmployee e = employeeRepo.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Employee", id.toString()));
        if (HrAccess.teamOnly(held) && !teamOf(tenantId, actorUserId).contains(id)) {
            throw new AccessDeniedException("You can only see the people in your own team.");
        }
        boolean team = HrAccess.teamOnly(held);
        Set<String> effective = team ? Set.of() : held;
        if (!team && HrAccess.sensitive(held)) audit.recordViewOnce(tenantId, id, "VIEWED_PERSONAL_DATA", actorUserId);
        if (!team && HrAccess.compensation(held)) audit.recordViewOnce(tenantId, id, "VIEWED_PAY", actorUserId);
        return view(e, names(tenantId), effective);
    }

    /** The people who report, directly or through others, to the employee linked to this login. */
    public Set<UUID> teamOf(TenantId tenantId, UUID actorUserId) {
        if (actorUserId == null) return Set.of();
        Optional<HrEmployee> me = employeeRepo.findByUserId(tenantId, actorUserId);
        if (me.isEmpty()) return Set.of();
        Map<UUID, UUID> managerOf = new HashMap<>();
        for (HrEmployee e : employeeRepo.findNotTerminated(tenantId)) if (e.getManagerId() != null) managerOf.put(e.getId(), e.getManagerId());
        return OrgRules.team(me.get().getId(), managerOf);
    }

    private Map<UUID, String> names(TenantId tenantId) {
        Map<UUID, String> m = new HashMap<>();
        for (HrEmployee e : employeeRepo.findNotTerminated(tenantId)) m.put(e.getId(), e.getFullName());
        return m;
    }

    static EmployeeViewResponse view(HrEmployee e, Map<UUID, String> names, Set<String> held) {
        boolean sensitive = HrAccess.sensitive(held);
        boolean pay = HrAccess.compensation(held);
        return new EmployeeViewResponse(
                e.getId(), e.getEmployeeNumber(), e.getFirstName(), e.getLastName(), e.getFullName(),
                e.getEmail(), e.getPhone(), e.getEmploymentType(), e.getJobTitle(), e.getDepartment(),
                e.getStartDate(), e.getEndDate(), e.getStatus(), e.getManagerId(),
                e.getManagerId() == null ? null : names.get(e.getManagerId()), e.getUserId(),
                sensitive, pay,
                sensitive ? e.getIdNumber() : null, sensitive ? e.getTaxNumber() : null, sensitive ? e.getDateOfBirth() : null,
                sensitive ? e.getGender() : null, sensitive ? e.getRace() : null,
                sensitive ? e.getEmergencyContactName() : null, sensitive ? e.getEmergencyContactPhone() : null,
                sensitive ? e.getTerminationReason() : null,
                pay ? e.getGrossSalary() : null, pay ? e.getPayFrequency() : null,
                pay ? e.getMedicalAidContribution() : null, pay ? e.getPensionContribution() : null,
                pay ? e.getTravelAllowance() : null, pay ? e.getBankName() : null,
                pay ? EmployeeRules.maskAccount(e.getBankAccountNumber()) : null, pay ? e.getBankBranchCode() : null,
                sensitive ? new ForeignIdentity(e.getPassportNumber(), e.getPassportCountry(), e.getPassportExpiry(),
                        e.getWorkPermitType(), e.getWorkPermitNumber(), e.getWorkPermitExpiry()) : null,
                pay ? e.getBankAccountType() : null);
    }
}
