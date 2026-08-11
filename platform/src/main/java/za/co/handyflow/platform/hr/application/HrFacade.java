package za.co.handyflow.platform.hr.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.hr.application.internal.HrService;
import za.co.handyflow.platform.hr.dto.CreateEmployeeRequest;
import za.co.handyflow.platform.hr.dto.EmployeeResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * Public entry point for other modules that need to create, look up, or
 * update HR employee records — matches the same pattern AccountingFacade
 * already established (thin pass-through to the real internal service,
 * no logic reimplemented here).
 * <p>
 * WHY THIS EXISTS: before this class, there was no public way to reach HR
 * from outside the module. Recruiter's RecruiterService worked around that
 * by importing HrService directly from hr.application.internal — reaching
 * past HR's own ".internal" convention, and contradicting recruiter's own
 * package-info.java, which never declared "hr" as an allowed dependency.
 * This facade is the fix — see HandyFlow BOS Discovery doc, Section 15.2.
 * <p>
 * DESIGN NOTE — shared identifier, not shared entity: HR keeps full
 * ownership of the Employee record and its own business rules (leave
 * balance seeding, employee numbering, BCEA minimums). Callers never see
 * or manipulate HrEmployee directly — only this DTO-shaped contract. This
 * is deliberate: it's the "shared identifier, not shared entity" pattern
 * from Section 22.3/22.10 of the discovery doc, and this facade is the
 * first real implementation of it. Every future People-subdomain
 * integration (Security->HR per 15.4, any future payroll-bureau work)
 * should follow this same shape rather than inventing a new one.
 * <p>
 * WHY findEmployeeById returns Optional instead of throwing: HrService's
 * own getEmployee() throws ResourceNotFoundException on a miss, which is
 * correct for HR's own API (a missing employee there is usually a bug in
 * the request). For a cross-module existence check, though, "not found" is
 * an expected, non-exceptional outcome — a caller asking "does this person
 * already have an HR record" shouldn't need a try/catch for the normal
 * case. This facade absorbs that translation so callers get a natural
 * Optional-based API instead of exception-driven control flow.
 */
@Service
@RequiredArgsConstructor
public class HrFacade {

    private final HrService hrService;

    /**
     * Creates a new HR employee record — full BCEA leave-balance seeding,
     * proper sequential employee numbering, exactly as if created through
     * HR's own UI. Nothing about this bypasses any HR validation or
     * business rule; it's the same HrService.createEmployee() the HR
     * module's own controller calls.
     */
    public EmployeeResponse createEmployee(TenantId tenantId, CreateEmployeeRequest req) {
        return hrService.createEmployee(tenantId, req);
    }

    /**
     * Updates an existing HR employee record. Throws ResourceNotFoundException
     * if the employee doesn't exist for this tenant — a genuine caller error
     * (you can't update a record you don't have the id for), not a case that
     * should be silently absorbed the way findEmployeeById's miss is.
     */
    public EmployeeResponse updateEmployee(TenantId tenantId, UUID employeeId, CreateEmployeeRequest req) {
        return hrService.updateEmployee(tenantId, employeeId, req);
    }

    /**
     * Looks up an employee by id. Returns empty rather than throwing when
     * not found — see class Javadoc for why this differs from HR's own
     * internal getEmployee().
     */
    public Optional<EmployeeResponse> findEmployeeById(TenantId tenantId, UUID employeeId) {
        try {
            return Optional.of(hrService.getEmployee(tenantId, employeeId));
        } catch (ResourceNotFoundException e) {
            return Optional.empty();
        }
    }

    /**
     * Cheap existence check for callers that only need a yes/no answer
     * (e.g. "has this applicant already been converted") without paying
     * for a full EmployeeResponse mapping.
     */
    public boolean employeeExists(TenantId tenantId, UUID employeeId) {
        return findEmployeeById(tenantId, employeeId).isPresent();
    }
}