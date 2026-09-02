package za.co.handyflow.platform.legalpractice.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalpractice.domain.model.LpDisbursement;
import za.co.handyflow.platform.legalpractice.domain.model.LpMatter;
import za.co.handyflow.platform.legalpractice.domain.repository.LpDisbursementRepository;
import za.co.handyflow.platform.legalpractice.domain.repository.LpMatterRepository;
import za.co.handyflow.platform.legalpractice.dto.CreateLpDisbursementRequest;
import za.co.handyflow.platform.legalpractice.dto.LpDisbursementResponse;
import za.co.handyflow.platform.legalpractice.dto.UpdateLpDisbursementRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Out-of-pocket matter costs. Same CLOSED/ARCHIVED matter guard as
 * {@link LpTimeEntryService}. {@code paidFromTrust} is captured here as
 * the caller's declared intent at creation time only — this service does
 * NOT itself call {@code LpTrustTransactionService.payDisbursement()}.
 * The two are deliberately kept separate: {@code LpDisbursement} carries
 * no back-reference to a specific {@code LpTrustTransaction} id, so
 * wiring them together automatically here would be guessing at a link the
 * entity layer doesn't actually model. A firm marks a disbursement
 * {@code paidFromTrust=true} to reflect how it was funded, and separately
 * records the real trust movement via the trust endpoints when it
 * actually happens — flagged as a reasonable follow-up (an explicit
 * {@code trustTransactionId} field) rather than silently invented here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LpDisbursementService {

    private final LpDisbursementRepository disbursementRepo;
    private final LpMatterRepository matterRepo;

    @Transactional(readOnly = true)
    public Page<LpDisbursementResponse> listForMatter(TenantId tenantId, UUID matterId, Pageable pageable) {
        return disbursementRepo.findAllForMatter(tenantId, matterId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<LpDisbursementResponse> listUnbilledForMatter(TenantId tenantId, UUID matterId) {
        return disbursementRepo.findUnbilledByMatter(tenantId, matterId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public LpDisbursementResponse createDisbursement(TenantId tenantId, UUID matterId, CreateLpDisbursementRequest req) {
        LpMatter matter = matterRepo.findActiveById(tenantId, matterId)
                .orElseThrow(() -> new ResourceNotFoundException("LpMatter", matterId.toString()));
        if ("CLOSED".equals(matter.getStatus()) || "ARCHIVED".equals(matter.getStatus())) {
            throw new IllegalStateException("Cannot log a disbursement against a matter in status " + matter.getStatus());
        }

        LpDisbursement disbursement = LpDisbursement.create(tenantId, matterId, req.disbursementDate(),
                req.description(), req.amount(), req.paidFromTrust());
        disbursementRepo.save(disbursement);
        return toResponse(disbursement);
    }

    @Transactional
    public LpDisbursementResponse updateDisbursement(TenantId tenantId, UUID disbursementId, UpdateLpDisbursementRequest req) {
        LpDisbursement disbursement = findOwn(tenantId, disbursementId);
        disbursement.update(req.disbursementDate(), req.description(), req.amount());
        disbursementRepo.save(disbursement);
        return toResponse(disbursement);
    }

    @Transactional
    public LpDisbursementResponse writeOff(TenantId tenantId, UUID disbursementId) {
        LpDisbursement disbursement = findOwn(tenantId, disbursementId);
        disbursement.writeOff();
        disbursementRepo.save(disbursement);
        return toResponse(disbursement);
    }

    private LpDisbursement findOwn(TenantId tenantId, UUID disbursementId) {
        return disbursementRepo.findActiveById(tenantId, disbursementId)
                .orElseThrow(() -> new ResourceNotFoundException("LpDisbursement", disbursementId.toString()));
    }

    private LpDisbursementResponse toResponse(LpDisbursement d) {
        return new LpDisbursementResponse(d.getId(), d.getMatterId(), d.getDisbursementDate(), d.getDescription(),
                d.getAmount(), d.isPaidFromTrust(), d.getStatus(), d.getInvoiceId(), d.getCreatedAt(), d.getUpdatedAt());
    }
}
