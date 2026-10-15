package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthAssetRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthInspectionRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthMaintenancePlanRepository;
import za.co.handyflow.platform.earthmoving.dto.DeployWarningsResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** The "before you deploy" check: overdue service and missing inspections, as warnings. */
@Service
@RequiredArgsConstructor
public class EarthDeployCheckService {
    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private final EarthAssetRepository assets;
    private final EarthMaintenancePlanRepository plans;
    private final EarthInspectionRepository inspections;
    private final EarthmovingAccess access;

    @Transactional(readOnly = true)
    public DeployWarningsResponse warnings(TenantId tenantId, UUID assetId) {
        return warnings(tenantId, assetId, Instant.now());
    }

    @Transactional(readOnly = true)
    public DeployWarningsResponse warnings(TenantId tenantId, UUID assetId, Instant now) {
        access.require(EarthmovingRules.READ, "view deployment warnings");
        EarthAsset asset = assets.findActiveById(tenantId, assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", assetId.toString()));
        return new DeployWarningsResponse(pending(tenantId, asset, now));
    }

    /** The warnings for a machine, with no permission check: the deploy itself has already been authorised. */
    @Transactional(readOnly = true)
    public List<String> pending(TenantId tenantId, EarthAsset asset, Instant now) {
        UUID assetId = asset.getId();
        LocalDate today = now.atZone(ZONE).toLocalDate();
        List<String> overdue = plans.findForAsset(tenantId, assetId).stream()
                .filter(EarthMaintenancePlan::isActive)
                .filter(p -> p.status(asset.getCurrentHours(), today).state() == WorkOrderRules.PlanState.OVERDUE)
                .map(EarthMaintenancePlan::getName).toList();
        EarthInspection last = inspections.findRecent(tenantId, assetId, org.springframework.data.domain.PageRequest.of(0, 1)).stream().findFirst().orElse(null);
        return DeployWarnings.compute(overdue, asset.isDueForService(),
                last == null ? null : last.getPerformedAt(), last == null ? null : last.getResult(), now);
    }
}
