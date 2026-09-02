package za.co.handyflow.platform.insurancebrokerage.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.insurancebrokerage.domain.model.InsBrokPolicy;
import za.co.handyflow.platform.insurancebrokerage.domain.repository.InsBrokPolicyRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * CRUD, search/filter, the renewal-chain lookup, and every lifecycle
 * transition for {@code InsBrokPolicy} — see that entity's own Javadoc
 * for the full QUOTE -&gt; BOUND -&gt; ACTIVE / renewal state machine.
 * <p>
 * This is the ONLY caller of {@code InsBrokCommissionInvoiceService
 * .issueForPolicy()} — activate() and renew() are the two paths that
 * bring a policy row to ACTIVE, and both call it right after the status
 * transition commits its own state, keeping "when does commission get
 * generated" in exactly one place per the class's own design note.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsBrokPolicyService {

    private final InsBrokPolicyRepository repository;
    private final InsBrokCommissionInvoiceService commissionInvoiceService;
    private final InsBrokNumberGenerator numberGenerator;

    @Transactional(readOnly = true)
    public InsBrokPolicy get(TenantId tenantId, UUID id) {
        return findActive(tenantId, id);
    }

    @Transactional(readOnly = true)
    public Page<InsBrokPolicy> listForClient(TenantId tenantId, UUID clientId, Pageable pageable) {
        return repository.findByClient(tenantId.getValue(), clientId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<InsBrokPolicy> search(TenantId tenantId, String status, String lineOfBusiness, String search,
                                       Pageable pageable) {
        return repository.search(tenantId.getValue(), status, lineOfBusiness, search, pageable);
    }

    @Transactional(readOnly = true)
    public List<InsBrokPolicy> renewalChain(TenantId tenantId, UUID policyId) {
        return repository.findRenewalChain(tenantId.getValue(), policyId);
    }

    @Transactional
    public InsBrokPolicy createQuote(TenantId tenantId, UUID clientId, UUID insurerId, String quoteReference,
                                      String lineOfBusiness, String assetType, String assetReference,
                                      BigDecimal sumInsured, BigDecimal premiumAmount, String premiumFrequency,
                                      BigDecimal excessAmount, BigDecimal commissionRatePct, LocalDate startDate,
                                      LocalDate expiryDate, String notes) {
        InsBrokPolicy policy = InsBrokPolicy.createQuote(tenantId.getValue(), clientId, insurerId,
                quoteReference != null ? quoteReference : numberGenerator.nextPolicyReference(tenantId),
                lineOfBusiness, assetType, assetReference, sumInsured, premiumAmount, premiumFrequency, excessAmount,
                commissionRatePct, startDate, expiryDate, notes);
        return repository.save(policy);
    }

    @Transactional
    public InsBrokPolicy update(TenantId tenantId, UUID id, UUID insurerId, String lineOfBusiness, String assetType,
                                 String assetReference, BigDecimal sumInsured, BigDecimal premiumAmount,
                                 String premiumFrequency, BigDecimal excessAmount, BigDecimal commissionRatePct,
                                 LocalDate startDate, LocalDate expiryDate, String notes) {
        InsBrokPolicy policy = findActive(tenantId, id);
        policy.update(insurerId, lineOfBusiness, assetType, assetReference, sumInsured, premiumAmount,
                premiumFrequency, excessAmount, commissionRatePct, startDate, expiryDate, notes);
        return repository.save(policy);
    }

    @Transactional
    public InsBrokPolicy bind(TenantId tenantId, UUID id, String policyNumber) {
        InsBrokPolicy policy = findActive(tenantId, id);
        policy.bind(policyNumber);
        return repository.save(policy);
    }

    /** Activates the policy and immediately issues the one commission invoice for this term. */
    @Transactional
    public InsBrokPolicy activate(TenantId tenantId, UUID id) {
        InsBrokPolicy policy = findActive(tenantId, id);
        policy.activate();
        policy = repository.save(policy);
        commissionInvoiceService.issueForPolicy(tenantId, policy);
        return policy;
    }

    @Transactional
    public InsBrokPolicy markLapsed(TenantId tenantId, UUID id) {
        InsBrokPolicy policy = findActive(tenantId, id);
        policy.markLapsed();
        return repository.save(policy);
    }

    @Transactional
    public InsBrokPolicy reinstate(TenantId tenantId, UUID id) {
        InsBrokPolicy policy = findActive(tenantId, id);
        policy.reinstate();
        return repository.save(policy);
    }

    @Transactional
    public InsBrokPolicy cancel(TenantId tenantId, UUID id, String reason) {
        InsBrokPolicy policy = findActive(tenantId, id);
        policy.cancel(reason);
        return repository.save(policy);
    }

    /**
     * Creates the next term directly in ACTIVE (continuation of existing
     * cover — no re-quoting), marks the old row RENEWED, and issues the
     * renewal term's own commission invoice via the same activate()-time
     * hook path (called explicitly here since the new row is created
     * already-ACTIVE rather than going through bind()/activate()).
     */
    @Transactional
    public InsBrokPolicy renew(TenantId tenantId, UUID id, String policyNumber, BigDecimal sumInsured,
                                BigDecimal premiumAmount, LocalDate startDate, LocalDate expiryDate) {
        InsBrokPolicy current = findActive(tenantId, id);
        if (!List.of("ACTIVE", "LAPSED").contains(current.getStatus())) {
            throw new IllegalStateException("Only an ACTIVE or LAPSED policy can be renewed (current status: "
                    + current.getStatus() + ")");
        }

        BigDecimal carriedSumInsured = sumInsured != null ? sumInsured : current.getSumInsured();
        InsBrokPolicy renewal = InsBrokPolicy.createRenewalTerm(current, policyNumber, carriedSumInsured,
                premiumAmount, startDate, expiryDate);
        renewal = repository.save(renewal);

        current.markRenewed();
        repository.save(current);

        commissionInvoiceService.issueForPolicy(tenantId, renewal);

        log.info("[InsuranceBrokerage] Policy {} renewed as {} tenant={} client={}",
                current.getId(), renewal.getId(), tenantId.getValue(), current.getClientId());
        return renewal;
    }

    private InsBrokPolicy findActive(TenantId tenantId, UUID id) {
        return repository.findByTenantAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new ResourceNotFoundException("InsBrokPolicy", id.toString()));
    }
}
