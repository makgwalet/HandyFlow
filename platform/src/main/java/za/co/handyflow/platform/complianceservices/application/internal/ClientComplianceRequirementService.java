package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.ClientComplianceRequirement;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientComplianceRequirementRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceClientRepository;
import za.co.handyflow.platform.complianceservices.dto.ClientComplianceRequirementResponse;
import za.co.handyflow.platform.complianceservices.dto.CreateClientComplianceRequirementRequest;
import za.co.handyflow.platform.complianceservices.dto.UpdateClientComplianceRequirementRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client-scoped counterpart to compliancetender.ComplianceRequirementService
 * — same reasoning throughout, scoped by client in addition to tenant:
 * deliberately no DELETE endpoint (see ClientComplianceRequirement's own
 * Javadoc for why), getRequirements deduplicates to the latest version
 * per code in Java rather than a fragile JPQL GROUP BY, create() rejects
 * a duplicate code with a message pointing to the new-version endpoint,
 * createNewVersion() rejects a stale view where someone else already
 * created a newer version.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientComplianceRequirementService {

    private final ClientComplianceRequirementRepository requirementRepository;
    private final ComplianceClientRepository clientRepository;

    @Transactional(readOnly = true)
    public List<ClientComplianceRequirementResponse> getRequirements(TenantId tenantId, UUID clientId) {
        requireClient(tenantId, clientId);
        Map<String, ClientComplianceRequirement> latestByCode = requirementRepository.findAllForClient(tenantId, clientId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        ClientComplianceRequirement::getCode, r -> r,
                        (a, b) -> a.getRequirementVersion() >= b.getRequirementVersion() ? a : b));
        return latestByCode.values().stream()
                .sorted(Comparator.comparing(ClientComplianceRequirement::getCode))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClientComplianceRequirementResponse getRequirement(TenantId tenantId, UUID id) {
        return toResponse(find(tenantId, id));
    }

    @Transactional
    public ClientComplianceRequirementResponse create(TenantId tenantId, UUID clientId,
                                                       CreateClientComplianceRequirementRequest req, UUID createdBy) {
        requireClient(tenantId, clientId);
        requirementRepository.findLatestByCode(tenantId, clientId, req.code().toUpperCase()).ifPresent(existing -> {
            throw new HandyFlowException(
                    "A requirement with code '" + existing.getCode() + "' already exists for this client (currently v"
                            + existing.getRequirementVersion() + ") — update it to create a new version instead of a duplicate code",
                    HttpStatus.CONFLICT, "REQUIREMENT_CODE_ALREADY_EXISTS");
        });
        ClientComplianceRequirement requirement = ClientComplianceRequirement.create(tenantId, clientId, req.code(),
                req.name(), req.appliesTo(), req.evidenceType(), req.required(), createdBy);
        requirementRepository.save(requirement);
        log.info("Client compliance requirement created id={} client={} code={} tenant={}",
                requirement.getId(), clientId, requirement.getCode(), tenantId);
        return toResponse(requirement);
    }

    @Transactional
    public ClientComplianceRequirementResponse createNewVersion(TenantId tenantId, UUID id,
                                                                 UpdateClientComplianceRequirementRequest req, UUID createdBy) {
        ClientComplianceRequirement current = find(tenantId, id);
        ClientComplianceRequirement latest = requirementRepository.findLatestByCode(tenantId, current.getClientId(), current.getCode())
                .orElse(current);
        if (latest.getRequirementVersion() != current.getRequirementVersion()) {
            throw new HandyFlowException(
                    "This is version " + current.getRequirementVersion() + ", but v" + latest.getRequirementVersion()
                            + " is now the latest — refresh and create the new version from the current one",
                    HttpStatus.CONFLICT, "NOT_LATEST_VERSION");
        }
        ClientComplianceRequirement next = current.newVersion(req.name(), req.appliesTo(), req.evidenceType(), req.required(), createdBy);
        requirementRepository.save(next);
        log.info("Client compliance requirement new version created code={} version={} tenant={}",
                next.getCode(), next.getRequirementVersion(), tenantId);
        return toResponse(next);
    }

    private void requireClient(TenantId tenantId, UUID clientId) {
        if (clientRepository.findByIdForTenant(tenantId, clientId).isEmpty())
            throw new ResourceNotFoundException("ComplianceClient", clientId.toString());
    }

    private ClientComplianceRequirement find(TenantId tenantId, UUID id) {
        return requirementRepository.findByIdForTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("ClientComplianceRequirement", id.toString()));
    }

    private ClientComplianceRequirementResponse toResponse(ClientComplianceRequirement r) {
        return new ClientComplianceRequirementResponse(r.getId(), r.getClientId(), r.getCode(), r.getName(),
                r.getAppliesTo(), r.getEvidenceType(), r.isRequired(), r.getRequirementVersion(), r.getCreatedAt());
    }
}
