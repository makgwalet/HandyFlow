package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.earthmoving.domain.model.EarthAsset;
import za.co.handyflow.platform.earthmoving.domain.model.EarthMeterReading;
import za.co.handyflow.platform.earthmoving.domain.model.EarthmovingRules;
import za.co.handyflow.platform.earthmoving.domain.model.MeterRules;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthAssetRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthMaintenancePlanRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthMeterReadingRepository;
import za.co.handyflow.platform.earthmoving.dto.ChangeMeterRequest;
import za.co.handyflow.platform.earthmoving.dto.CorrectReadingRequest;
import za.co.handyflow.platform.earthmoving.dto.MeterReadingResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A machine's meter: the history of its readings, correcting a wrong reading, and switching between hours and kilometres.
 * Readings only count up. A correction is the one way down; it needs a reason, is audited, and adds a row instead of
 * rewriting one.
 */
@Service
@RequiredArgsConstructor
public class EarthMeterService {

    private final EarthAssetRepository assets;
    private final EarthMeterReadingRepository readings;
    private final EarthMaintenancePlanRepository plans;
    private final EarthAlertService alerts;
    private final EarthmovingAccess access;
    private final AuditTrailFacade trail;

    /** Called after a reading went up (manual update, end of shift, service). Writes a history row if the number changed. */
    public void record(TenantId tenantId, EarthAsset asset, BigDecimal before, String source, UUID actor) {
        if (asset.getCurrentHours() == null) return;
        if (before != null && before.compareTo(asset.getCurrentHours()) == 0) return;
        readings.save(EarthMeterReading.of(tenantId, asset.getId(), asset.getMeterType(), asset.getCurrentHours(), before, source, null, actor));
    }

    @Transactional(readOnly = true)
    public List<MeterReadingResponse> history(TenantId tenantId, UUID assetId) {
        access.require(EarthmovingRules.READ, "view meter readings");
        find(tenantId, assetId);
        return readings.findByAsset(tenantId, assetId, PageRequest.of(0, 200)).stream().map(EarthMeterService::toResponse).toList();
    }

    @Transactional
    public MeterReadingResponse correct(TenantId tenantId, UUID assetId, CorrectReadingRequest req, UUID actor) {
        access.require(EarthmovingRules.MAINTAIN, "correct a meter reading");
        EarthAsset asset = find(tenantId, assetId);
        List<EarthMeterReading> latest = readings.findByAsset(tenantId, assetId, PageRequest.of(0, 1));
        BigDecimal floor = BigDecimal.ZERO;
        if (!latest.isEmpty()) {
            EarthMeterReading last = latest.get(0);
            if ("CORRECTION".equals(last.getSource()))
                throw new IllegalStateException("The latest reading was already corrected. Record a new reading instead.");
            if (!"TYPE_CHANGE".equals(last.getSource()) && last.getPreviousReading() != null) floor = last.getPreviousReading();
        }
        BigDecimal before = asset.getCurrentHours();
        String why = MeterRules.checkCorrection(before, req.reading(), floor, asset.getLastServiceHours(), req.reason());
        boolean wasDue = asset.isDueForService();

        asset.correctReading(req.reading());
        assets.save(asset);
        EarthMeterReading row = readings.save(EarthMeterReading.of(tenantId, assetId, asset.getMeterType(), req.reading(), before, "CORRECTION", why, actor));
        trail.record(tenantId, "EARTHMOVING_METER_CORRECTED", "EARTHMOVING_ASSET", assetId,
                Map.of("reading", String.valueOf(before), "meterType", asset.getMeterType()),
                Map.of("reading", String.valueOf(req.reading()), "reason", why));
        if (wasDue && !asset.isDueForService()) alerts.resolveByKey(tenantId, "SERVICE_DUE:" + assetId, actor, "Meter reading corrected");
        return toResponse(row);
    }

    /** Hours to kilometres or back. Administrators only; the three numbers are given again in the new unit. */
    @Transactional
    public MeterReadingResponse change(TenantId tenantId, UUID assetId, ChangeMeterRequest req, UUID actor) {
        access.require(EarthmovingRules.ADMIN, "change a meter type");
        EarthAsset asset = find(tenantId, assetId);
        String type = MeterRules.normalizeType(req.meterType());
        String why = MeterRules.checkChange(asset.getMeterType(), type, req.currentReading(), req.lastServiceReading(), req.serviceInterval(), req.reason());
        long metered = plans.findForAsset(tenantId, assetId).stream().filter(p -> p.isActive() && "HOURS".equals(p.getTriggerType())).count();
        if (metered > 0)
            throw new IllegalStateException("This machine has " + metered + " active service plan(s) counted on its meter. "
                    + "Switch them off or change them first, since their intervals are in " + MeterRules.word(asset.getMeterType()) + ".");

        Map<String, String> before = Map.of("meterType", asset.getMeterType(), "reading", String.valueOf(asset.getCurrentHours()),
                "lastService", String.valueOf(asset.getLastServiceHours()), "interval", String.valueOf(asset.getServiceIntervalHours()));
        BigDecimal previous = asset.getCurrentHours();
        asset.changeMeter(type, req.currentReading(), req.lastServiceReading(), req.serviceInterval());
        assets.save(asset);
        EarthMeterReading row = readings.save(EarthMeterReading.of(tenantId, assetId, type, req.currentReading(), previous, "TYPE_CHANGE", why, actor));
        trail.record(tenantId, "EARTHMOVING_METER_CHANGED", "EARTHMOVING_ASSET", assetId, before,
                Map.of("meterType", type, "reading", String.valueOf(req.currentReading()), "lastService", String.valueOf(req.lastServiceReading()),
                        "interval", String.valueOf(req.serviceInterval()), "reason", why));
        if (!asset.isDueForService()) alerts.resolveByKey(tenantId, "SERVICE_DUE:" + assetId, actor, "Meter changed");
        return toResponse(row);
    }

    private EarthAsset find(TenantId tenantId, UUID assetId) {
        return assets.findActiveById(tenantId, assetId).orElseThrow(() -> new ResourceNotFoundException("Asset", assetId.toString()));
    }

    private static MeterReadingResponse toResponse(EarthMeterReading r) {
        return new MeterReadingResponse(r.getId(), r.getMeterType(), r.getReading(), r.getPreviousReading(), r.getSource(),
                r.getReason(), r.getActorUserId(), r.getRecordedAt());
    }
}
