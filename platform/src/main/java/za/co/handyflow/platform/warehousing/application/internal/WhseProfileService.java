package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.domain.model.WhseProfile;
import za.co.handyflow.platform.warehousing.domain.repository.WhseProfileRepository;

import java.math.BigDecimal;

/** Direct structural mirror of CollAgencyProfileService — upsert-only, one profile per tenant. */
@Service
@RequiredArgsConstructor
public class WhseProfileService {

    private final WhseProfileRepository repository;

    @Transactional(readOnly = true)
    public WhseProfile get(TenantId tenantId) {
        return repository.findByTenantId(tenantId.getValue()).orElse(null);
    }

    @Transactional
    public WhseProfile upsert(TenantId tenantId, String warehouseName, String registrationNumber,
                               BigDecimal defaultStorageRatePerUnitPerMonth, BigDecimal defaultReceivingFeePerUnit,
                               BigDecimal defaultPickFeePerUnit, BigDecimal defaultPackFeePerOrder,
                               String contactEmail, String contactPhone, String physicalAddress) {
        WhseProfile profile = repository.findByTenantId(tenantId.getValue()).orElse(null);
        if (profile == null) {
            profile = WhseProfile.create(tenantId.getValue(), warehouseName, registrationNumber,
                    defaultStorageRatePerUnitPerMonth, defaultReceivingFeePerUnit, defaultPickFeePerUnit,
                    defaultPackFeePerOrder, contactEmail, contactPhone, physicalAddress);
        } else {
            profile.update(warehouseName, registrationNumber, defaultStorageRatePerUnitPerMonth,
                    defaultReceivingFeePerUnit, defaultPickFeePerUnit, defaultPackFeePerOrder, contactEmail,
                    contactPhone, physicalAddress);
        }
        return repository.save(profile);
    }
}
