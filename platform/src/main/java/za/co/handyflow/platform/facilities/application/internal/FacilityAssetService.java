package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilities.domain.model.FacilityAsset;
import za.co.handyflow.platform.facilities.domain.repository.FacilityAssetRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilitySiteRepository;
import za.co.handyflow.platform.facilities.dto.AssetResponse;
import za.co.handyflow.platform.facilities.dto.CreateAssetRequest;
import za.co.handyflow.platform.facilities.dto.UpdateAssetRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Chunk 3: reads and writes are limited to the caller's {@link AccessScope}
 * (an asset is visible if its site is); status changes and deletions are
 * audited. {@link #requireAccessible} lets controllers guard the evidence
 * endpoints, which don't load the asset themselves.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FacilityAssetService {

    private final FacilityAssetRepository assetRepository;
    private final FacilitySiteRepository siteRepository;
    private final FacilityAccessPolicy accessPolicy;
    private final FacilityAuditService auditService;

    @Transactional(readOnly = true)
    public Page<AssetResponse> getAssets(TenantId tenantId, UUID siteId, Pageable pageable) {
        AccessScope scope = accessPolicy.scope(tenantId);
        if (siteId != null) {
            scope.requireSite(siteId);
            return assetRepository.findBySite(tenantId, siteId, pageable).map(this::toResponse);
        }
        return assetRepository.findAllActiveInScope(tenantId, scope.allSites(), scope.siteIds(), pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AssetResponse getAsset(TenantId tenantId, UUID id) {
        return toResponse(findActive(tenantId, id, accessPolicy.scope(tenantId)));
    }

    /** Throws NOT FOUND unless the asset exists for this tenant and the caller may see its site. */
    @Transactional(readOnly = true)
    public void requireAccessible(TenantId tenantId, UUID id) {
        findActive(tenantId, id, accessPolicy.scope(tenantId));
    }

    @Transactional
    public AssetResponse createAsset(TenantId tenantId, CreateAssetRequest req) {
        accessPolicy.scope(tenantId).requireSite(req.siteId());
        siteRepository.findActiveById(tenantId, req.siteId())
                .orElseThrow(() -> new ResourceNotFoundException("FacilitySite", req.siteId().toString()));

        FacilityAsset asset = FacilityAsset.create(tenantId, req.siteId(), req.assetTag(), req.name(),
                req.assetType(), req.location(), req.manufacturer(), req.model(), req.serialNumber(),
                req.installDate(), req.warrantyExpiryDate(), req.criticality(), req.notes());
        assetRepository.save(asset);
        log.info("Facility asset created id={} tenant={} site={}", asset.getId(), tenantId, req.siteId());
        return toResponse(asset);
    }

    @Transactional
    public AssetResponse updateAsset(TenantId tenantId, UUID id, UpdateAssetRequest req) {
        FacilityAsset asset = findActive(tenantId, id, accessPolicy.scope(tenantId));
        asset.update(req.name(), req.location(), req.manufacturer(), req.model(), req.serialNumber(),
                req.warrantyExpiryDate(), req.criticality(), req.notes());
        assetRepository.save(asset);
        return toResponse(asset);
    }

    @Transactional
    public AssetResponse updateStatus(TenantId tenantId, UUID id, String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status is required. Valid values: OPERATIONAL, DOWN, MAINTENANCE, DECOMMISSIONED");
        }
        FacilityAsset asset = findActive(tenantId, id, accessPolicy.scope(tenantId));
        String previous = asset.getStatus();
        switch (status.toUpperCase()) {
            case "OPERATIONAL" -> asset.markOperational();
            case "DOWN" -> asset.markDown();
            case "MAINTENANCE" -> asset.sendToMaintenance();
            case "DECOMMISSIONED" -> asset.decommission();
            default -> throw new IllegalArgumentException(
                    "Unknown status: " + status + ". Valid values: OPERATIONAL, DOWN, MAINTENANCE, DECOMMISSIONED");
        }
        assetRepository.save(asset);
        auditService.record(tenantId, "ASSET_STATUS_CHANGED", "ASSET", id, previous + " -> " + asset.getStatus());
        log.info("Facility asset status changed id={} status={}", id, status);
        return toResponse(asset);
    }

    @Transactional
    public void deleteAsset(TenantId tenantId, UUID id, UUID deletedByUserId) {
        FacilityAsset asset = findActive(tenantId, id, accessPolicy.scope(tenantId));
        asset.softDelete(deletedByUserId);
        assetRepository.save(asset);
        auditService.record(tenantId, "ASSET_DELETED", "ASSET", id,
                "name=" + asset.getName() + (asset.getAssetTag() != null ? " tag=" + asset.getAssetTag() : ""));
    }

    private FacilityAsset findActive(TenantId tenantId, UUID id, AccessScope scope) {
        FacilityAsset asset = assetRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FacilityAsset", id.toString()));
        if (!scope.canSeeSite(asset.getSiteId())) {
            throw new ResourceNotFoundException("FacilityAsset", id.toString());
        }
        return asset;
    }

    private AssetResponse toResponse(FacilityAsset a) {
        return new AssetResponse(a.getId(), a.getSiteId(), a.getAssetTag(), a.getName(), a.getAssetType(),
                a.getLocation(), a.getManufacturer(), a.getModel(), a.getSerialNumber(), a.getInstallDate(),
                a.getWarrantyExpiryDate(), a.getCriticality(), a.getStatus(), a.getNotes(), a.getCreatedAt());
    }
}