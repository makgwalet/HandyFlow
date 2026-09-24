package za.co.handyflow.platform.complianceservices.application.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTender;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTenderPersonnel;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTenderRequirement;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTenderSubmissionSnapshot;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientTenderPersonnelRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientTenderRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientTenderRequirementRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientTenderSubmissionSnapshotRepository;
import za.co.handyflow.platform.complianceservices.dto.ClientTenderSnapshotData;
import za.co.handyflow.platform.complianceservices.dto.ClientTenderSnapshotResponse;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Client-scoped counterpart to compliancetender.TenderSnapshotService —
 * same capture-on-SUBMITTED intent, wired automatically into
 * ClientTenderService.transition(), same fail-loudly-on-serialization-
 * failure discipline, same per-tender snapshotNumber incrementing
 * rather than overwriting. Captures the tender's own fields, its
 * requirement matrix, and (since the design question was resolved) its
 * personnel — each personnel line's name/number baked in at capture
 * time, the one deliberate exception to reference-not-copy here, same
 * reasoning as compliancetender.TenderSnapshotService's own personnel
 * capture.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientTenderSnapshotService {

    private final ClientTenderRepository tenderRepository;
    private final ClientTenderRequirementRepository requirementRepository;
    private final ClientTenderPersonnelRepository personnelRepository;
    private final ClientTenderSubmissionSnapshotRepository snapshotRepository;
    private final HrFacade hrFacade;
    private final ObjectMapper objectMapper;

    @Transactional
    public ClientTenderSnapshotResponse captureSnapshot(TenantId tenantId, UUID clientTenderId, UUID submittedBy) {
        ClientTender tender = tenderRepository.findByIdForTenant(tenantId, clientTenderId)
                .orElseThrow(() -> new ResourceNotFoundException("ClientTender", clientTenderId.toString()));

        List<ClientTenderSnapshotData.RequirementSnapshot> requirements = requirementRepository
                .findByTender(tenantId, clientTenderId).stream()
                .map(this::toRequirementSnapshot)
                .toList();

        List<ClientTenderSnapshotData.PersonnelSnapshot> personnel = personnelRepository
                .findByTender(tenantId, clientTenderId).stream()
                .map(p -> toPersonnelSnapshot(tenantId, p))
                .toList();

        ClientTenderSnapshotData data = new ClientTenderSnapshotData(
                tender.getId(), tender.getClientId(), tender.getTenderNumber(), tender.getName(),
                tender.getTenderAuthority(), tender.getAuthorityReferenceNumber(), tender.getClosingDate(),
                tender.getEstimatedValue(), tender.getIndustry(), tender.getRequiredClassOfWork(),
                tender.getStatus(), requirements, personnel, Instant.now());

        String json = serialize(data);
        int snapshotNumber = (int) snapshotRepository.countByTender(tenantId, clientTenderId) + 1;

        ClientTenderSubmissionSnapshot snapshot = ClientTenderSubmissionSnapshot.create(
                tenantId, clientTenderId, snapshotNumber, json, submittedBy);
        snapshotRepository.save(snapshot);

        log.info("Client tender submission snapshot captured tender={} snapshotNumber={} requirements={} tenant={}",
                clientTenderId, snapshotNumber, requirements.size(), tenantId);

        return new ClientTenderSnapshotResponse(snapshot.getId(), clientTenderId, snapshotNumber, data, snapshot.getSubmittedAt());
    }

    @Transactional(readOnly = true)
    public List<ClientTenderSnapshotResponse> getSnapshots(TenantId tenantId, UUID clientTenderId) {
        return snapshotRepository.findByTender(tenantId, clientTenderId).stream()
                .map(s -> new ClientTenderSnapshotResponse(s.getId(), s.getClientTenderId(), s.getSnapshotNumber(),
                        deserialize(s.getSnapshotJson()), s.getSubmittedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ClientTenderSnapshotResponse getSnapshot(TenantId tenantId, UUID id) {
        ClientTenderSubmissionSnapshot s = snapshotRepository.findByIdForTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("ClientTenderSubmissionSnapshot", id.toString()));
        return new ClientTenderSnapshotResponse(s.getId(), s.getClientTenderId(), s.getSnapshotNumber(),
                deserialize(s.getSnapshotJson()), s.getSubmittedAt());
    }

    private ClientTenderSnapshotData.RequirementSnapshot toRequirementSnapshot(ClientTenderRequirement r) {
        return new ClientTenderSnapshotData.RequirementSnapshot(r.getDescription(), r.getSource(), r.getStatus());
    }

    // Baking in the employee's current name/number here, at capture
    // time, is deliberate — see this class's own Javadoc.
    private ClientTenderSnapshotData.PersonnelSnapshot toPersonnelSnapshot(TenantId tenantId, ClientTenderPersonnel p) {
        var employee = hrFacade.findEmployeeById(tenantId, p.getEmployeeId()).orElse(null);
        return new ClientTenderSnapshotData.PersonnelSnapshot(p.getEmployeeId(), p.getRole(),
                employee != null ? employee.fullName() : "(employee record no longer available)",
                employee != null ? employee.employeeNumber() : null);
    }

    private String serialize(ClientTenderSnapshotData data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new HandyFlowException("Failed to build client tender submission snapshot: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR, "SNAPSHOT_SERIALIZATION_FAILED");
        }
    }

    private ClientTenderSnapshotData deserialize(String json) {
        try {
            return objectMapper.readValue(json, ClientTenderSnapshotData.class);
        } catch (JsonProcessingException e) {
            throw new HandyFlowException("Failed to read client tender submission snapshot: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR, "SNAPSHOT_DESERIALIZATION_FAILED");
        }
    }
}
