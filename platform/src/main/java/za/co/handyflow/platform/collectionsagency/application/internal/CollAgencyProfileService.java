package za.co.handyflow.platform.collectionsagency.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyProfile;
import za.co.handyflow.platform.collectionsagency.domain.repository.CollAgencyProfileRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class CollAgencyProfileService {

    private final CollAgencyProfileRepository repository;

    @Transactional(readOnly = true)
    public CollAgencyProfile get(TenantId tenantId) {
        return repository.findByTenantId(tenantId.getValue()).orElse(null);
    }

    @Transactional
    public CollAgencyProfile upsert(TenantId tenantId, String agencyName, String firmRegistrationNumber,
                                     LocalDate firmRegistrationExpiryDate, BigDecimal defaultCommissionPct,
                                     String contactEmail, String contactPhone, String physicalAddress) {
        CollAgencyProfile profile = repository.findByTenantId(tenantId.getValue()).orElse(null);
        if (profile == null) {
            profile = CollAgencyProfile.create(tenantId.getValue(), agencyName, firmRegistrationNumber,
                    firmRegistrationExpiryDate, defaultCommissionPct, contactEmail, contactPhone, physicalAddress);
        } else {
            profile.update(agencyName, firmRegistrationNumber, firmRegistrationExpiryDate, defaultCommissionPct,
                    contactEmail, contactPhone, physicalAddress);
        }
        return repository.save(profile);
    }
}
