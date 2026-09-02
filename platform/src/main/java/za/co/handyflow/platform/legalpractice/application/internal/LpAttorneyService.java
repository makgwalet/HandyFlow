package za.co.handyflow.platform.legalpractice.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.hr.dto.EmployeeResponse;
import za.co.handyflow.platform.legalpractice.domain.model.LpAttorney;
import za.co.handyflow.platform.legalpractice.domain.repository.LpAttorneyRepository;
import za.co.handyflow.platform.legalpractice.dto.CreateLpAttorneyRequest;
import za.co.handyflow.platform.legalpractice.dto.LpAttorneyResponse;
import za.co.handyflow.platform.legalpractice.dto.UpdateLpAttorneyRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * CRUD for the firm's own attorneys/staff. {@code employeeId} is optional
 * and unvalidated — see {@code LpAttorney}'s own Javadoc — so every
 * {@link HrFacade#findEmployeeById} lookup here is best-effort and never
 * throws or blocks the operation when the employee record is absent.
 * {@code hr} is declared in this module's {@code allowedDependencies}
 * specifically for this call (fixed after an initial draft omitted it —
 * see {@code package-info.java}'s own note).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LpAttorneyService {

    private final LpAttorneyRepository attorneyRepo;
    private final HrFacade hrFacade;

    @Transactional(readOnly = true)
    public Page<LpAttorneyResponse> listAttorneys(TenantId tenantId, Pageable pageable) {
        return attorneyRepo.findAllActive(tenantId, pageable).map(a -> toResponse(tenantId, a));
    }

    @Transactional(readOnly = true)
    public LpAttorneyResponse getAttorney(TenantId tenantId, UUID attorneyId) {
        return toResponse(tenantId, findOwn(tenantId, attorneyId));
    }

    @Transactional
    public LpAttorneyResponse createAttorney(TenantId tenantId, CreateLpAttorneyRequest req) {
        LpAttorney attorney = LpAttorney.create(tenantId, req.name(), req.email(), req.phone(),
                req.role(), req.admissionNumber(), req.hourlyRate(), req.employeeId());
        attorneyRepo.save(attorney);
        log.info("Created legal practice attorney={} name={} tenant={}", attorney.getId(), attorney.getName(), tenantId);
        return toResponse(tenantId, attorney);
    }

    @Transactional
    public LpAttorneyResponse updateAttorney(TenantId tenantId, UUID attorneyId, UpdateLpAttorneyRequest req) {
        LpAttorney attorney = findOwn(tenantId, attorneyId);
        attorney.update(req.name(), req.email(), req.phone(), req.role(),
                req.admissionNumber(), req.hourlyRate(), req.employeeId());
        attorneyRepo.save(attorney);
        return toResponse(tenantId, attorney);
    }

    @Transactional
    public LpAttorneyResponse deactivateAttorney(TenantId tenantId, UUID attorneyId) {
        LpAttorney attorney = findOwn(tenantId, attorneyId);
        attorney.deactivate();
        attorneyRepo.save(attorney);
        return toResponse(tenantId, attorney);
    }

    @Transactional
    public LpAttorneyResponse reactivateAttorney(TenantId tenantId, UUID attorneyId) {
        LpAttorney attorney = findOwn(tenantId, attorneyId);
        attorney.reactivate();
        attorneyRepo.save(attorney);
        return toResponse(tenantId, attorney);
    }

    /** Hard delete — ADMIN-gated at the controller. */
    @Transactional
    public void deleteAttorney(TenantId tenantId, UUID attorneyId) {
        LpAttorney attorney = findOwn(tenantId, attorneyId);
        attorneyRepo.delete(attorney);
        log.info("Deleted legal practice attorney={} tenant={}", attorneyId, tenantId);
    }

    LpAttorney findOwn(TenantId tenantId, UUID attorneyId) {
        return attorneyRepo.findActiveById(tenantId, attorneyId)
                .orElseThrow(() -> new ResourceNotFoundException("LpAttorney", attorneyId.toString()));
    }

    private LpAttorneyResponse toResponse(TenantId tenantId, LpAttorney a) {
        String employeeName = null;
        String employeeEmail = null;
        if (a.getEmployeeId() != null) {
            Optional<EmployeeResponse> emp = hrFacade.findEmployeeById(tenantId, a.getEmployeeId());
            if (emp.isPresent()) {
                employeeName = emp.get().fullName();
                employeeEmail = emp.get().email();
            }
        }
        return new LpAttorneyResponse(a.getId(), a.getName(), a.getEmail(), a.getPhone(), a.getRole(),
                a.getAdmissionNumber(), a.getHourlyRate(), a.getEmployeeId(), employeeName, employeeEmail,
                a.isActive(), a.getCreatedAt(), a.getUpdatedAt());
    }
}
