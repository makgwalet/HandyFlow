package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmProfile;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmProfileRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmProfileResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.UpsertFmProfileRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

/** One profile row per tenant — created on first save, updated thereafter. */
@Service
@RequiredArgsConstructor
public class FmProfileService {

    private final FmProfileRepository profileRepository;

    @Transactional(readOnly = true)
    public FmProfileResponse getProfile(TenantId tenantId) {
        return profileRepository.findByTenant(tenantId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("FmProfile", tenantId.getValue().toString()));
    }

    @Transactional
    public FmProfileResponse upsertProfile(TenantId tenantId, UpsertFmProfileRequest req) {
        FmProfile profile = profileRepository.findByTenant(tenantId).orElse(null);
        if (profile == null) {
            profile = FmProfile.create(tenantId, req.companyName(), req.registrationNumber(),
                    req.contactEmail(), req.contactPhone(), req.notes());
        } else {
            profile.update(req.companyName(), req.registrationNumber(), req.contactEmail(),
                    req.contactPhone(), req.notes());
        }
        profileRepository.save(profile);
        return toResponse(profile);
    }

    private FmProfileResponse toResponse(FmProfile p) {
        return new FmProfileResponse(p.getId(), p.getCompanyName(), p.getRegistrationNumber(),
                p.getContactEmail(), p.getContactPhone(), p.getNotes(), p.getCreatedAt());
    }
}
