package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilities.domain.model.FacilityAsset;
import za.co.handyflow.platform.facilities.domain.model.FacilityInspection;
import za.co.handyflow.platform.facilities.domain.model.FacilityPpmSchedule;
import za.co.handyflow.platform.facilities.domain.repository.*;
import za.co.handyflow.platform.facilities.dto.AssetLookupResponse;
import za.co.handyflow.platform.facilities.dto.AssetResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Scan-and-go: resolves a QR payload, asset id or tag and returns the asset with what is happening to it. */
@Service
@RequiredArgsConstructor
public class FacilityAssetLookupService {

    static final int MAX_OPEN_WORK_ORDERS = 10;

    private final FacilityAssetRepository assetRepository;
    private final FacilityWorkOrderRepository workOrderRepository;
    private final FacilityPpmScheduleRepository ppmScheduleRepository;
    private final FacilityInspectionRepository inspectionRepository;
    private final FacilityCorrectiveActionRepository actionRepository;
    private final FacilityAccessPolicy accessPolicy;

    @Transactional(readOnly = true)
    public AssetLookupResponse lookup(TenantId tenantId, String code) {
        FacilityAssetCode.Parsed parsed = FacilityAssetCode.parse(code);
        AccessScope scope = accessPolicy.scope(tenantId);
        FacilityAsset asset = (parsed.id() != null
                ? assetRepository.findActiveById(tenantId, parsed.id())
                : assetRepository.findActiveByTag(tenantId, parsed.tag()))
                .filter(a -> scope.canSeeSite(a.getSiteId()))
                .orElseThrow(() -> new ResourceNotFoundException("FacilityAsset", code.trim()));
        return build(tenantId, asset, scope);
    }

    @Transactional(readOnly = true)
    public String qrPayload(TenantId tenantId, UUID assetId) {
        AccessScope scope = accessPolicy.scope(tenantId);
        assetRepository.findActiveById(tenantId, assetId)
                .filter(a -> scope.canSeeSite(a.getSiteId()))
                .orElseThrow(() -> new ResourceNotFoundException("FacilityAsset", assetId.toString()));
        return FacilityAssetCode.payloadFor(assetId);
    }

    private AssetLookupResponse build(TenantId tenantId, FacilityAsset a, AccessScope scope) {
        List<AssetLookupResponse.OpenWorkOrder> open = workOrderRepository.findOpenForAsset(tenantId, a.getId()).stream()
                .filter(scope::canSeeWorkOrder)
                .limit(MAX_OPEN_WORK_ORDERS)
                .map(w -> new AssetLookupResponse.OpenWorkOrder(w.getId(), w.getWorkOrderNumber(), w.getStatus(), w.getPriority(),
                        w.getDescription(), w.getScheduledDate()))
                .toList();

        LocalDate nextPpm = ppmScheduleRepository.findByAsset(tenantId, a.getId()).stream()
                .filter(FacilityPpmSchedule::isActive)
                .map(FacilityPpmSchedule::getNextDueDate)
                .min(Comparator.naturalOrder()).orElse(null);

        List<FacilityInspection> last = inspectionRepository.findLatestCompletedForAsset(tenantId, a.getId(), PageRequest.of(0, 1));
        AssetLookupResponse.LastInspection lastInspection = last.isEmpty() ? null
                : new AssetLookupResponse.LastInspection(last.get(0).getId(), last.get(0).getTemplateName(),
                        last.get(0).getResult(), last.get(0).getCompletedAt());

        AssetResponse asset = new AssetResponse(a.getId(), a.getSiteId(), a.getAssetTag(), a.getName(), a.getAssetType(),
                a.getLocation(), a.getManufacturer(), a.getModel(), a.getSerialNumber(), a.getInstallDate(),
                a.getWarrantyExpiryDate(), a.getCriticality(), a.getStatus(), a.getNotes(), a.getCreatedAt());
        return new AssetLookupResponse(asset, FacilityAssetCode.payloadFor(a.getId()), open,
                actionRepository.countUnfinishedForAsset(tenantId, a.getId()), lastInspection, nextPpm);
    }
}
