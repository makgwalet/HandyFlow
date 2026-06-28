package za.co.handyflow.platform.crm.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.domain.model.CustomerConsent;
import za.co.handyflow.platform.crm.domain.model.CustomerConsent.ConsentSource;
import za.co.handyflow.platform.crm.domain.model.CustomerConsent.LawfulBasis;
import za.co.handyflow.platform.crm.domain.repository.CustomerConsentRepository;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerConsentService {

    private static final int DEFAULT_RETENTION_YEARS = 7;

    private final CustomerConsentRepository consentRepository;

    @Transactional(readOnly = true)
    public CustomerConsent getActive(TenantId tenantId, UUID customerId) {
        return consentRepository.findActiveByCustomer(tenantId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Consent", "customer=" + customerId));
    }

    @Transactional(readOnly = true)
    public List<CustomerConsent> getHistory(TenantId tenantId, UUID customerId) {
        return consentRepository.findAllByCustomer(tenantId, customerId);
    }

    @Transactional(readOnly = true)
    public List<CustomerConsent> findExpiredForTenant(TenantId tenantId) {
        return consentRepository.findExpiredForTenant(tenantId, Instant.now());
    }

    @Transactional
    public CustomerConsent recordConsent(TenantId tenantId,
                                         UUID customerId,
                                         LawfulBasis lawfulBasis,
                                         String[] purposes,
                                         ConsentSource source,
                                         String evidence,
                                         Integer retentionYears) {
        consentRepository.findActiveByCustomer(tenantId, customerId)
                .ifPresent(existing -> {
                    throw new ConflictException(
                            "Customer already has an active consent record. " +
                                    "Withdraw the existing consent before recording new consent."
                    );
                });

        int years   = retentionYears != null ? retentionYears : DEFAULT_RETENTION_YEARS;
        var consent = CustomerConsent.create(tenantId, customerId, lawfulBasis,
                purposes, source, evidence);
        consent.setRetentionExpiry(Instant.now().plus(years * 365L, ChronoUnit.DAYS));

        var saved = consentRepository.save(consent);
        log.info("[CRM] Consent recorded customer={} basis={} source={} tenant={}",
                customerId, lawfulBasis, source, tenantId);
        return saved;
    }

    @Transactional
    public CustomerConsent withdrawConsent(TenantId tenantId, UUID customerId, String reason) {
        var consent = consentRepository.findActiveByCustomer(tenantId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active consent", "customer=" + customerId));
        consent.withdraw(reason);
        var saved = consentRepository.save(consent);
        log.info("[CRM] Consent withdrawn customer={} reason='{}' tenant={}",
                customerId, reason, tenantId);
        return saved;
    }

    @Transactional
    public void extendRetention(TenantId tenantId, UUID customerId, int years) {
        consentRepository.findActiveByCustomer(tenantId, customerId)
                .ifPresent(consent -> {
                    consent.setRetentionExpiry(
                            Instant.now().plus(years * 365L, ChronoUnit.DAYS));
                    consentRepository.save(consent);
                });
    }

    @Transactional
    public void recordReview(TenantId tenantId, UUID consentId, UUID reviewedBy) {
        var consent = consentRepository.findById(consentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Consent", consentId.toString()));
        consent.recordReview(reviewedBy);
        consentRepository.save(consent);
        log.info("[CRM] Retention review recorded consent={} by={} tenant={}",
                consentId, reviewedBy, tenantId);
    }
}
