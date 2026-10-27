package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilities.domain.repository.FacilityComplianceCertificateRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityCorrectiveActionRepository;
import za.co.handyflow.platform.facilities.dto.ActionCentreResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;

/** The compliance action centre: certificates and corrective actions in the caller's site scope, ranked by severity. */
@Service
@RequiredArgsConstructor
public class FacilityActionCentreService {

    static final int WARNING_DAYS = 30;

    private final FacilityComplianceCertificateRepository certificateRepository;
    private final FacilityCorrectiveActionRepository actionRepository;
    private final FacilityAccessPolicy accessPolicy;

    @Transactional(readOnly = true)
    public ActionCentreResponse get(TenantId tenantId) {
        AccessScope scope = accessPolicy.scope(tenantId);
        LocalDate today = FacilityTime.today();
        return FacilityActionCentre.build(
                certificateRepository.findLapsedOrExpiringInScope(tenantId, today.plusDays(WARNING_DAYS), scope.allSites(), scope.siteIds()),
                actionRepository.findUnfinishedInScope(tenantId, scope.allSites(), scope.siteIds()),
                today, WARNING_DAYS);
    }
}
