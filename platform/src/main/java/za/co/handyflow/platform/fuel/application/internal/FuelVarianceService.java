package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelDeliveryVariance;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryVarianceRepository;
import za.co.handyflow.platform.fuel.dto.FuelDistributionDtos.VarianceList;
import za.co.handyflow.platform.fuel.dto.FuelDistributionDtos.VarianceView;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Loaded-versus-delivered variance review (gap C02). A variance is raised for a delivered, non-reversed load whose recorded
 * loading differs from the litres delivered by more than the company's tolerance. With no tolerance set nothing is raised.
 * Detection runs whenever the list is opened (looking back {@link #LOOKBACK_DAYS} days) and is idempotent per delivery.
 * A decision is a record only: it never moves stock or writes anything off by itself.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelVarianceService {

    static final int LOOKBACK_DAYS = 90;

    private final FuelDeliveryVarianceRepository variances;
    private final FuelDeliveryRepository deliveries;
    private final FuelSettingsService settings;

    /** Detects new variances, then lists all of them, newest first. */
    @Transactional
    public VarianceList list(TenantId tenantId) {
        BigDecimal tolerance = settings.current(tenantId).getVarianceTolerancePct();
        if (tolerance != null) detect(tenantId, tolerance);
        Map<UUID, FuelDelivery> byId = new HashMap<>();
        List<VarianceView> out = variances.findAllForTenant(tenantId).stream().map(v -> view(tenantId, v, byId)).toList();
        return new VarianceList(tolerance != null, out);
    }

    /** How many new variances were raised just now. Zero when no tolerance is set. Used by the daily sweep. */
    @Transactional
    public int raiseNew(TenantId tenantId) {
        BigDecimal tolerance = settings.current(tenantId).getVarianceTolerancePct();
        return tolerance == null ? 0 : detect(tenantId, tolerance);
    }

    int detect(TenantId tenantId, BigDecimal tolerance) {
        Instant since = Instant.now().minus(LOOKBACK_DAYS, ChronoUnit.DAYS);
        int created = 0;
        for (FuelDelivery d : deliveries.findUncheckedForVariance(tenantId, since)) {
            FuelDeliveryVariance v = FuelDeliveryVariance.ifOutside(tenantId, d.getId(), d.getLoadedLitres(), d.getLitresDelivered(), tolerance);
            if (v != null) { variances.save(v); created++; }
        }
        if (created > 0) log.info("Fuel variances raised tenant={} count={} tolerance={}%", tenantId, created, tolerance);
        return created;
    }

    @Transactional
    public VarianceView investigate(TenantId tenantId, UUID id, String note, UUID by) {
        FuelDeliveryVariance v = lock(tenantId, id);
        v.investigate(by, note);
        return view(tenantId, variances.save(v), new HashMap<>());
    }

    @Transactional
    public VarianceView close(TenantId tenantId, UUID id, String decision, String note, UUID by) {
        FuelDeliveryVariance v = lock(tenantId, id);
        v.close(by, decision, note);
        log.info("Fuel variance closed tenant={} variance={} decision={} by={}", tenantId, id, decision, by);
        return view(tenantId, variances.save(v), new HashMap<>());
    }

    private FuelDeliveryVariance lock(TenantId tenantId, UUID id) {
        return variances.findByIdForUpdate(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Variance", id.toString()));
    }

    private VarianceView view(TenantId tenantId, FuelDeliveryVariance v, Map<UUID, FuelDelivery> cache) {
        FuelDelivery d = cache.computeIfAbsent(v.getDeliveryId(), id -> deliveries.findActiveById(tenantId, id).orElse(null));
        return new VarianceView(v.getId(), v.getDeliveryId(), d == null ? null : d.getReceiptNumber(), d == null ? null : d.getDriverName(),
                d == null ? null : d.getVehicleReg(), v.getLoadedLitres(), v.getDeliveredLitres(), v.getVarianceLitres(), v.getVariancePct(),
                v.getTolerancePct(), v.getStatus(), v.getDecision(), v.getNote(), v.getReviewedBy(), v.getReviewedAt(), v.getDetectedAt());
    }
}
