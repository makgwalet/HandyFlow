package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelSettings;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.fuel.dto.EnforcementDtos.Readiness;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.Instant;

/**
 * A look back before a company switches the arrival check or POD policy on: how many recent deliveries it would have refused.
 * Read only. It changes nothing and does not switch anything on.
 */
@Service
@RequiredArgsConstructor
public class FuelEnforcementReadinessService {

    static final int MIN_DAYS = 1, MAX_DAYS = 180, DEFAULT_DAYS = 30;

    private final FuelDeliveryRepository deliveries;
    private final FuelSettingsService settings;

    @Transactional(readOnly = true)
    public Readiness readiness(TenantId tenantId, Integer days) { return readiness(tenantId, days, Instant.now()); }

    Readiness readiness(TenantId tenantId, Integer days, Instant now) {
        int d = days == null ? DEFAULT_DAYS : Math.max(MIN_DAYS, Math.min(MAX_DAYS, days));
        Instant since = now.minus(Duration.ofDays(d));
        FuelSettings s = settings.current(tenantId);
        return new Readiness(d, s.isArrivalCheckEnforced(), s.isPodPolicyEnforced(),
                deliveries.countDeliveredSince(tenantId, since),
                deliveries.countDeliveredWithoutReceiverSince(tenantId, since),
                deliveries.countDeliveredWithoutArrivalCheckSince(tenantId, since),
                deliveries.countDeliveredWithoutSignatureSince(tenantId, since));
    }
}
