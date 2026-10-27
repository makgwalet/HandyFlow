package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmAsset;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmPpmSchedule;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmAssetRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmPpmScheduleRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmWorkOrderRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmAssetLookupResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmAssetResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Scan-and-go: resolves a QR payload, asset id or tag and returns the asset with what is happening to it. */
@Service
@RequiredArgsConstructor
public class FmAssetLookupService {

    static final int MAX_OPEN_WORK_ORDERS = 10;

    private final FmAssetRepository assetRepository;
    private final FmWorkOrderRepository workOrderRepository;
    private final FmPpmScheduleRepository ppmScheduleRepository;
    private final FmAccessPolicy accessPolicy;

    @Transactional(readOnly = true)
    public FmAssetLookupResponse lookup(TenantId tenantId, String code) {
        FmAssetCode.Parsed parsed = FmAssetCode.parse(code);
        FmAccessScope scope = accessPolicy.scope(tenantId);
        FmAsset asset;
        if (parsed.id() != null) {
            asset = assetRepository.findActiveById(tenantId, parsed.id()).orElse(null);
        } else {
            List<FmAsset> byTag = assetRepository.findActiveByTag(tenantId, parsed.tag());
            // Tags are not unique in FM, so a tag matching several assets is refused rather than guessed. Only assets
            // the caller may see count, so one client's duplicate tag never leaks into another's scan.
            List<FmAsset> visible = byTag.stream().filter(a -> scope.canSeeSite(a.getSiteId())).toList();
            if (visible.size() > 1)
                throw new IllegalArgumentException("More than one asset has the tag '" + parsed.tag() + "'; scan its QR label instead");
            asset = visible.isEmpty() ? null : visible.get(0);
        }
        if (asset == null || !scope.canSeeSite(asset.getSiteId())) throw new ResourceNotFoundException("FmAsset", code.trim());
        return build(tenantId, asset, scope);
    }

    @Transactional(readOnly = true)
    public String qrPayload(TenantId tenantId, UUID assetId) {
        FmAccessScope scope = accessPolicy.scope(tenantId);
        assetRepository.findActiveById(tenantId, assetId)
                .filter(a -> scope.canSeeSite(a.getSiteId()))
                .orElseThrow(() -> new ResourceNotFoundException("FmAsset", assetId.toString()));
        return FmAssetCode.payloadFor(assetId);
    }

    private FmAssetLookupResponse build(TenantId tenantId, FmAsset a, FmAccessScope scope) {
        List<FmAssetLookupResponse.OpenWorkOrder> open = workOrderRepository.findOpenForAsset(tenantId, a.getId()).stream()
                .filter(scope::canSeeWorkOrder)
                .limit(MAX_OPEN_WORK_ORDERS)
                .map(w -> new FmAssetLookupResponse.OpenWorkOrder(w.getId(), w.getWorkOrderNumber(), w.getStatus(), w.getPriority(),
                        w.getDescription(), w.getScheduledDate()))
                .toList();

        LocalDate nextPpm = ppmScheduleRepository.findByAsset(tenantId, a.getId()).stream()
                .filter(FmPpmSchedule::isActive)
                .map(FmPpmSchedule::getNextDueDate)
                .min(Comparator.naturalOrder()).orElse(null);

        FmAssetResponse asset = new FmAssetResponse(a.getId(), a.getSiteId(), a.getAssetTag(), a.getName(), a.getAssetType(),
                a.getLocation(), a.getManufacturer(), a.getModel(), a.getSerialNumber(), a.getInstallDate(),
                a.getWarrantyExpiryDate(), a.getCriticality(), a.getStatus(), a.getNotes(), a.getCreatedAt());
        return new FmAssetLookupResponse(asset, FmAssetCode.payloadFor(a.getId()), open, nextPpm);
    }
}
