package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmServiceAgreement;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmClientRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmServiceAgreementRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.CreateFmServiceAgreementRequest;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmServiceAgreementResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.UpdateFmServiceAgreementRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * CRUD + {@code end()} for the commercial agreement {@link FmBillingService}
 * reads to decide RETAINER vs. time-and-materials billing for a period.
 * No delete — an agreement is a billing/legal record, only ever ended,
 * matching every other commercial-agreement-shaped entity in this codebase.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FmServiceAgreementService {

    private final FmServiceAgreementRepository agreementRepository;
    private final FmClientRepository clientRepository;

    @Transactional(readOnly = true)
    public Page<FmServiceAgreementResponse> getAgreements(TenantId tenantId, UUID clientId, Pageable pageable) {
        return agreementRepository.findAllActiveForClient(tenantId, clientId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public FmServiceAgreementResponse getAgreement(TenantId tenantId, UUID id) {
        return toResponse(findActive(tenantId, id));
    }

    @Transactional
    public FmServiceAgreementResponse createAgreement(TenantId tenantId, CreateFmServiceAgreementRequest req) {
        clientRepository.findActiveById(tenantId, req.clientId())
                .orElseThrow(() -> new ResourceNotFoundException("FmClient", req.clientId().toString()));

        FmServiceAgreement agreement = FmServiceAgreement.create(tenantId, req.clientId(), req.billingType(),
                req.monthlyFee(), req.hourlyRate(), req.startDate(), req.endDate());
        agreementRepository.save(agreement);
        log.info("FM service agreement created id={} client={} tenant={}", agreement.getId(), req.clientId(), tenantId);
        return toResponse(agreement);
    }

    @Transactional
    public FmServiceAgreementResponse updateAgreement(TenantId tenantId, UUID id, UpdateFmServiceAgreementRequest req) {
        FmServiceAgreement agreement = findActive(tenantId, id);
        agreement.update(req.monthlyFee(), req.hourlyRate(), req.endDate());
        agreementRepository.save(agreement);
        return toResponse(agreement);
    }

    @Transactional
    public FmServiceAgreementResponse end(TenantId tenantId, UUID id) {
        FmServiceAgreement agreement = findActive(tenantId, id);
        agreement.end();
        agreementRepository.save(agreement);
        log.info("FM service agreement ended id={} tenant={}", id, tenantId);
        return toResponse(agreement);
    }

    FmServiceAgreement findActive(TenantId tenantId, UUID id) {
        return agreementRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FmServiceAgreement", id.toString()));
    }

    private FmServiceAgreementResponse toResponse(FmServiceAgreement a) {
        return new FmServiceAgreementResponse(a.getId(), a.getClientId(), a.getBillingType(), a.getMonthlyFee(),
                a.getHourlyRate(), a.getStartDate(), a.getEndDate(), a.getStatus(), a.getCreatedAt());
    }
}
