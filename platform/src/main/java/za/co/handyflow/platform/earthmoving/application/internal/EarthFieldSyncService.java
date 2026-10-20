package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
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
 * Receives entries captured on a phone, possibly hours or days earlier. Each entry carries a submission id made on the phone, so sending it twice does
 * nothing the second time. The server re-checks every entry against the machine as it is now, with the sender's own permissions, and records the outcome:
 * SYNCED (applied), REJECTED (refused, with the reason) or CONFLICT (a newer reading exists; kept beside it and flagged for a person to review, never
 * overwriting). Deployments, maintenance release and disposal approval are not accepted here at all: they must be done while connected.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EarthFieldSyncService {

    private final EarthFieldSubmissionRepository submissions;
    private final EarthAssetRepository assets;
    private final EarthMeterReadingRepository meterReadings;
    private final EarthAssetService assetService;
    private final EarthTelematicsService telematics;
    private final AuditTrailFacade trail;
    private final EarthmovingAccess access;

    /** Handles a batch in the order the phone captured it. One bad entry never stops the others. */
    @Transactional
    public List<FieldEntryResult> sync(TenantId tenantId, FieldSyncRequest req, UUID actor) {
        access.require(EarthmovingRules.WRITE, "send field entries");
        List<FieldEntryRequest> entries = req.entries() == null ? List.of() : req.entries();
        if (entries.size() > FieldRules.MAX_BATCH) throw new IllegalArgumentException("Send at most " + FieldRules.MAX_BATCH + " entries at a time.");

        Set<UUID> ids = entries.stream().map(FieldEntryRequest::submissionId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, EarthFieldSubmission> known = ids.isEmpty() ? new HashMap<>()
                : submissions.findBySubmissionIds(tenantId, ids).stream().collect(Collectors.toMap(EarthFieldSubmission::getSubmissionId, Function.identity()));
        Map<UUID, EarthAsset> assetCache = new HashMap<>();
        Set<UUID> seenInBatch = new HashSet<>();

        List<FieldEntryRequest> ordered = new ArrayList<>(entries);
        ordered.sort(Comparator.comparing((FieldEntryRequest e) -> e.capturedAt() == null ? Instant.MAX : e.capturedAt()));
        List<FieldEntryResult> out = new ArrayList<>();
        for (FieldEntryRequest e : ordered) {
            if (e.submissionId() == null) throw new IllegalArgumentException("Every entry needs its own submission id.");
            EarthFieldSubmission existing = known.get(e.submissionId());
            if (existing != null) {
                // The same entry sent again. It must belong to this sender; someone else's id is simply not found.
                if (!existing.getSubmittedBy().equals(actor)) throw new ResourceNotFoundException("Submission", e.submissionId().toString());
                out.add(toResult(existing, assetCache, tenantId, true));
                continue;
            }
            if (!seenInBatch.add(e.submissionId())) continue;   // listed twice in one request: first one wins
            EarthFieldSubmission s = submissions.save(handle(tenantId, e, actor, assetCache));
            known.put(s.getSubmissionId(), s);
            out.add(toResult(s, assetCache, tenantId, false));
        }
        return out;
    }

    /** The sender's own recent entries with their outcome, so the phone can show pending, synced, rejected or conflict. */
    @Transactional(readOnly = true)
    public List<FieldEntryResult> mine(TenantId tenantId, UUID actor, int limit) {
        access.require(EarthmovingRules.READ, "view field entries");
        Map<UUID, EarthAsset> cache = new HashMap<>();
        return submissions.findMine(tenantId, actor, PageRequest.of(0, Math.max(1, Math.min(limit, 200)))).stream().map(s -> toResult(s, cache, tenantId, false)).toList();
    }

    @Transactional(readOnly = true)
    public List<FieldEntryResult> openConflicts(TenantId tenantId) {
        access.require(EarthmovingRules.MAINTAIN, "review field conflicts");
        Map<UUID, EarthAsset> cache = new HashMap<>();
        return submissions.findOpenConflicts(tenantId).stream().map(s -> toResult(s, cache, tenantId, false)).toList();
    }

    /**
     * A person decides a conflict: keep what the server has, or apply the phone's entry now. Applying checks the entry again against the machine as it
     * stands today, so it cannot lower a meter or touch a retired machine. Both versions stay on record either way.
     */
    @Transactional
    public FieldEntryResult review(TenantId tenantId, UUID id, ReviewFieldConflictRequest req, UUID actor) {
        access.require(EarthmovingRules.MAINTAIN, "review field conflicts");
        EarthFieldSubmission s = submissions.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Field entry", String.valueOf(id)));
        if (!s.isOpenConflict()) throw new IllegalStateException("Only a conflict that has not been reviewed can be reviewed.");
        String decision = FieldRules.decision(req == null ? null : req.decision());
        if (FieldRules.APPLIED.equals(decision)) {
            EarthAsset asset = assets.findActiveById(tenantId, s.getAssetId()).orElseThrow(() -> new IllegalStateException("The machine is no longer in the fleet, so this entry cannot be applied."));
            if (asset.getStatus() == AssetStatus.RETIRED) throw new IllegalStateException("The machine is retired, so this entry cannot be applied.");
            if (FieldRules.METER_READING.equals(s.getKind())) {
                if (s.getMeterValue().compareTo(asset.getCurrentHours()) < 0)
                    throw new IllegalStateException("The entry is below the machine's current meter (" + asset.getCurrentHours().stripTrailingZeros().toPlainString() + "), so it cannot be applied.");
                assetService.updateHours(tenantId, asset.getId(), new UpdateHoursRequest(s.getMeterValue()), actor, "FIELD");
            }
        }
        s.review(decision, req == null ? null : req.note(), actor);
        trail.record(tenantId, "EARTHMOVING_FIELD_CONFLICT_REVIEWED", "EARTHMOVING_FIELD_ENTRY", s.getId(), Map.of("status", FieldRules.CONFLICT),
                Map.of("decision", decision, "kind", s.getKind()));
        return toResult(s, new HashMap<>(), tenantId, false);
    }

    // ── one entry ────────────────────────────────────────────────────────────

    private EarthFieldSubmission handle(TenantId tenantId, FieldEntryRequest e, UUID actor, Map<UUID, EarthAsset> cache) {
        String kind = FieldRules.kind(e.kind());
        Instant now = Instant.now();
        String refused = FieldRules.kindProblem(kind);
        if (refused == null) refused = FieldRules.entryProblem(kind, e.meterValue(), e.latitude(), e.longitude(), e.capturedAt(), now);
        if (refused != null) return stored(tenantId, e, kind, actor, FieldRules.REJECTED, refused);

        EarthAsset asset = e.assetId() == null ? null : cache.computeIfAbsent(e.assetId(), id -> assets.findActiveById(tenantId, id).orElse(null));
        if (asset == null) return stored(tenantId, e, kind, actor, FieldRules.REJECTED, "That machine was not found.");
        if (asset.getStatus() == AssetStatus.RETIRED) return stored(tenantId, e, kind, actor, FieldRules.REJECTED, "That machine is retired.");

        if (FieldRules.METER_READING.equals(kind)) {
            FieldRules.Verdict v = FieldRules.judgeMeter(e.meterValue(), e.capturedAt(), asset.getCurrentHours(), latestMeterTime(tenantId, asset.getId()));
            if (v != null) return stored(tenantId, e, kind, actor, v.status(), v.reason());
            assetService.updateHours(tenantId, asset.getId(), new UpdateHoursRequest(e.meterValue()), actor, "FIELD");
        } else {
            try {
                TelematicsRules.checkTime(e.capturedAt(), now);
            } catch (IllegalArgumentException bad) {
                return stored(tenantId, e, kind, actor, FieldRules.REJECTED, bad.getMessage());
            }
            telematics.recordManual(tenantId, asset.getId(), new ManualTelematicsReadingRequest(e.capturedAt(), e.latitude(), e.longitude(), null, null, e.note()), actor);
        }
        return stored(tenantId, e, kind, actor, FieldRules.SYNCED, null);
    }

    /**
     * The time of the newest meter reading the server knows of. A reading entered on the server counts from when it was recorded; one that came from a
     * phone counts from when it was captured, so a phone that syncs late is not judged against its own sync time.
     */
    private Instant latestMeterTime(TenantId tenantId, UUID assetId) {
        Instant server = meterReadings.findByAssetExcludingSource(tenantId, assetId, "FIELD", PageRequest.of(0, 1)).stream()
                .map(EarthMeterReading::getRecordedAt).findFirst().orElse(null);
        Instant field = submissions.findAppliedByAsset(tenantId, assetId, FieldRules.METER_READING, PageRequest.of(0, 1)).stream()
                .map(EarthFieldSubmission::getCapturedAt).findFirst().orElse(null);
        return server == null ? field : field == null ? server : server.isAfter(field) ? server : field;
    }

    private EarthFieldSubmission stored(TenantId tenantId, FieldEntryRequest e, String kind, UUID actor, String status, String reason) {
        if (!FieldRules.SYNCED.equals(status)) log.info("Field entry {} kind={} status={}", e.submissionId(), kind, status);
        return EarthFieldSubmission.received(tenantId, e.submissionId(), kind.isEmpty() ? "UNKNOWN" : kind, e.assetId(), e.capturedAt(), e.meterValue(),
                e.latitude(), e.longitude(), e.note(), actor, status, reason);
    }

    private FieldEntryResult toResult(EarthFieldSubmission s, Map<UUID, EarthAsset> cache, TenantId tenantId, boolean duplicate) {
        EarthAsset a = s.getAssetId() == null ? null : cache.computeIfAbsent(s.getAssetId(), id -> assets.findActiveById(tenantId, id).orElse(null));
        return new FieldEntryResult(s.getId(), s.getSubmissionId(), s.getKind(), s.getAssetId(), a == null ? null : a.getName(), s.getCapturedAt(), s.getReceivedAt(),
                s.getStatus(), s.getReason(), duplicate, s.getReviewDecision(), s.getReviewNote(), s.getReviewedAt());
    }
}
