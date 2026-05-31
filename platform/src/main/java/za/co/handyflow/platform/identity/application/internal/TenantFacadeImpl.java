package za.co.handyflow.platform.identity.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.identity.TenantDetails;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.identity.domain.repository.TenantRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;

@Service
@RequiredArgsConstructor
class TenantFacadeImpl implements TenantFacade {

    private final TenantRepository tenantRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantDetails> findTenantDetails(TenantId tenantId) {
        return tenantRepository.findById(tenantId.getValue())
                .map(t -> new TenantDetails(
                        t.getId(),
                        t.getName(),
                        t.getSlug(),
                        t.getVatNumber(),
                        t.getPhone(),
                        t.getEmail(),
                        t.getAddress(),
                        t.getLogoUrl(),
                        t.getBankName(),
                        t.getBankAccount(),
                        t.getBankBranch(),
                        t.getPaymentTerms()
                ));
    }
}
