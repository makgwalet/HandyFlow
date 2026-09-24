package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTenderPersonnel;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientTenderPersonnelRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientTenderRepository;
import za.co.handyflow.platform.complianceservices.dto.AddClientTenderPersonnelRequest;
import za.co.handyflow.platform.complianceservices.dto.ClientTenderPersonnelResponse;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.hr.dto.EmployeeResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Client-scoped counterpart to compliancetender.TenderPersonnelService —
 * identical write/read asymmetry: strict at write time (the employee
 * must actually exist in this tenant's own HR before being referenced —
 * see ClientTenderPersonnel's own Javadoc for why it's THIS tenant's HR,
 * not the client's), tolerant at read time (a since-deleted employee
 * doesn't make an existing tender's personnel list unreadable, it just
 * reports employeeFound=false for that line).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientTenderPersonnelService {

    private final ClientTenderPersonnelRepository personnelRepository;
    private final ClientTenderRepository tenderRepository;
    private final HrFacade hrFacade;

    @Transactional
    public ClientTenderPersonnelResponse addPersonnel(TenantId tenantId, UUID clientTenderId,
                                                       AddClientTenderPersonnelRequest req, UUID createdBy) {
        tenderRepository.findByIdForTenant(tenantId, clientTenderId)
                .orElseThrow(() -> new ResourceNotFoundException("ClientTender", clientTenderId.toString()));

        EmployeeResponse employee = hrFacade.findEmployeeById(tenantId, req.employeeId())
                .orElseThrow(() -> new HandyFlowException(
                        "No HR employee found with id " + req.employeeId() + " — add them in HR first",
                        HttpStatus.BAD_REQUEST, "EMPLOYEE_NOT_FOUND"));

        ClientTenderPersonnel personnel = ClientTenderPersonnel.create(tenantId, clientTenderId, req.employeeId(),
                req.role(), createdBy);
        personnelRepository.save(personnel);
        log.info("Client tender personnel added tender={} employee={} role={} tenant={}",
                clientTenderId, req.employeeId(), req.role(), tenantId);
        return toResponse(personnel, employee);
    }

    @Transactional(readOnly = true)
    public List<ClientTenderPersonnelResponse> getPersonnel(TenantId tenantId, UUID clientTenderId) {
        tenderRepository.findByIdForTenant(tenantId, clientTenderId)
                .orElseThrow(() -> new ResourceNotFoundException("ClientTender", clientTenderId.toString()));

        return personnelRepository.findByTender(tenantId, clientTenderId).stream()
                .map(p -> toResponse(p, hrFacade.findEmployeeById(tenantId, p.getEmployeeId()).orElse(null)))
                .toList();
    }

    @Transactional
    public void removePersonnel(TenantId tenantId, UUID id) {
        ClientTenderPersonnel personnel = personnelRepository.findByIdForTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("ClientTenderPersonnel", id.toString()));
        personnelRepository.delete(personnel);
        log.info("Client tender personnel removed id={} tenant={}", id, tenantId);
    }

    private ClientTenderPersonnelResponse toResponse(ClientTenderPersonnel p, EmployeeResponse employee) {
        return new ClientTenderPersonnelResponse(p.getId(), p.getClientTenderId(), p.getEmployeeId(), p.getRole(),
                employee != null, employee != null ? employee.fullName() : null,
                employee != null ? employee.employeeNumber() : null, p.getCreatedAt());
    }
}
