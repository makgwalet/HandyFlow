package za.co.handyflow.platform.legalpractice.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalpractice.domain.model.LpRetainerAgreement;
import za.co.handyflow.platform.legalpractice.domain.repository.LpRetainerAgreementRepository;
import za.co.handyflow.platform.legalpractice.dto.CancelRetainerRequest;
import za.co.handyflow.platform.legalpractice.dto.CreateLpRetainerAgreementRequest;
import za.co.handyflow.platform.legalpractice.dto.LpRetainerAgreementResponse;
import za.co.handyflow.platform.legalpractice.dto.UpdateLpRetainerAgreementRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** CRUD + cancel for client-level standing retainers — independent of any single {@code LpMatter}. */
@Slf4j
@Service
@RequiredArgsConstructor
public class LpRetainerAgreementService {

    private final LpRetainerAgreementRepository retainerRepo;

    @Transactional(readOnly = true)
    public List<LpRetainerAgreementResponse> listForClient(TenantId tenantId, UUID clientId) {
        return retainerRepo.findAllForClient(tenantId, clientId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public LpRetainerAgreementResponse getAgreement(TenantId tenantId, UUID id) {
        return toResponse(findOwn(tenantId, id));
    }

    @Transactional
    public LpRetainerAgreementResponse createAgreement(TenantId tenantId, CreateLpRetainerAgreementRequest req) {
        LpRetainerAgreement agreement = LpRetainerAgreement.create(tenantId, req.clientId(), req.monthlyFee(),
                req.startDate(), req.endDate(), req.notes());
        retainerRepo.save(agreement);
        log.info("Created legal practice retainer={} client={} tenant={}", agreement.getId(), req.clientId(), tenantId);
        return toResponse(agreement);
    }

    @Transactional
    public LpRetainerAgreementResponse updateAgreement(TenantId tenantId, UUID id, UpdateLpRetainerAgreementRequest req) {
        LpRetainerAgreement agreement = findOwn(tenantId, id);
        agreement.update(req.monthlyFee(), req.endDate(), req.notes());
        retainerRepo.save(agreement);
        return toResponse(agreement);
    }

    @Transactional
    public LpRetainerAgreementResponse cancelAgreement(TenantId tenantId, UUID id, CancelRetainerRequest req) {
        LpRetainerAgreement agreement = findOwn(tenantId, id);
        agreement.cancel(req.endDate());
        retainerRepo.save(agreement);
        return toResponse(agreement);
    }

    private LpRetainerAgreement findOwn(TenantId tenantId, UUID id) {
        return retainerRepo.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("LpRetainerAgreement", id.toString()));
    }

    private LpRetainerAgreementResponse toResponse(LpRetainerAgreement r) {
        return new LpRetainerAgreementResponse(r.getId(), r.getClientId(), r.getMonthlyFee(),
                r.getStartDate(), r.getEndDate(), r.getStatus(), r.getNotes(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
