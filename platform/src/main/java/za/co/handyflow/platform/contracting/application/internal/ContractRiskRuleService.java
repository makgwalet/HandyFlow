package za.co.handyflow.platform.contracting.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.contracting.domain.model.ContractRiskRule;
import za.co.handyflow.platform.contracting.domain.repository.ContractRiskRuleRepository;
import za.co.handyflow.platform.contracting.dto.ContractRiskDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Management of risk rules: draft, legal review, publish, new version, withdraw. The tenant's legal reviewer supplies the rules. */
@Service
@RequiredArgsConstructor
public class ContractRiskRuleService {

    private final ContractRiskRuleRepository ruleRepo;

    @Transactional(readOnly = true)
    public List<RuleResponse> list(TenantId tenantId, String status) {
        LocalDate today = LocalDate.now();
        String s = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        return ruleRepo.search(tenantId.getValue(), s).stream().map(r -> toResponse(r, today)).toList();
    }

    @Transactional
    public RuleResponse create(TenantId tenantId, RuleRequest q, UUID by) {
        return toResponse(ruleRepo.save(ContractRiskRule.create(tenantId.getValue(), q.name(), q.description(), q.ruleType(), q.paramText(), q.paramNumber(),
                q.appliesToTypes(), q.severity(), q.blocksSending(), q.message(), q.remediation(), q.legalArea(), q.jurisdiction(), by)), LocalDate.now());
    }

    @Transactional
    public RuleResponse edit(TenantId tenantId, UUID id, RuleRequest q) {
        ContractRiskRule r = rule(tenantId, id);
        r.edit(q.name(), q.description(), q.ruleType(), q.paramText(), q.paramNumber(), q.appliesToTypes(), q.severity(), q.blocksSending(),
                q.message(), q.remediation(), q.legalArea(), q.jurisdiction());
        return toResponse(ruleRepo.save(r), LocalDate.now());
    }

    @Transactional
    public RuleResponse approve(TenantId tenantId, UUID id, ApproveRuleRequest q) {
        ContractRiskRule r = rule(tenantId, id);
        r.approve(q.reviewerName(), q.reviewerProfessionalDetails(), q.reviewDate(), q.nextReviewDate(), q.notes(), LocalDate.now());
        return toResponse(ruleRepo.save(r), LocalDate.now());
    }

    /** Publishes an approved rule; the previously published version of the same rule is superseded first. */
    @Transactional
    public RuleResponse publish(TenantId tenantId, UUID id) {
        ContractRiskRule r = rule(tenantId, id);
        ruleRepo.findFirstByTenantIdAndFamilyIdAndStatus(tenantId.getValue(), r.getFamilyId(), "PUBLISHED").ifPresent(prev -> {
            prev.supersede();
            ruleRepo.saveAndFlush(prev);
        });
        r.publish(LocalDate.now());
        return toResponse(ruleRepo.save(r), LocalDate.now());
    }

    @Transactional
    public RuleResponse newVersion(TenantId tenantId, UUID id, UUID by) {
        ContractRiskRule r = rule(tenantId, id);
        int next = ruleRepo.latestVersionNo(tenantId.getValue(), r.getFamilyId()) + 1;
        return toResponse(ruleRepo.save(r.newVersion(next, by)), LocalDate.now());
    }

    @Transactional
    public RuleResponse withdraw(TenantId tenantId, UUID id, String reason) {
        ContractRiskRule r = rule(tenantId, id);
        r.withdraw(reason);
        return toResponse(ruleRepo.save(r), LocalDate.now());
    }

    static String legalStatus(ContractRiskRule r, LocalDate today) {
        return switch (r.getStatus()) {
            case "DRAFT" -> "NOT_REVIEWED";
            case "SUPERSEDED" -> "SUPERSEDED";
            case "WITHDRAWN" -> "WITHDRAWN";
            default -> r.reviewExpired(today) ? "REVIEW_EXPIRED" : "REVIEWED";
        };
    }

    private RuleResponse toResponse(ContractRiskRule r, LocalDate today) {
        return new RuleResponse(r.getId(), r.getFamilyId(), r.getVersionNo(), r.getName(), r.getDescription(), r.getRuleType(), r.getParamText(), r.getParamNumber(),
                r.getAppliesToTypes(), r.getSeverity(), r.isBlocksSending(), r.getMessage(), r.getRemediation(), r.getLegalArea(), r.getJurisdiction(),
                r.getStatus(), legalStatus(r, today), r.getReviewerName(), r.getReviewerProfessionalDetails(), r.getReviewDate(), r.getNextReviewDate(),
                r.getReviewNotes(), r.getWithdrawnReason(), r.active(today));
    }

    private ContractRiskRule rule(TenantId tenantId, UUID id) {
        return ruleRepo.findByTenantIdAndId(tenantId.getValue(), id).orElseThrow(() -> new ResourceNotFoundException("Risk rule", id.toString()));
    }
}
