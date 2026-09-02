package za.co.handyflow.platform.bookkeeping.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.bookkeeping.domain.model.BkServiceAgreement;
import za.co.handyflow.platform.bookkeeping.domain.repository.BkClientRepository;
import za.co.handyflow.platform.bookkeeping.domain.repository.BkServiceAgreementRepository;
import za.co.handyflow.platform.bookkeeping.dto.BkServiceAgreementResponse;
import za.co.handyflow.platform.bookkeeping.dto.CreateBkServiceAgreementRequest;
import za.co.handyflow.platform.bookkeeping.dto.UpdateBkServiceAgreementRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * CRUD + {@code end()} for the commercial agreement {@link BkBillingService}
 * reads to decide RETAINER vs. time-and-materials billing for a period.
 * No delete — an agreement is a billing/legal record, only ever ended.
 * Direct mirror of {@code FmServiceAgreementService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BkServiceAgreementService {

    private final BkServiceAgreementRepository agreementRepository;
    private final BkClientRepository clientRepository;

    @Transactional(readOnly = true)
    public Page<BkServiceAgreementResponse> getAgreements(TenantId tenantId, UUID clientId, Pageable pageable) {
        return agreementRepository.findAllForClient(tenantId, clientId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public BkServiceAgreementResponse getAgreement(TenantId tenantId, UUID id) {
        return toResponse(findActive(tenantId, id));
    }

    @Transactional
    public BkServiceAgreementResponse createAgreement(TenantId tenantId, CreateBkServiceAgreementRequest req) {
        clientRepository.findActiveById(tenantId, req.clientId())
                .orElseThrow(() -> new ResourceNotFoundException("BkClient", req.clientId().toString()));

        BkServiceAgreement agreement = BkServiceAgreement.create(tenantId, req.clientId(), req.billingType(),
                req.monthlyFee(), req.hourlyRate(), req.startDate(), req.endDate());
        agreementRepository.save(agreement);
        log.info("Bookkeeping service agreement created id={} client={} tenant={}", agreement.getId(), req.clientId(), tenantId);
        return toResponse(agreement);
    }

    @Transactional
    public BkServiceAgreementResponse updateAgreement(TenantId tenantId, UUID id, UpdateBkServiceAgreementRequest req) {
        BkServiceAgreement agreement = findActive(tenantId, id);
        agreement.update(req.monthlyFee(), req.hourlyRate(), req.endDate());
        agreementRepository.save(agreement);
        return toResponse(agreement);
    }

    @Transactional
    public BkServiceAgreementResponse end(TenantId tenantId, UUID id) {
        BkServiceAgreement agreement = findActive(tenantId, id);
        agreement.end();
        agreementRepository.save(agreement);
        log.info("Bookkeeping service agreement ended id={} tenant={}", id, tenantId);
        return toResponse(agreement);
    }

    BkServiceAgreement findActive(TenantId tenantId, UUID id) {
        return agreementRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("BkServiceAgreement", id.toString()));
    }

    private BkServiceAgreementResponse toResponse(BkServiceAgreement a) {
        return new BkServiceAgreementResponse(a.getId(), a.getClientId(), a.getBillingType(), a.getMonthlyFee(),
                a.getHourlyRate(), a.getStartDate(), a.getEndDate(), a.getStatus(), a.getCreatedAt());
    }
}
