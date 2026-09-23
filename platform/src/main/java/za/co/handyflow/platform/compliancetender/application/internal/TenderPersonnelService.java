package za.co.handyflow.platform.compliancetender.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.compliancetender.domain.model.TenderPersonnel;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderPersonnelRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderRepository;
import za.co.handyflow.platform.compliancetender.dto.AddTenderPersonnelRequest;
import za.co.handyflow.platform.compliancetender.dto.TenderPersonnelResponse;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.hr.dto.EmployeeResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * The first real cross-module reference in this module — see
 * TenderPersonnel's own Javadoc for the reference-not-copy design.
 * addPersonnel validates the employee actually exists in HR before
 * storing the reference (fail at write time, not silently at read time),
 * but getPersonnel tolerates a since-deleted employee gracefully
 * (employeeFound=false) rather than throwing — a tender shouldn't become
 * unreadable because an old reference no longer resolves.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenderPersonnelService {

    private final TenderPersonnelRepository personnelRepository;
    private final TenderRepository tenderRepository;
    private final HrFacade hrFacade;

    @Transactional
    public TenderPersonnelResponse addPersonnel(TenantId tenantId, UUID tenderId,
                                                AddTenderPersonnelRequest req, UUID createdBy) {
        tenderRepository.findByIdForTenant(tenantId, tenderId)
                .orElseThrow(() -> new ResourceNotFoundException("Tender", tenderId.toString()));

        EmployeeResponse employee = hrFacade.findEmployeeById(tenantId, req.employeeId())
                .orElseThrow(() -> new HandyFlowException(
                        "No HR employee found with id " + req.employeeId() + " — add them in HR first",
                        HttpStatus.BAD_REQUEST, "EMPLOYEE_NOT_FOUND"));

        TenderPersonnel personnel = TenderPersonnel.create(tenantId, tenderId, req.employeeId(), req.role(), createdBy);
        personnelRepository.save(personnel);
        log.info("Tender personnel added tender={} employee={} role={} tenant={}",
                tenderId, req.employeeId(), req.role(), tenantId);
        return toResponse(personnel, employee);
    }

    @Transactional(readOnly = true)
    public List<TenderPersonnelResponse> getPersonnel(TenantId tenantId, UUID tenderId) {
        tenderRepository.findByIdForTenant(tenantId, tenderId)
                .orElseThrow(() -> new ResourceNotFoundException("Tender", tenderId.toString()));

        return personnelRepository.findByTender(tenantId, tenderId).stream()
                .map(p -> toResponse(p, hrFacade.findEmployeeById(tenantId, p.getEmployeeId()).orElse(null)))
                .toList();
    }

    @Transactional
    public void removePersonnel(TenantId tenantId, UUID id) {
        TenderPersonnel personnel = personnelRepository.findByIdForTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("TenderPersonnel", id.toString()));
        personnelRepository.delete(personnel);
        log.info("Tender personnel removed id={} tenant={}", id, tenantId);
    }

    private TenderPersonnelResponse toResponse(TenderPersonnel p, EmployeeResponse employee) {
        return new TenderPersonnelResponse(p.getId(), p.getTenderId(), p.getEmployeeId(), p.getRole(),
                employee != null, employee != null ? employee.fullName() : null,
                employee != null ? employee.employeeNumber() : null, p.getCreatedAt());
    }
}
