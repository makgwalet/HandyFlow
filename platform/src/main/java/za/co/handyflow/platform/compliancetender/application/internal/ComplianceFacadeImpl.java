package za.co.handyflow.platform.compliancetender.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.compliancetender.application.ComplianceFacade;
import za.co.handyflow.platform.compliancetender.domain.repository.ComplianceRegistrationRepository;
import za.co.handyflow.platform.compliancetender.dto.ComplianceRegistrationResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ComplianceFacadeImpl implements ComplianceFacade {

    private final ComplianceRegistrationRepository registrationRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ComplianceRegistrationResponse> findRegistrations(TenantId tenantId) {
        return registrationRepository.findAllForTenant(tenantId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ComplianceRegistrationResponse> findRegistration(TenantId tenantId, UUID id) {
        return registrationRepository.findByIdForTenant(tenantId, id).map(this::toResponse);
    }

    private ComplianceRegistrationResponse toResponse(
            za.co.handyflow.platform.compliancetender.domain.model.ComplianceRegistration r) {
        return new ComplianceRegistrationResponse(r.getId(), r.getAuthority(), r.getRegistrationType(),
                r.getRegistrationNumber(), r.getStatus(), r.getIssuedDate(), r.getExpiryDate(), r.getNotes(),
                r.isExpiringWithin(30), r.getCreatedAt());
    }
}
