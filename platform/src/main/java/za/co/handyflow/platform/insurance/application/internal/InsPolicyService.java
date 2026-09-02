package za.co.handyflow.platform.insurance.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.insurance.domain.model.InsPolicy;
import za.co.handyflow.platform.insurance.domain.repository.InsPolicyRepository;
import za.co.handyflow.platform.insurance.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsPolicyService {

    private final InsPolicyRepository policyRepository;

    @Transactional
    public InsPolicyResponse create(TenantId tenantId, CreateInsPolicyRequest req) {
        InsPolicy policy = InsPolicy.create(tenantId, req.policyNumber(), req.insurerName(), req.lineOfBusiness(),
                req.assetType(), req.assetReference(), req.sumInsured(), req.premiumAmount(), req.premiumFrequency(),
                req.excessAmount(), req.brokerOrInsurerContact(), req.startDate(), req.expiryDate(), req.notes(), null);
        policyRepository.save(policy);
        return toResponse(policy);
    }

    @Transactional(readOnly = true)
    public InsPolicyResponse get(TenantId tenantId, UUID id) {
        return toResponse(findOwn(tenantId, id));
    }

    @Transactional(readOnly = true)
    public Page<InsPolicyResponse> search(TenantId tenantId, String status, String lineOfBusiness, String search, Pageable pageable) {
        return policyRepository.search(tenantId, status, lineOfBusiness, search, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<InsPolicyResponse> renewalChain(TenantId tenantId, UUID policyId) {
        return policyRepository.findRenewalChain(tenantId, policyId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public InsPolicyResponse update(TenantId tenantId, UUID id, UpdateInsPolicyRequest req) {
        InsPolicy policy = findOwn(tenantId, id);
        policy.update(req.insurerName(), req.assetType(), req.assetReference(), req.sumInsured(), req.premiumAmount(),
                req.premiumFrequency(), req.excessAmount(), req.brokerOrInsurerContact(), req.startDate(),
                req.expiryDate(), req.notes());
        policyRepository.save(policy);
        return toResponse(policy);
    }

    @Transactional
    public InsPolicyResponse cancel(TenantId tenantId, UUID id, CancelInsPolicyRequest req) {
        InsPolicy policy = findOwn(tenantId, id);
        policy.cancel(req.cancelledDate(), req.reason());
        policyRepository.save(policy);
        return toResponse(policy);
    }

    @Transactional
    public InsPolicyResponse markLapsed(TenantId tenantId, UUID id) {
        InsPolicy policy = findOwn(tenantId, id);
        policy.markLapsed();
        policyRepository.save(policy);
        return toResponse(policy);
    }

    @Transactional
    public InsPolicyResponse reinstate(TenantId tenantId, UUID id) {
        InsPolicy policy = findOwn(tenantId, id);
        policy.reinstate();
        policyRepository.save(policy);
        return toResponse(policy);
    }

    /**
     * Creates a new policy row for the next term and marks the current one
     * RENEWED. Insurer, line of business, asset type/reference and excess
     * carry forward unchanged — see {@code RenewInsPolicyRequest}'s own
     * Javadoc for why.
     */
    @Transactional
    public InsPolicyResponse renew(TenantId tenantId, UUID id, RenewInsPolicyRequest req) {
        InsPolicy current = findOwn(tenantId, id);
        if (!current.isRenewable()) {
            throw new IllegalStateException("Policy " + current.getPolicyNumber() + " is " + current.getStatus() + " and cannot be renewed");
        }
        InsPolicy renewed = InsPolicy.create(tenantId, req.policyNumber(), current.getInsurerName(),
                current.getLineOfBusiness(), current.getAssetType(), current.getAssetReference(),
                req.sumInsured() != null ? req.sumInsured() : current.getSumInsured(), req.premiumAmount(),
                current.getPremiumFrequency(), current.getExcessAmount(), current.getBrokerOrInsurerContact(),
                req.startDate(), req.expiryDate(), current.getNotes(), current.getId());
        policyRepository.save(renewed);
        current.markRenewed();
        policyRepository.save(current);
        log.info("[Insurance] Policy {} renewed as {} for tenant={}", current.getPolicyNumber(), renewed.getPolicyNumber(), tenantId.getValue());
        return toResponse(renewed);
    }

    private InsPolicy findOwn(TenantId tenantId, UUID id) {
        return policyRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("InsPolicy", id.toString()));
    }

    private InsPolicyResponse toResponse(InsPolicy p) {
        return new InsPolicyResponse(p.getId(), p.getPolicyNumber(), p.getInsurerName(), p.getLineOfBusiness(),
                p.getAssetType(), p.getAssetReference(), p.getSumInsured(), p.getPremiumAmount(), p.getPremiumFrequency(),
                p.getExcessAmount(), p.getBrokerOrInsurerContact(), p.getStartDate(), p.getExpiryDate(), p.getStatus(),
                p.getRenewalOfPolicyId(), p.getCancelledDate(), p.getCancelReason(), p.getNotes(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
