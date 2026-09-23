package za.co.handyflow.platform.compliancetender.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.compliancetender.domain.model.ComplianceRequirement;
import za.co.handyflow.platform.compliancetender.domain.repository.ComplianceRequirementRepository;
import za.co.handyflow.platform.compliancetender.dto.ComplianceRequirementResponse;
import za.co.handyflow.platform.compliancetender.dto.CreateComplianceRequirementRequest;
import za.co.handyflow.platform.compliancetender.dto.UpdateComplianceRequirementRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Deliberately no delete endpoint. ComplianceRequirement's own Javadoc is
 * explicit about why it's versioned rather than edited in place: "a past
 * application's readiness check should be judged against the requirement
 * version that was actually in force when it ran, not silently
 * reinterpreted against today's rules." Deleting a version would break
 * exactly that guarantee for any tender that referenced it — the same
 * audit reasoning behind TenderSubmissionSnapshot never being editable
 * either. A requirement that's no longer wanted should be superseded by
 * a new version with {@code required = false}, not removed from history.
 * <p>
 * {@code getRequirements} returns one row per code — the CURRENT
 * (highest) version only, deduplicated in Java rather than via a more
 * fragile correlated-subquery/GROUP BY in JPQL, since this list is
 * expected to be small (dozens of requirement codes per tenant, not
 * thousands) and the simpler approach is easier to verify correct.
 * {@code getRequirementHistory} is the separate, explicit way to see
 * every version of one code, for whoever actually needs the audit trail.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceRequirementService {

    private final ComplianceRequirementRepository requirementRepository;

    @Transactional(readOnly = true)
    public List<ComplianceRequirementResponse> getRequirements(TenantId tenantId) {
        Map<String, ComplianceRequirement> latestByCode = requirementRepository.findAllForTenant(tenantId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        ComplianceRequirement::getCode, r -> r,
                        (a, b) -> a.getRequirementVersion() >= b.getRequirementVersion() ? a : b));
        return latestByCode.values().stream()
                .sorted(Comparator.comparing(ComplianceRequirement::getCode))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ComplianceRequirementResponse> getRequirementHistory(TenantId tenantId, String code) {
        return requirementRepository.findAllVersionsByCode(tenantId, code.toUpperCase()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ComplianceRequirementResponse getRequirement(TenantId tenantId, UUID id) {
        return toResponse(find(tenantId, id));
    }

    @Transactional
    public ComplianceRequirementResponse create(TenantId tenantId, CreateComplianceRequirementRequest req, UUID createdBy) {
        requirementRepository.findLatestByCode(tenantId, req.code().toUpperCase()).ifPresent(existing -> {
            throw new HandyFlowException(
                    "A requirement with code '" + existing.getCode() + "' already exists (currently v"
                            + existing.getRequirementVersion() + ") — update it to create a new version instead of a duplicate code",
                    HttpStatus.CONFLICT, "REQUIREMENT_CODE_ALREADY_EXISTS");
        });
        ComplianceRequirement requirement = ComplianceRequirement.create(tenantId, req.code(), req.name(),
                req.appliesTo(), req.evidenceType(), req.required(), createdBy);
        requirementRepository.save(requirement);
        log.info("Compliance requirement created id={} code={} tenant={}", requirement.getId(), requirement.getCode(), tenantId);
        return toResponse(requirement);
    }

    /** Creates a new version of the requirement identified by {@code id} — the existing row is left untouched. */
    @Transactional
    public ComplianceRequirementResponse createNewVersion(TenantId tenantId, UUID id,
                                                           UpdateComplianceRequirementRequest req, UUID createdBy) {
        ComplianceRequirement current = find(tenantId, id);
        ComplianceRequirement latest = requirementRepository.findLatestByCode(tenantId, current.getCode())
                .orElse(current);
        if (latest.getRequirementVersion() != current.getRequirementVersion()) {
            throw new HandyFlowException(
                    "This is version " + current.getRequirementVersion() + ", but v" + latest.getRequirementVersion()
                            + " is now the latest — refresh and create the new version from the current one",
                    HttpStatus.CONFLICT, "NOT_LATEST_VERSION");
        }
        ComplianceRequirement next = current.newVersion(req.name(), req.appliesTo(), req.evidenceType(), req.required(), createdBy);
        requirementRepository.save(next);
        log.info("Compliance requirement new version created code={} version={} tenant={}", next.getCode(), next.getRequirementVersion(), tenantId);
        return toResponse(next);
    }

    private ComplianceRequirement find(TenantId tenantId, UUID id) {
        return requirementRepository.findByIdForTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("ComplianceRequirement", id.toString()));
    }

    private ComplianceRequirementResponse toResponse(ComplianceRequirement r) {
        return new ComplianceRequirementResponse(r.getId(), r.getCode(), r.getName(), r.getAppliesTo(),
                r.getEvidenceType(), r.isRequired(), r.getRequirementVersion(), r.getCreatedAt());
    }
}
