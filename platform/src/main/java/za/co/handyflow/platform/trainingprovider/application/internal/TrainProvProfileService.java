package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvProfile;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvProfileRepository;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class TrainProvProfileService {

    private final TrainProvProfileRepository profileRepository;

    @Transactional
    public TrainProvProfile upsert(TenantId tenantId, String tradingName, String registrationNumber,
                                    String accreditationBody, String accreditationNumber,
                                    LocalDate accreditationExpiry, String address, String phone, String email) {
        TrainProvProfile profile = profileRepository.findByTenant(tenantId)
                .orElse(null);
        if (profile == null) {
            profile = TrainProvProfile.create(tenantId, tradingName, registrationNumber, accreditationBody,
                    accreditationNumber, accreditationExpiry, address, phone, email);
        } else {
            profile.update(tradingName, registrationNumber, accreditationBody, accreditationNumber,
                    accreditationExpiry, address, phone, email, profile.getLogoUrl());
        }
        return profileRepository.save(profile);
    }

    public TrainProvProfile get(TenantId tenantId) {
        return profileRepository.findByTenant(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvProfile", tenantId.getValue().toString()));
    }

    public boolean exists(TenantId tenantId) {
        return profileRepository.findByTenant(tenantId).isPresent();
    }
}
