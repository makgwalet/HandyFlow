package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.*;
import za.co.handyflow.platform.legalcompliance.dto.ApplicabilityDtos.GenerateObligationRequest;
import za.co.handyflow.platform.legalcompliance.dto.ApplicabilityDtos.GenerateObligationResponse;
import za.co.handyflow.platform.legalcompliance.dto.RegulatoryObligationResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Turns an APPLICABLE requirement into an assigned obligation.
 * Needs: an APPROVED APPLICABLE assessment, a requirement still in a CURRENT version, a named owner and a first date.
 * Idempotent: a live obligation already linked to the requirement is returned, not duplicated (and the DB enforces it).
 */
@Service
@RequiredArgsConstructor
public class ObligationGenerationService {

    private final RegulatoryRequirementRepository requirements;
    private final RegulatoryVersionRepository versions;
    private final RegulatoryInstrumentRepository instruments;
    private final ApplicabilityAssessmentRepository assessments;
    private final RegulatoryObligationLinkRepository obligations;
    private final RegulatoryAuditService audit;

    @Transactional
    public GenerateObligationResponse generate(TenantId tenantId, UUID requirementId, UUID userId, String userName, GenerateObligationRequest req) {
        UUID tid = tenantId.getValue();
        RegulatoryRequirement r = requirements.findVisible(tid, requirementId)
                .orElseThrow(() -> new ResourceNotFoundException("Requirement", requirementId.toString()));

        var existing = obligations.findLiveByRequirement(tenantId, requirementId);
        if (existing.isPresent()) return new GenerateObligationResponse(false, RegulatoryObligationResponse.of(existing.get()));

        ApplicabilityAssessment a = assessments.findOne(tenantId, requirementId, AssessmentStatus.APPROVED)
                .filter(x -> x.getDecision() == ApplicabilityDecision.APPLICABLE)
                .orElseThrow(() -> new HandyFlowException(
                        "This requirement has no approved APPLICABLE assessment — assess it first", HttpStatus.CONFLICT, "NOT_ASSESSED_APPLICABLE"));

        RegulatoryVersion v = versions.findVisible(tid, r.getVersionId()).orElseThrow();
        if (v.getStatus() != VersionStatus.CURRENT)
            throw new HandyFlowException("The requirement's regulatory version is no longer current", HttpStatus.CONFLICT, "REQUIREMENT_NOT_CURRENT");
        if (req.reviewDate().isBefore(LocalDate.now()))
            throw new HandyFlowException("The first review date cannot be in the past", HttpStatus.BAD_REQUEST, "VALIDATION");

        RegulatoryInstrument i = instruments.findById(r.getInstrumentId()).orElseThrow();
        RegulatoryObligation o = RegulatoryObligation.createFromRequirement(tenantId, r.getTitle(), r.getCategory(),
                i.getTitle() + " (" + i.getIdentifier() + ") " + r.getReference(), r.getRequirementText(),
                req.responsibleUserId(), req.responsibleUserName().trim(), req.reviewDate(),
                req.recurrence() != null ? req.recurrence() : r.getRecurrence(),
                r.getId(), a.getId(), v.getId(), req.departmentName(), r.getEvidenceExpectation(), userId);
        obligations.save(o);
        audit.record(tid, "OBLIGATION", o.getId(), "GENERATED", "From requirement " + r.getReference() + " (version " + v.getVersionLabel()
                + "), assessment " + a.getId() + ", owner " + req.responsibleUserName().trim(), userId, userName);
        return new GenerateObligationResponse(true, RegulatoryObligationResponse.of(o));
    }
}
