package za.co.handyflow.platform.legalpractice.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalpractice.domain.model.LpProfile;
import za.co.handyflow.platform.legalpractice.domain.repository.LpProfileRepository;
import za.co.handyflow.platform.legalpractice.dto.LpProfileResponse;
import za.co.handyflow.platform.legalpractice.dto.UpsertLpProfileRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

/** One {@link LpProfile} row per tenant — create-on-first-save, update thereafter. */
@Slf4j
@Service
@RequiredArgsConstructor
public class LpProfileService {

    private final LpProfileRepository profileRepo;

    @Transactional(readOnly = true)
    public LpProfileResponse getProfile(TenantId tenantId) {
        LpProfile profile = profileRepo.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("LpProfile", tenantId.toString()));
        return toResponse(profile);
    }

    @Transactional
    public LpProfileResponse upsertProfile(TenantId tenantId, UpsertLpProfileRequest req) {
        LpProfile profile = profileRepo.findByTenantId(tenantId).orElse(null);
        if (profile == null) {
            profile = LpProfile.create(tenantId, req.firmName(), req.practiceNumber(), req.vatNumber(),
                    req.contactEmail(), req.contactPhone(), req.trustBankName(), req.trustAccountNumber(),
                    req.businessBankName(), req.businessAccountNumber());
        } else {
            profile.update(req.firmName(), req.practiceNumber(), req.vatNumber(),
                    req.contactEmail(), req.contactPhone(), req.trustBankName(), req.trustAccountNumber(),
                    req.businessBankName(), req.businessAccountNumber());
        }
        profileRepo.save(profile);
        log.info("Saved legal practice profile firm={} tenant={}", profile.getFirmName(), tenantId);
        return toResponse(profile);
    }

    private LpProfileResponse toResponse(LpProfile p) {
        return new LpProfileResponse(p.getId(), p.getFirmName(), p.getPracticeNumber(), p.getVatNumber(),
                p.getContactEmail(), p.getContactPhone(), p.getTrustBankName(), p.getTrustAccountNumber(),
                p.getBusinessBankName(), p.getBusinessAccountNumber(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
