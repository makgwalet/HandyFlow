package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelSettings;
import za.co.handyflow.platform.fuel.domain.repository.FuelSettingsRepository;
import za.co.handyflow.platform.fuel.dto.FuelSettingsDtos.SettingsRequest;
import za.co.handyflow.platform.fuel.dto.FuelSettingsDtos.SettingsResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;

/** Per-tenant fuel settings. A tenant that has never saved any gets defaults without a row being written. */
@Service
@RequiredArgsConstructor
public class FuelSettingsService {

    private final FuelSettingsRepository settings;

    /** The stored settings, or unsaved defaults. */
    @Transactional(readOnly = true)
    public FuelSettings current(TenantId tenantId) {
        return settings.findByTenant(tenantId).orElseGet(() -> FuelSettings.forTenant(tenantId));
    }

    @Transactional(readOnly = true)
    public SettingsResponse get(TenantId tenantId) { return toResponse(current(tenantId)); }

    /** Supplier-delivery tolerance as a fraction. */
    @Transactional(readOnly = true)
    public BigDecimal poToleranceFraction(TenantId tenantId) { return current(tenantId).poToleranceFraction(); }

    @Transactional
    public SettingsResponse update(TenantId tenantId, SettingsRequest req) {
        FuelSettings s = settings.findByTenant(tenantId).orElseGet(() -> FuelSettings.forTenant(tenantId));
        s.update(req.driverCostPerHour(), req.vehicleCostPerKm(), req.fixedCostPerDelivery(), req.overheadPctOfRevenue(),
                req.defaultTripHours(), req.poOverTolerancePct());
        return toResponse(settings.save(s));
    }

    private static SettingsResponse toResponse(FuelSettings s) {
        return new SettingsResponse(s.getDriverCostPerHour(), s.getVehicleCostPerKm(), s.getFixedCostPerDelivery(), s.getOverheadPctOfRevenue(),
                s.getDefaultTripHours(), s.getPoOverTolerancePct(), !s.ratesUnset());
    }
}
