package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceApprovalRule;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceClient;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceApprovalRuleRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceClientRepository;
import za.co.handyflow.platform.complianceservices.dto.ApprovalRuleDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;
import java.util.function.Supplier;

/** Practice-set approval rules for quotes. Changing who must approve is itself a two-person decision, and nothing is deleted. */
@Service
@RequiredArgsConstructor
public class ComplianceApprovalRuleService {

    private final ComplianceApprovalRuleRepository rules;
    private final ComplianceClientRepository clients;

    @Transactional(readOnly = true)
    public List<RuleView> list(TenantId tenantId) {
        Map<UUID, String> names = new HashMap<>();
        List<RuleView> out = new ArrayList<>();
        for (ComplianceApprovalRule r : rules.findAllForTenant(tenantId)) out.add(view(tenantId, r, names));
        return out;
    }

    @Transactional
    public RuleView propose(TenantId tenantId, RuleRequest q, UUID by) {
        if (q.clientId() != null) clients.findByIdForTenant(tenantId, q.clientId()).orElseThrow(() -> new ResourceNotFoundException("ComplianceClient", q.clientId().toString()));
        ComplianceApprovalRule r = guard(() -> ComplianceApprovalRule.propose(tenantId, q.name(), q.minFees(), q.serviceCode(), q.riskRating(), q.clientId(), q.requiredApprover(), q.reason(), by));
        return view(tenantId, rules.save(r), new HashMap<>());
    }

    @Transactional
    public RuleView activate(TenantId tenantId, UUID id, UUID by) {
        ComplianceApprovalRule r = find(tenantId, id);
        guard(() -> { r.activate(by); return null; });
        return view(tenantId, rules.save(r), new HashMap<>());
    }

    @Transactional
    public RuleView retire(TenantId tenantId, UUID id, String note, UUID by) {
        ComplianceApprovalRule r = find(tenantId, id);
        guard(() -> { r.retire(by, note); return null; });
        return view(tenantId, rules.save(r), new HashMap<>());
    }

    private ComplianceApprovalRule find(TenantId tenantId, UUID id) {
        return rules.findOne(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("ComplianceApprovalRule", id.toString()));
    }

    private RuleView view(TenantId tenantId, ComplianceApprovalRule r, Map<UUID, String> names) {
        String client = null;
        if (r.getClientId() != null) client = names.computeIfAbsent(r.getClientId(), id -> clients.findByIdForTenant(tenantId, id).map(ComplianceClient::getName).orElse(null));
        return new RuleView(r.getId(), r.getName(), r.getMinFees(), r.getServiceCode(), r.getRiskRating(), r.getClientId(), client, r.getRequiredApprover(), r.getReason(), r.getStatus(),
                r.getProposedBy(), r.getProposedAt(), r.getActivatedBy(), r.getActivatedAt(), r.getRetiredBy(), r.getRetiredAt(), r.getRetireNote());
    }

    private static <T> T guard(Supplier<T> work) {
        try { return work.get(); }
        catch (IllegalStateException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "APPROVAL_RULE_STATE"); }
        catch (IllegalArgumentException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "APPROVAL_RULE_INVALID"); }
    }
}
