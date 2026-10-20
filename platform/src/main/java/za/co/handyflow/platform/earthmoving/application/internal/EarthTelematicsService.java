package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.earthmoving.application.internal.TelematicsProvider.ProviderReading;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.earthmoving.domain.repository.*;
import za.co.handyflow.platform.earthmoving.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provider-neutral tracking. Maps devices to machines (administrators), stores normalised readings from any provider adapter or from a person, and says
 * where each machine was last seen. It reports and never acts: a provider's meter value is shown beside the machine's own and never replaces it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EarthTelematicsService {

    private final EarthTelematicsDeviceRepository devices;
    private final EarthTelematicsReadingRepository readings;
    private final EarthAssetRepository assets;
    private final ObjectProvider<TelematicsProvider> providers;
    private final AuditTrailFacade trail;
    private final EarthmovingAccess access;

    // ── devices ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TelematicsDeviceResponse> devices(TenantId tenantId, boolean includeUnmapped) {
        access.require(EarthmovingRules.READ, "view tracking devices");
        Map<UUID, EarthAsset> byId = assetsById(tenantId);
        List<EarthTelematicsDevice> list = includeUnmapped ? devices.findAllForTenant(tenantId) : devices.findActive(tenantId);
        return list.stream().map(d -> toResponse(d, byId.get(d.getAssetId()))).toList();
    }

    /** The provider codes that have an adapter installed (none, until a provider is chosen). Devices may still be mapped under any code. */
    public List<String> installedProviders() {
        return providers.orderedStream().map(TelematicsProvider::code).toList();
    }

    @Transactional
    public TelematicsDeviceResponse map(TenantId tenantId, MapTelematicsDeviceRequest req, UUID actor) {
        access.require(EarthmovingRules.ADMIN, "map a tracking device");
        EarthAsset asset = assets.findActiveById(tenantId, req.assetId()).orElseThrow(() -> new ResourceNotFoundException("Asset", String.valueOf(req.assetId())));
        String provider = TelematicsRules.providerCode(req.providerCode());
        String external = TelematicsRules.externalId(req.externalId());
        devices.findActiveDevice(tenantId, provider, external).ifPresent(d -> {
            throw new IllegalStateException(d.getAssetId().equals(asset.getId()) ? "That device is already mapped to this machine."
                    : "That device is already mapped to another machine. Unmap it there first.");
        });
        devices.findActiveForAsset(tenantId, asset.getId(), provider).ifPresent(d -> {
            throw new IllegalStateException("This machine already has a " + provider + " device (" + d.getExternalId() + "). Unmap it first.");
        });
        EarthTelematicsDevice d = devices.save(EarthTelematicsDevice.map(tenantId, asset.getId(), provider, external, req.label(), actor));
        trail.record(tenantId, "EARTHMOVING_TELEMATICS_DEVICE_MAPPED", "EARTHMOVING_TELEMATICS_DEVICE", d.getId(), Map.of(),
                Map.of("asset", asset.getName(), "provider", provider, "device", external));
        return toResponse(d, asset);
    }

    @Transactional
    public TelematicsDeviceResponse unmap(TenantId tenantId, UUID deviceId, UnmapTelematicsDeviceRequest req, UUID actor) {
        access.require(EarthmovingRules.ADMIN, "unmap a tracking device");
        EarthTelematicsDevice d = devices.findByTenantAndId(tenantId, deviceId).orElseThrow(() -> new ResourceNotFoundException("Device", String.valueOf(deviceId)));
        d.unmap(req == null ? null : req.reason(), actor);
        trail.record(tenantId, "EARTHMOVING_TELEMATICS_DEVICE_UNMAPPED", "EARTHMOVING_TELEMATICS_DEVICE", d.getId(), Map.of("active", true),
                Map.of("active", false, "reason", d.getUnmapReason()));
        return toResponse(d, assets.findActiveById(tenantId, d.getAssetId()).orElse(null));
    }

    // ── readings ─────────────────────────────────────────────────────────────

    /**
     * Stores readings a provider adapter delivers for one device. Called by adapters and the poller, not by a person, so it checks no permission.
     * Readings that fail a check, or arrive twice, are reported and skipped; the rest are kept.
     */
    @Transactional
    public TelematicsIngestResult ingest(TenantId tenantId, String providerCode, String externalDeviceId, List<ProviderReading> batch) {
        String provider = TelematicsRules.providerCode(providerCode);
        EarthTelematicsDevice device = devices.findActiveDevice(tenantId, provider, TelematicsRules.externalId(externalDeviceId))
                .orElseThrow(() -> new IllegalArgumentException("No machine is mapped to that device."));
        Instant now = Instant.now();
        int accepted = 0, duplicates = 0;
        List<String> rejected = new ArrayList<>();
        for (ProviderReading p : batch == null ? List.<ProviderReading>of() : batch) {
            try {
                if (p == null) throw new IllegalArgumentException("Empty reading.");
                EarthTelematicsReading r = EarthTelematicsReading.fromProvider(tenantId, device, p.recordedAt(), now, p.latitude(), p.longitude(), p.meterValue(), p.meterUnit());
                if (readings.countDuplicate(device.getId(), r.getRecordedAt()) > 0) { duplicates++; continue; }
                readings.save(r);
                accepted++;
            } catch (IllegalArgumentException e) {
                rejected.add((p == null || p.recordedAt() == null ? "?" : p.recordedAt().toString()) + ": " + e.getMessage());
            }
        }
        if (!rejected.isEmpty()) log.warn("Telematics readings refused provider={} device={} count={}", provider, device.getExternalId(), rejected.size());
        return new TelematicsIngestResult(accepted, duplicates, rejected);
    }

    /** Asks every installed adapter for the readings of its active devices since the last one we hold. With no adapter installed it does nothing. */
    @Transactional
    public int pollInstalled(TenantId tenantId) {
        int stored = 0;
        Map<String, TelematicsProvider> byCode = providers.orderedStream().collect(Collectors.toMap(TelematicsProvider::code, Function.identity(), (a, b) -> a));
        if (byCode.isEmpty()) return 0;
        for (EarthTelematicsDevice d : devices.findActive(tenantId)) {
            TelematicsProvider p = byCode.get(d.getProviderCode());
            if (p == null) continue;
            Instant since = readings.findForAsset(tenantId, d.getAssetId(), PageRequest.of(0, 1)).stream()
                    .filter(r -> d.getId().equals(r.getDeviceId())).map(EarthTelematicsReading::getRecordedAt).findFirst().orElse(null);
            try {
                stored += ingest(tenantId, d.getProviderCode(), d.getExternalId(), p.fetchSince(d.getExternalId(), since)).accepted();
            } catch (RuntimeException e) {
                log.warn("Telematics poll failed provider={} device={}: {}", d.getProviderCode(), d.getExternalId(), e.getMessage());
            }
        }
        return stored;
    }

    @Transactional
    public TelematicsReadingResponse recordManual(TenantId tenantId, UUID assetId, ManualTelematicsReadingRequest req, UUID actor) {
        access.require(EarthmovingRules.WRITE, "record a reading");
        EarthAsset asset = assets.findActiveById(tenantId, assetId).orElseThrow(() -> new ResourceNotFoundException("Asset", String.valueOf(assetId)));
        Instant now = Instant.now();
        EarthTelematicsReading r = readings.save(EarthTelematicsReading.manual(tenantId, asset.getId(), req.recordedAt() == null ? now : req.recordedAt(), now,
                req.latitude(), req.longitude(), req.meterValue(), req.meterUnit(), req.note(), actor));
        trail.record(tenantId, "EARTHMOVING_TELEMATICS_MANUAL_READING", "EARTHMOVING_ASSET", asset.getId(), Map.of(),
                Map.of("recordedAt", r.getRecordedAt().toString(), "hasPosition", r.getLatitude() != null, "hasMeter", r.getMeterValue() != null));
        return toResponse(r);
    }

    @Transactional(readOnly = true)
    public List<TelematicsReadingResponse> history(TenantId tenantId, UUID assetId, int limit) {
        access.require(EarthmovingRules.READ, "view tracking readings");
        assets.findActiveById(tenantId, assetId).orElseThrow(() -> new ResourceNotFoundException("Asset", String.valueOf(assetId)));
        Pageable page = PageRequest.of(0, Math.max(1, Math.min(limit, 200)));
        return readings.findForAsset(tenantId, assetId, page).stream().map(this::toResponse).toList();
    }

    // ── last seen ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TrackingStatusResponse> status(TenantId tenantId) {
        access.require(EarthmovingRules.READ, "view tracking");
        Instant now = Instant.now();
        Map<UUID, EarthTelematicsReading> provPos = latest(readings.findLatestPositionBySource(tenantId, TelematicsRules.PROVIDER));
        Map<UUID, EarthTelematicsReading> provMeter = latest(readings.findLatestMeterBySource(tenantId, TelematicsRules.PROVIDER));
        Map<UUID, EarthTelematicsReading> manPos = latest(readings.findLatestPositionBySource(tenantId, TelematicsRules.MANUAL));
        Map<UUID, EarthTelematicsReading> manMeter = latest(readings.findLatestMeterBySource(tenantId, TelematicsRules.MANUAL));
        Map<UUID, Long> deviceCount = devices.findActive(tenantId).stream().collect(Collectors.groupingBy(EarthTelematicsDevice::getAssetId, Collectors.counting()));
        List<TrackingStatusResponse> out = new ArrayList<>();
        for (EarthAsset a : assets.findAllActive(tenantId, Pageable.unpaged()).getContent()) {
            if (a.getStatus() == AssetStatus.RETIRED) continue;
            String unit = TelematicsRules.unitForMeter(a.getMeterType());
            TrackingStatusResponse.Source prov = source(provPos.get(a.getId()), provMeter.get(a.getId()), now);
            TrackingStatusResponse.Source man = source(manPos.get(a.getId()), manMeter.get(a.getId()), now);
            BigDecimal diff = TelematicsRules.meterDifference(prov.meterValue(), prov.meterUnit(), a.getCurrentHours(), unit);
            out.add(new TrackingStatusResponse(a.getId(), a.getName(), a.getFleetNumber(), unit, a.getCurrentHours(),
                    deviceCount.getOrDefault(a.getId(), 0L).intValue(), prov, man, diff));
        }
        return out;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** One reading per machine: the newest taken, and for a tie the newest received. */
    private static Map<UUID, EarthTelematicsReading> latest(List<EarthTelematicsReading> list) {
        Map<UUID, EarthTelematicsReading> out = new HashMap<>();
        for (EarthTelematicsReading r : list) out.merge(r.getAssetId(), r, (a, b) -> b.getReceivedAt().isAfter(a.getReceivedAt()) ? b : a);
        return out;
    }

    private static TrackingStatusResponse.Source source(EarthTelematicsReading pos, EarthTelematicsReading meter, Instant now) {
        Instant seen = pos == null ? (meter == null ? null : meter.getRecordedAt())
                : meter == null || pos.getRecordedAt().isAfter(meter.getRecordedAt()) ? pos.getRecordedAt() : meter.getRecordedAt();
        String provider = pos != null ? pos.getProviderCode() : meter == null ? null : meter.getProviderCode();
        return new TrackingStatusResponse.Source(TelematicsRules.freshness(seen, now).name(), seen,
                pos == null ? null : pos.getRecordedAt(), pos == null ? null : pos.getLatitude(), pos == null ? null : pos.getLongitude(),
                meter == null ? null : meter.getRecordedAt(), meter == null ? null : meter.getMeterValue(), meter == null ? null : meter.getMeterUnit(), provider);
    }

    private Map<UUID, EarthAsset> assetsById(TenantId tenantId) {
        return assets.findAllActive(tenantId, Pageable.unpaged()).getContent().stream().collect(Collectors.toMap(EarthAsset::getId, Function.identity()));
    }

    private TelematicsDeviceResponse toResponse(EarthTelematicsDevice d, EarthAsset a) {
        return new TelematicsDeviceResponse(d.getId(), d.getAssetId(), a == null ? null : a.getName(), a == null ? null : a.getFleetNumber(), d.getProviderCode(),
                d.getExternalId(), d.getLabel(), d.isActive(), d.getMappedAt(), d.getUnmappedAt(), d.getUnmapReason());
    }

    private TelematicsReadingResponse toResponse(EarthTelematicsReading r) {
        return new TelematicsReadingResponse(r.getId(), r.getSource(), r.getProviderCode(), r.getRecordedAt(), r.getReceivedAt(), r.getLatitude(), r.getLongitude(),
                r.getMeterValue(), r.getMeterUnit(), r.getEnteredBy(), r.getNote());
    }
}
