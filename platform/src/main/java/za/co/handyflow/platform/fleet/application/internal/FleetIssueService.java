package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fleet.domain.model.FleetIssue;
import za.co.handyflow.platform.fleet.domain.model.IssueSeverity;
import za.co.handyflow.platform.fleet.domain.model.IssueStatus;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.repository.FleetIssueRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleRepository;
import za.co.handyflow.platform.fleet.dto.CreateIssueRequest;
import za.co.handyflow.platform.fleet.dto.IssueResponse;
import za.co.handyflow.platform.fleet.dto.UpdateIssueStatusRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FleetIssueService {

    private final FleetIssueRepository issueRepository;
    private final VehicleRepository vehicleRepository;

    @Transactional(readOnly = true)
    public Page<IssueResponse> list(TenantId tenantId, UUID vehicleId, IssueStatus status, Pageable pageable) {
        Page<FleetIssue> page;
        if (vehicleId != null && status != null) {
            page = issueRepository.findByVehicleAndStatus(tenantId, vehicleId, status, pageable);
        } else if (vehicleId != null) {
            page = issueRepository.findByVehicle(tenantId, vehicleId, pageable);
        } else if (status != null) {
            page = issueRepository.findByStatus(tenantId, status, pageable);
        } else {
            page = issueRepository.findAllForTenant(tenantId, pageable);
        }
        return page.map(i -> toResponse(i, tenantId));
    }

    @Transactional(readOnly = true)
    public IssueResponse get(TenantId tenantId, UUID id) {
        return toResponse(find(tenantId, id), tenantId);
    }

    @Transactional
    public IssueResponse create(TenantId tenantId, UUID reportedBy, CreateIssueRequest req) {
        Vehicle vehicle = vehicleRepository.findActiveById(tenantId, req.vehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", req.vehicleId().toString()));
        FleetIssue issue = FleetIssue.manual(tenantId, vehicle.getId(), req.title().trim(),
                req.description(), req.severity() != null ? req.severity() : IssueSeverity.MEDIUM, reportedBy);
        issueRepository.save(issue);
        log.info("Fleet issue raised id={} vehicle={} tenant={}", issue.getId(), vehicle.getId(), tenantId);
        return toResponse(issue, tenantId);
    }

    @Transactional
    public IssueResponse updateStatus(TenantId tenantId, UUID id, UUID byUser, UpdateIssueStatusRequest req) {
        FleetIssue issue = find(tenantId, id);
        issue.changeStatus(req.status(), req.note(), byUser);
        issueRepository.save(issue);
        return toResponse(issue, tenantId);
    }

    /**
     * Called by InspectionService when an inspection completes. Skips a defect that is
     * already open on the same vehicle (same title), so a recurring failure — an unrepaired
     * tyre failing every morning — doesn't bury the register in duplicates.
     * Returns true if a new issue was raised.
     */
    boolean raiseFromInspection(TenantId tenantId, UUID vehicleId, UUID inspectionId, UUID resultId,
                                String title, String description, IssueSeverity severity, UUID reportedBy) {
        if (issueRepository.existsActiveWithTitle(tenantId, vehicleId, title, IssueStatus.ACTIVE)) {
            return false;
        }
        issueRepository.save(FleetIssue.fromInspection(tenantId, vehicleId, inspectionId, resultId,
                title, description, severity, reportedBy));
        return true;
    }

    private FleetIssue find(TenantId tenantId, UUID id) {
        return issueRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FleetIssue", id.toString()));
    }

    private IssueResponse toResponse(FleetIssue i, TenantId tenantId) {
        String reg = vehicleRepository.findActiveById(tenantId, i.getVehicleId())
                .map(Vehicle::getRegistration).orElse(null);
        return new IssueResponse(i.getId(), i.getVehicleId(), reg, i.getSource().name(),
                i.getSourceInspectionId(), i.getTitle(), i.getDescription(), i.getSeverity().name(),
                i.getStatus().name(), i.getCreatedAt(), i.getResolvedAt(), i.getResolutionNote());
    }
}