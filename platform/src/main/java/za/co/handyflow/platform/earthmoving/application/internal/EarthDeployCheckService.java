package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthAssetRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthInspectionRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthMaintenancePlanRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthSiteRepository;
import za.co.handyflow.platform.contracting.application.ContractSummary;
import za.co.handyflow.platform.contracting.application.ContractingFacade;
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
    private final EarthSiteRepository siteRepository;
    private final ContractingFacade contracting;
    private final EarthSettingsService settings;
    private final EarthmovingAccess access;

    @Transactional(readOnly = true)
    public DeployWarningsResponse warnings(TenantId tenantId, UUID assetId) {
        return warnings(tenantId, assetId, Instant.now());
    }

    @Transactional(readOnly = true)
    public DeployWarningsResponse warnings(TenantId tenantId, UUID assetId, Instant now) {
        return warnings(tenantId, assetId, now, null);
    }

    /** With a {@code hire} (customer or site, dates, contract) the warnings also say whether the hire needs a signed contract. */
    @Transactional(readOnly = true)
    public DeployWarningsResponse warnings(TenantId tenantId, UUID assetId, Instant now, HireContext hire) {
        access.require(EarthmovingRules.READ, "view deployment warnings");
        EarthAsset asset = assets.findActiveById(tenantId, assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", assetId.toString()));
        List<String> out = new java.util.ArrayList<>(pending(tenantId, asset, now));
        if (hire != null) out.addAll(contractWarnings(tenantId, asset, hire));
        return new DeployWarningsResponse(out);
    }

    /** What the person is deploying for. {@code customerId} may be empty when only a saved site is chosen; the site's customer is used. */
    public record HireContext(UUID customerId, UUID siteId, LocalDate from, LocalDate to, UUID contractId) {}

    /** The contract warning for a hire, no permission check. Nothing is said for a deployment that is not for a customer. */
    @Transactional(readOnly = true)
    public List<String> contractWarnings(TenantId tenantId, EarthAsset asset, HireContext hire) {
        UUID customer = hire.customerId();
        if (customer == null && hire.siteId() != null)
            customer = siteRepository.findByTenantAndId(tenantId, hire.siteId()).map(EarthSite::getCustomerId).orElse(null);
        if (customer == null) return List.of();
        java.math.BigDecimal value = ContractRules.expectedValue(asset.getDailyRate(), hire.from(), hire.to());
        ContractSummary contract = hire.contractId() == null ? null : contracting.findById(tenantId, hire.contractId()).orElse(null);
        String w = ContractRules.warning(settings.contractThreshold(tenantId), value,
                contract == null ? null : contract.contractNumber(), contract == null ? null : contract.status());
        return w == null ? List.of() : List.of(w);
    }

    /** A contract picked for a deployment must exist in this company. */
    @Transactional(readOnly = true)
    public void requireContract(TenantId tenantId, UUID contractId) {
        if (contracting.findById(tenantId, contractId).isEmpty()) throw new IllegalArgumentException("That contract was not found.");
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
