package za.co.handyflow.platform.bookkeeping.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.bookkeeping.domain.model.BkProfile;
import za.co.handyflow.platform.bookkeeping.domain.repository.BkProfileRepository;
import za.co.handyflow.platform.bookkeeping.dto.BkProfileResponse;
import za.co.handyflow.platform.bookkeeping.dto.UpsertBkProfileRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

/** One profile row per tenant — created on first save, updated thereafter. */
@Service
@RequiredArgsConstructor
public class BkProfileService {

    private final BkProfileRepository profileRepository;

    @Transactional(readOnly = true)
    public BkProfileResponse getProfile(TenantId tenantId) {
        return profileRepository.findByTenant(tenantId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("BkProfile", tenantId.getValue().toString()));
    }

    @Transactional
    public BkProfileResponse upsertProfile(TenantId tenantId, UpsertBkProfileRequest req) {
        BkProfile profile = profileRepository.findByTenant(tenantId).orElse(null);
        if (profile == null) {
            profile = BkProfile.create(tenantId, req.practiceName(), req.registrationNumber(),
                    req.contactEmail(), req.contactPhone(), req.notes());
        } else {
            profile.update(req.practiceName(), req.registrationNumber(), req.contactEmail(),
                    req.contactPhone(), req.notes());
        }
        profileRepository.save(profile);
        return toResponse(profile);
    }

    private BkProfileResponse toResponse(BkProfile p) {
        return new BkProfileResponse(p.getId(), p.getPracticeName(), p.getRegistrationNumber(),
                p.getContactEmail(), p.getContactPhone(), p.getNotes(), p.getCreatedAt());
    }
}
