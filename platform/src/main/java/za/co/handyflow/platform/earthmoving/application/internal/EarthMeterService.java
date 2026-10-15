package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.earthmoving.domain.model.EarthAsset;
import za.co.handyflow.platform.earthmoving.domain.model.EarthMeterDispute;
import za.co.handyflow.platform.earthmoving.domain.model.EarthMeterReading;
import za.co.handyflow.platform.earthmoving.domain.model.EarthmovingRules;
import za.co.handyflow.platform.earthmoving.domain.model.MeterRules;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthAssetRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthMaintenancePlanRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthMeterDisputeRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthMeterReadingRepository;
import za.co.handyflow.platform.earthmoving.dto.ChangeMeterRequest;
import za.co.handyflow.platform.earthmoving.dto.CorrectReadingRequest;
import za.co.handyflow.platform.earthmoving.dto.DisputeReadingRequest;
import za.co.handyflow.platform.earthmoving.dto.ResolveDisputeRequest;
import za.co.handyflow.platform.earthmoving.dto.MeterReadingResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.HashMap;
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
    private final EarthMeterDisputeRepository disputes;
    private final EarthHireService hire;
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
        Map<UUID, EarthMeterDispute> latest = new HashMap<>();
        for (EarthMeterDispute d : disputes.findByAsset(tenantId, assetId)) latest.putIfAbsent(d.getReadingId(), d); // newest first
        return readings.findByAsset(tenantId, assetId, PageRequest.of(0, 200)).stream()
                .map(r -> toResponse(r, latest.get(r.getId()), null)).toList();
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
        UUID replaces = latest.isEmpty() ? null : latest.get(0).getId();
        EarthMeterReading row = readings.save(EarthMeterReading.correctionOf(tenantId, assetId, asset.getMeterType(), req.reading(), before, why, actor, replaces));
        trail.record(tenantId, "EARTHMOVING_METER_CORRECTED", "EARTHMOVING_ASSET", assetId,
                Map.of("reading", String.valueOf(before), "meterType", asset.getMeterType()),
                Map.of("reading", String.valueOf(req.reading()), "reason", why));
        if (wasDue && !asset.isDueForService()) alerts.resolveByKey(tenantId, "SERVICE_DUE:" + assetId, actor, "Meter reading corrected");
        if (replaces != null)
            for (EarthMeterDispute d : disputes.findOpenForReadings(tenantId, List.of(replaces))) {
                d.corrected("Corrected from " + before + " to " + req.reading() + ": " + why, actor);
                disputes.save(d);
                trail.record(tenantId, "EARTHMOVING_METER_DISPUTE_CORRECTED", "EARTHMOVING_ASSET", assetId,
                        Map.of("dispute", d.getReason(), "status", "OPEN"), Map.of("status", "CORRECTED", "reading", String.valueOf(req.reading())));
            }
        EarthHireService.Refresh charges = MeterRules.KILOMETRES.equals(asset.getMeterType())
                ? hire.refreshKilometreCharges(tenantId, assetId, actor, "reading corrected") : null;
        return toResponse(row, null, charges);
    }

    /**
     * Says a kilometre reading looks wrong. Charges built on it are held back from invoicing and no new charge can use it until
     * the dispute is closed, by confirming the reading or by correcting it. Billing or maintenance may raise one.
     */
    @Transactional
    public MeterReadingResponse dispute(TenantId tenantId, UUID assetId, UUID readingId, DisputeReadingRequest req, UUID actor) {
        if (!access.has(EarthmovingRules.MAINTAIN)) access.require(EarthmovingRules.ADMIN, "dispute a meter reading");
        find(tenantId, assetId);
        EarthMeterReading reading = readings.findById(readingId)
                .filter(r -> r.getTenantId().equals(tenantId) && r.getAssetId().equals(assetId))
                .orElseThrow(() -> new ResourceNotFoundException("Meter reading", readingId.toString()));
        if (!MeterRules.KILOMETRES.equals(reading.getMeterType()))
            throw new IllegalArgumentException("Only kilometre readings are billed on, so only they can be disputed.");
        List<EarthMeterReading> all = readings.findByAsset(tenantId, assetId, PageRequest.of(0, 5000));
        if (all.stream().anyMatch(r -> readingId.equals(r.getCorrectsId())))
            throw new IllegalStateException("That reading has already been corrected. Dispute the corrected reading instead.");
        if (!disputes.findOpenForReadings(tenantId, List.of(readingId)).isEmpty())
            throw new IllegalStateException("That reading is already disputed.");
        EarthMeterDispute d = disputes.saveAndFlush(EarthMeterDispute.raise(tenantId, assetId, readingId, req == null ? null : req.reason(), actor));
        trail.record(tenantId, "EARTHMOVING_METER_DISPUTED", "EARTHMOVING_ASSET", assetId, null,
                Map.of("reading", String.valueOf(reading.getReading()), "reason", d.getReason()));
        EarthHireService.Refresh charges = hire.refreshKilometreCharges(tenantId, assetId, actor, "reading disputed");
        return toResponse(reading, d, charges);
    }

    /** The reading stands: the dispute ends and charges held for it are released. */
    @Transactional
    public MeterReadingResponse confirm(TenantId tenantId, UUID assetId, UUID disputeId, ResolveDisputeRequest req, UUID actor) {
        access.require(EarthmovingRules.MAINTAIN, "confirm a disputed meter reading");
        find(tenantId, assetId);
        EarthMeterDispute d = disputes.findByTenantAndId(tenantId, disputeId).filter(x -> x.getAssetId().equals(assetId))
                .orElseThrow(() -> new ResourceNotFoundException("Meter dispute", disputeId.toString()));
        EarthMeterReading reading = readings.findById(d.getReadingId()).orElseThrow(() -> new ResourceNotFoundException("Meter reading", d.getReadingId().toString()));
        d.confirm(req == null ? null : req.note(), actor);
        disputes.save(d);
        trail.record(tenantId, "EARTHMOVING_METER_DISPUTE_CONFIRMED", "EARTHMOVING_ASSET", assetId,
                Map.of("reading", String.valueOf(reading.getReading()), "dispute", d.getReason()), Map.of("status", "CONFIRMED", "note", d.getResolutionNote()));
        EarthHireService.Refresh charges = hire.refreshKilometreCharges(tenantId, assetId, actor, "dispute confirmed");
        return toResponse(reading, d, charges);
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
        return toResponse(row, null, null);
    }

    private EarthAsset find(TenantId tenantId, UUID assetId) {
        return assets.findActiveById(tenantId, assetId).orElseThrow(() -> new ResourceNotFoundException("Asset", assetId.toString()));
    }

    private static MeterReadingResponse toResponse(EarthMeterReading r, EarthMeterDispute d, EarthHireService.Refresh charges) {
        return new MeterReadingResponse(r.getId(), r.getMeterType(), r.getReading(), r.getPreviousReading(), r.getSource(),
                r.getReason(), r.getActorUserId(), r.getRecordedAt(), r.getCorrectsId(),
                d == null ? null : d.getId(), d == null ? null : d.getStatus(), d == null ? null : d.getReason(),
                charges == null ? null : charges.recalculated(), charges == null ? null : charges.held(), charges == null ? null : charges.invoiced());
    }
}
