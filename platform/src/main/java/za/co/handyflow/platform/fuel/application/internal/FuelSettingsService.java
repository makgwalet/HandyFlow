package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelSettings;
import za.co.handyflow.platform.fuel.domain.repository.FuelSettingsRepository;
import za.co.handyflow.platform.fuel.dto.FuelWholesaleDtos.ControlsRequest;
import za.co.handyflow.platform.fuel.dto.FuelWholesaleDtos.ControlsResponse;
import za.co.handyflow.platform.fuel.dto.FuelSettingsDtos.SettingsRequest;
import za.co.handyflow.platform.fuel.dto.FuelSettingsDtos.SettingsResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;

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

    @Transactional
    public za.co.handyflow.platform.fuel.dto.AutomationDtos.AutomationSettings updateAutomation(TenantId tenantId, boolean alertOverduePurchaseOrders, Integer varianceReviewDays) {
        FuelSettings s = settings.findByTenant(tenantId).orElseGet(() -> FuelSettings.forTenant(tenantId));
        s.updateAutomation(alertOverduePurchaseOrders);
        s.updateVarianceReviewDays(varianceReviewDays);
        FuelSettings saved = settings.save(s);
        return new za.co.handyflow.platform.fuel.dto.AutomationDtos.AutomationSettings(saved.isAlertOverduePurchaseOrders(), saved.getVarianceReviewDays());
    }

    @Transactional(readOnly = true)
    public ControlsResponse controls(TenantId tenantId) {
        FuelSettings s = current(tenantId);
        return toControls(s);
    }

    @Transactional
    public ControlsResponse updateControls(TenantId tenantId, ControlsRequest req) {
        FuelSettings s = settings.findByTenant(tenantId).orElseGet(() -> FuelSettings.forTenant(tenantId));
        s.updateControls(req.requireSupplierApproval(), req.adjustmentThresholdLitres());
        s.updateVarianceTolerance(req.varianceTolerancePct());
        s.updateCashControls(req.cashVarianceTolerance(), req.requireSeparateCloser());
        s.updatePriceControls(req.priceChangeApprovalPct(), req.minMarginPct());
        s.updateLicenceBlocking(req.licenceBlockingActivities());
        return toControls(settings.save(s));
    }

    private static ControlsResponse toControls(FuelSettings s) {
        return new ControlsResponse(s.isRequireSupplierApproval(), s.getAdjustmentThresholdLitres(), s.getVarianceTolerancePct(),
                s.getCashVarianceTolerance(), s.isRequireSeparateCloser(), s.getPriceChangeApprovalPct(), s.getMinMarginPct(), List.copyOf(s.blockingActivities()));
    }

    private static SettingsResponse toResponse(FuelSettings s) {
        return new SettingsResponse(s.getDriverCostPerHour(), s.getVehicleCostPerKm(), s.getFixedCostPerDelivery(), s.getOverheadPctOfRevenue(),
                s.getDefaultTripHours(), s.getPoOverTolerancePct(), !s.ratesUnset());
    }
}
