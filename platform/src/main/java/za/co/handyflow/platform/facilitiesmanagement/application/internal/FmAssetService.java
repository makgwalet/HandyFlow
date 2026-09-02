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

@Slf4j
@Service
@RequiredArgsConstructor
public class FmAssetService {

    private final FmAssetRepository assetRepository;
    private final FmSiteRepository siteRepository;

    @Transactional(readOnly = true)
    public Page<FmAssetResponse> getAssets(TenantId tenantId, UUID siteId, Pageable pageable) {
        if (siteId != null) return assetRepository.findBySite(tenantId, siteId, pageable).map(this::toResponse);
        return assetRepository.findAllActive(tenantId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public FmAssetResponse getAsset(TenantId tenantId, UUID id) {
        return toResponse(findActive(tenantId, id));
    }

    @Transactional
    public FmAssetResponse createAsset(TenantId tenantId, CreateFmAssetRequest req) {
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
        FmAsset asset = findActive(tenantId, id);
        asset.update(req.name(), req.location(), req.manufacturer(), req.model(), req.serialNumber(),
                req.warrantyExpiryDate(), req.criticality(), req.notes());
        assetRepository.save(asset);
        return toResponse(asset);
    }

    @Transactional
    public FmAssetResponse updateStatus(TenantId tenantId, UUID id, String status) {
        FmAsset asset = findActive(tenantId, id);
        switch (status.toUpperCase()) {
            case "OPERATIONAL" -> asset.markOperational();
            case "DOWN" -> asset.markDown();
            case "MAINTENANCE" -> asset.sendToMaintenance();
            case "DECOMMISSIONED" -> asset.decommission();
            default -> throw new IllegalArgumentException(
                    "Unknown status: " + status + ". Valid values: OPERATIONAL, DOWN, MAINTENANCE, DECOMMISSIONED");
        }
        assetRepository.save(asset);
        log.info("FM asset status changed id={} status={}", id, status);
        return toResponse(asset);
    }

    @Transactional
    public void deleteAsset(TenantId tenantId, UUID id) {
        FmAsset asset = findActive(tenantId, id);
        asset.softDelete();
        assetRepository.save(asset);
    }

    FmAsset findActive(TenantId tenantId, UUID id) {
        return assetRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FmAsset", id.toString()));
    }

    private FmAssetResponse toResponse(FmAsset a) {
        return new FmAssetResponse(a.getId(), a.getSiteId(), a.getAssetTag(), a.getName(), a.getAssetType(),
                a.getLocation(), a.getManufacturer(), a.getModel(), a.getSerialNumber(), a.getInstallDate(),
                a.getWarrantyExpiryDate(), a.getCriticality(), a.getStatus(), a.getNotes(), a.getCreatedAt());
    }
}
