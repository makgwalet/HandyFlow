package za.co.handyflow.platform.compliancetender.application.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.compliancetender.domain.model.Tender;
import za.co.handyflow.platform.compliancetender.domain.model.TenderPersonnel;
import za.co.handyflow.platform.compliancetender.domain.model.TenderRequirement;
import za.co.handyflow.platform.compliancetender.domain.model.TenderSubmissionSnapshot;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderPersonnelRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderRequirementRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderSubmissionSnapshotRepository;
import za.co.handyflow.platform.compliancetender.dto.TenderSnapshotData;
import za.co.handyflow.platform.compliancetender.dto.TenderSnapshotResponse;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Captures and reads frozen tender-submission snapshots — see
 * TenderSubmissionSnapshot's own Javadoc for the "reference, don't copy"
 * design this exists to complete. Called from TenderService.transition()
 * automatically whenever a tender actually reaches SUBMITTED — capturing
 * a snapshot is not something a caller can forget to trigger separately,
 * since "submitted with no record of what was submitted" would defeat
 * the entire point.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenderSnapshotService {

    private final TenderRepository tenderRepository;
    private final TenderRequirementRepository requirementRepository;
    private final TenderPersonnelRepository personnelRepository;
    private final TenderSubmissionSnapshotRepository snapshotRepository;
    private final HrFacade hrFacade;
    private final ObjectMapper objectMapper;

    @Transactional
    public TenderSnapshotResponse captureSnapshot(TenantId tenantId, UUID tenderId, UUID submittedBy) {
        Tender tender = tenderRepository.findByIdForTenant(tenantId, tenderId)
                .orElseThrow(() -> new ResourceNotFoundException("Tender", tenderId.toString()));

        List<TenderSnapshotData.RequirementSnapshot> requirements = requirementRepository
                .findByTender(tenantId, tenderId).stream()
                .map(this::toRequirementSnapshot)
                .toList();

        List<TenderSnapshotData.PersonnelSnapshot> personnel = personnelRepository
                .findByTender(tenantId, tenderId).stream()
                .map(p -> toPersonnelSnapshot(tenantId, p))
                .toList();

        TenderSnapshotData data = new TenderSnapshotData(
                tender.getId(), tender.getTenderNumber(), tender.getName(), tender.getTenderAuthority(),
                tender.getAuthorityReferenceNumber(), tender.getClosingDate(), tender.getEstimatedValue(),
                tender.getIndustry(), tender.getRequiredClassOfWork(), tender.getStatus(),
                requirements, personnel, Instant.now());

        String json = serialize(data);
        int snapshotNumber = (int) snapshotRepository.countByTender(tenantId, tenderId) + 1;

        TenderSubmissionSnapshot snapshot = TenderSubmissionSnapshot.create(
                tenantId, tenderId, snapshotNumber, json, submittedBy);
        snapshotRepository.save(snapshot);

        log.info("Tender submission snapshot captured tender={} snapshotNumber={} requirements={} personnel={} tenant={}",
                tenderId, snapshotNumber, requirements.size(), personnel.size(), tenantId);

        return new TenderSnapshotResponse(snapshot.getId(), tenderId, snapshotNumber, data, snapshot.getSubmittedAt());
    }

    @Transactional(readOnly = true)
    public List<TenderSnapshotResponse> getSnapshots(TenantId tenantId, UUID tenderId) {
        return snapshotRepository.findByTender(tenantId, tenderId).stream()
                .map(s -> new TenderSnapshotResponse(s.getId(), s.getTenderId(), s.getSnapshotNumber(),
                        deserialize(s.getSnapshotJson()), s.getSubmittedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public TenderSnapshotResponse getSnapshot(TenantId tenantId, UUID id) {
        TenderSubmissionSnapshot s = snapshotRepository.findByIdForTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("TenderSubmissionSnapshot", id.toString()));
        return new TenderSnapshotResponse(s.getId(), s.getTenderId(), s.getSnapshotNumber(),
                deserialize(s.getSnapshotJson()), s.getSubmittedAt());
    }

    private TenderSnapshotData.RequirementSnapshot toRequirementSnapshot(TenderRequirement r) {
        return new TenderSnapshotData.RequirementSnapshot(r.getDescription(), r.getSource(), r.getStatus());
    }

    // Baking in the employee's current name/number here, at capture time,
    // is deliberate and the one place in this module HR data is copied
    // rather than referenced — see TenderSnapshotData's own Javadoc.
    private TenderSnapshotData.PersonnelSnapshot toPersonnelSnapshot(TenantId tenantId, TenderPersonnel p) {
        var employee = hrFacade.findEmployeeById(tenantId, p.getEmployeeId()).orElse(null);
        return new TenderSnapshotData.PersonnelSnapshot(p.getEmployeeId(), p.getRole(),
                employee != null ? employee.fullName() : "(employee record no longer available)",
                employee != null ? employee.employeeNumber() : null);
    }

    private String serialize(TenderSnapshotData data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            // A snapshot that fails to serialize is worse than useless — it would silently
            // let a tender be marked SUBMITTED with no actual record of what was submitted.
            // Fail the whole transition rather than continue with a missing snapshot.
            throw new HandyFlowException("Failed to build tender submission snapshot: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR, "SNAPSHOT_SERIALIZATION_FAILED");
        }
    }

    private TenderSnapshotData deserialize(String json) {
        try {
            return objectMapper.readValue(json, TenderSnapshotData.class);
        } catch (JsonProcessingException e) {
            throw new HandyFlowException("Failed to read tender submission snapshot: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR, "SNAPSHOT_DESERIALIZATION_FAILED");
        }
    }
}
