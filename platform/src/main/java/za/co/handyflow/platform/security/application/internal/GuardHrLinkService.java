// security/application/internal/GuardHrLinkService.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.hr.dto.EmployeeResponse;
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.dto.GuardHrLinkDtos.EmployeeOption;
import za.co.handyflow.platform.security.dto.GuardHrLinkDtos.HrLink;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Links a guard to the HR employee record of the same person. Optional and one-to-one. HR owns the employee
 * (name, job title, status); Security stores the id and reads the rest through the HR facade, so nothing is copied.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuardHrLinkService {

    private static final HrLink NOT_LINKED = new HrLink(false, false, null, null, null, null, null, null);

    private final GuardRepository guardRepository;
    private final HrFacade hrFacade;

    @Transactional(readOnly = true)
    public HrLink get(TenantId tenantId, UUID guardId) {
        Guard g = guard(tenantId, guardId);
        if (g.getEmployeeId() == null) return NOT_LINKED;
        return hrFacade.findEmployeeById(tenantId, g.getEmployeeId())
                .map(GuardHrLinkService::toLink)
                .orElse(new HrLink(true, true, g.getEmployeeId(), null, null, null, null, null));
    }

    /** Employees to choose from, found by name, number or ID. A blank search returns nothing. */
    @Transactional(readOnly = true)
    public List<EmployeeOption> search(TenantId tenantId, String q) {
        if (q == null || q.trim().length() < 2) return List.of();
        return hrFacade.searchEmployees(tenantId, q.trim(), 20).stream()
                .map(e -> new EmployeeOption(e.id(), e.employeeNumber(), e.fullName(), e.jobTitle(), e.status())).toList();
    }

    @Transactional
    public HrLink link(TenantId tenantId, UUID guardId, UUID employeeId) {
        Guard g = guard(tenantId, guardId);
        EmployeeResponse e = hrFacade.findEmployeeById(tenantId, employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId.toString()));
        guardRepository.findByEmployee(tenantId, employeeId).ifPresent(other -> {
            if (!other.getId().equals(guardId))
                throw new HandyFlowException("That employee is already linked to " + other.getFullName(), HttpStatus.CONFLICT, "EMPLOYEE_ALREADY_LINKED");
        });
        g.linkEmployee(employeeId);
        guardRepository.save(g);
        log.info("[Security] Guard {} linked to HR employee {}", guardId, employeeId);
        return toLink(e);
    }

    @Transactional
    public HrLink unlink(TenantId tenantId, UUID guardId) {
        Guard g = guard(tenantId, guardId);
        g.linkEmployee(null);
        guardRepository.save(g);
        return NOT_LINKED;
    }

    private Guard guard(TenantId tenantId, UUID guardId) {
        return guardRepository.findActiveById(tenantId, guardId).orElseThrow(() -> new ResourceNotFoundException("Guard", guardId.toString()));
    }

    private static HrLink toLink(EmployeeResponse e) {
        return new HrLink(true, false, e.id(), e.employeeNumber(), e.fullName(), e.jobTitle(), e.department(), e.status());
    }
}
