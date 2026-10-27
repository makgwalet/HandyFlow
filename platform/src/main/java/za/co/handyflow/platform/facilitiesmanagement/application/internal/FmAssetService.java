package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmAsset;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmAssetRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmSiteRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.CreateFmAssetRequest;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmAssetResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.UpdateFmAssetRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Chunk 5: an asset is visible when its site is within the caller's {@link FmAccessScope}; status changes
 * and deletions are audited. {@link #requireAccessible} lets controllers guard the evidence endpoints,
 * which don't load the asset themselves.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FmAssetService {

    private final FmAssetRepository assetRepository;
    private final FmSiteRepository siteRepository;
    private final FmAccessPolicy accessPolicy;
    private final FmAuditService auditService;

    @Transactional(readOnly = true)
    public Page<FmAssetResponse> getAssets(TenantId tenantId, UUID siteId, Pageable pageable) {
        FmAccessScope scope = accessPolicy.scope(tenantId);
        if (siteId != null) {
            scope.requireSite(siteId);
            return assetRepository.findBySite(tenantId, siteId, pageable).map(this::toResponse);
        }
        return assetRepository.findAllActiveInScope(tenantId, scope.unrestricted(), scope.siteIds(), pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public FmAssetResponse getAsset(TenantId tenantId, UUID id) {
        return toResponse(findActive(tenantId, id, accessPolicy.scope(tenantId)));
    }

    /** Throws NOT FOUND unless the asset exists for this tenant and the caller may see its site. */
    @Transactional(readOnly = true)
    public void requireAccessible(TenantId tenantId, UUID id) {
        findActive(tenantId, id, accessPolicy.scope(tenantId));
    }

    @Transactional
    public FmAssetResponse createAsset(TenantId tenantId, CreateFmAssetRequest req) {
        accessPolicy.scope(tenantId).requireSite(req.siteId());
        siteRepository.findActiveById(tenantId, req.siteId())
                .orElseThrow(() -> new ResourceNotFoundException("FmSite", req.siteId().toString()));

        FmAsset asset = FmAsset.create(tenantId, req.siteId(), req.assetTag(), req.name(), req.assetType(),
                req.location(), req.manufacturer(), req.model(), req.serialNumber(), req.installDate(),
                req.warrantyExpiryDate(), req.criticality(), req.notes());
        assetRepository.save(asset);
        log.info("FM asset created id={} tenant={} site={}", asset.getId(), tenantId, req.siteId());
        return toResponse(asset);
    }

    @Transactional
    public FmAssetResponse updateAsset(TenantId tenantId, UUID id, UpdateFmAssetRequest req) {
        FmAsset asset = findActive(tenantId, id, accessPolicy.scope(tenantId));
        asset.update(req.name(), req.location(), req.manufacturer(), req.model(), req.serialNumber(),
                req.warrantyExpiryDate(), req.criticality(), req.notes());
        assetRepository.save(asset);
        return toResponse(asset);
    }

    @Transactional
    public FmAssetResponse updateStatus(TenantId tenantId, UUID id, String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status is required. Valid values: OPERATIONAL, DOWN, MAINTENANCE, DECOMMISSIONED");
        }
        FmAsset asset = findActive(tenantId, id, accessPolicy.scope(tenantId));
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
        log.info("FM asset status changed id={} status={}", id, status);
        return toResponse(asset);
    }

    @Transactional
    public void deleteAsset(TenantId tenantId, UUID id) {
        FmAsset asset = findActive(tenantId, id, accessPolicy.scope(tenantId));
        asset.softDelete();
        assetRepository.save(asset);
        auditService.record(tenantId, "ASSET_DELETED", "ASSET", id,
                "name=" + asset.getName() + (asset.getAssetTag() != null ? " tag=" + asset.getAssetTag() : "") + " site=" + asset.getSiteId());
    }

    private FmAsset findActive(TenantId tenantId, UUID id, FmAccessScope scope) {
        FmAsset asset = assetRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FmAsset", id.toString()));
        if (!scope.canSeeSite(asset.getSiteId())) {
            throw new ResourceNotFoundException("FmAsset", id.toString());
        }
        return asset;
    }

    private FmAssetResponse toResponse(FmAsset a) {
        return new FmAssetResponse(a.getId(), a.getSiteId(), a.getAssetTag(), a.getName(), a.getAssetType(),
                a.getLocation(), a.getManufacturer(), a.getModel(), a.getSerialNumber(), a.getInstallDate(),
                a.getWarrantyExpiryDate(), a.getCriticality(), a.getStatus(), a.getNotes(), a.getCreatedAt());
    }
}