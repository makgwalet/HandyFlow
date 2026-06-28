// security/application/internal/CheckpointScanService.java

package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.Checkpoint;
import za.co.handyflow.platform.security.domain.model.CheckpointLog;
import za.co.handyflow.platform.security.domain.repository.CheckpointLogRepository;
import za.co.handyflow.platform.security.domain.repository.CheckpointRepository;
import za.co.handyflow.platform.security.domain.repository.ShiftRepository;
import za.co.handyflow.platform.security.dto.ScanRequest;
import za.co.handyflow.platform.security.dto.ScanResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * CheckpointScanService — rewritten to fix bugs #6, #7(partial), #11, #18.
 *
 * Original issues:
 *
 *   Bug #6: NFC/BLE scans threw "Invalid QR code" because findByQrCode was
 *   called unconditionally even when qrCode was null.  Fixed by branching on
 *   scanType before the lookup.
 *
 *   Bug #11: findByQrCode had no tenant filter — any tenant's QR could
 *   (in theory) resolve a checkpoint from a different tenant.  Fixed by passing
 *   tenantId to all checkpoint lookup queries.
 *
 *   Bug #18: The same checkpoint could be scanned twice in the same second,
 *   allowing a guard to "scan everything in 10 seconds" and fake a patrol.
 *   Fixed by a configurable per-checkpoint cooldown within a shift.
 *
 *   Bug #7 (partial): QR codes are still plain UUIDs, not HMAC-signed.
 *   Full HMAC signing (using security_sites.qr_secret which already exists
 *   in the DB but is unused) is Phase 1 work.  Phase 0 adds the tenant filter
 *   and NFC/BLE routing so at least the scan service is correct.
 *
 * ─ Scan type routing ──────────────────────────────────────────────────────────
 * The ScanRequest carries a scanType field ("QR", "NFC", "BLE", "GPS_PING",
 * "MANUAL") and the corresponding identifier (qrCode, nfcTagId, bleBeaconId).
 * This service routes to the correct checkpoint lookup based on scanType.
 * GPS_PING and MANUAL don't look up a checkpoint — they log a guard position
 * or a supervisor-entered event instead.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckpointScanService {

    /**
     * Minimum seconds between two scans of the SAME checkpoint in the SAME shift.
     *
     * WHY 60 seconds? A guard physically walking a checkpoint should take at
     * least 30–60 seconds to travel away and return.  10 seconds is a
     * speed-run fraud pattern; 60 seconds is a reasonable floor for any real
     * patrol dwell time.  Sites with longer patrol intervals can raise this;
     * the default errs on the side of catching the obvious fraud case.
     *
     * Phase 1: move this to a per-site configuration column.
     */
    private static final int SCAN_COOLDOWN_SECONDS = 60;

    private final CheckpointRepository    checkpointRepository;
    private final CheckpointLogRepository logRepository;
    private final ShiftRepository         shiftRepository;

    @Transactional
    public ScanResponse scan(TenantId tenantId, ScanRequest req) {

        String scanType = req.scanType() != null ? req.scanType().toUpperCase() : "QR";

        // ── 1. Resolve the checkpoint based on scan method ─────────────────────
        Checkpoint checkpoint = resolveCheckpoint(tenantId, req, scanType);

        // ── 2. Validate shift (if provided) ────────────────────────────────────
        if (req.shiftId() != null) {
            validateShiftForScan(tenantId, req.shiftId(), checkpoint);
        }

        // ── 3. Cooldown check (fixes bug #18) ──────────────────────────────────
        if (req.shiftId() != null) {
            enforceCheckpointCooldown(checkpoint.getId(), req.shiftId());
        }

        // ── 4. Create the log entry ─────────────────────────────────────────────
        CheckpointLog entry = CheckpointLog.create(
                tenantId,
                checkpoint.getId(),
                req.guardId(),
                req.shiftId(),
                req.latitude(),
                req.longitude()
        );
        logRepository.save(entry);

        log.info("[Security] Checkpoint scanned type={} checkpoint='{}' guard={} shift={}",
                scanType, checkpoint.getName(), req.guardId(), req.shiftId());

        return new ScanResponse(
                entry.getId(),
                checkpoint.getName(),
                checkpoint.getSite().getName(),
                entry.getScannedAt()
        );
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Routes the scan to the correct checkpoint lookup based on scanType.
     *
     * WHY switch on scanType instead of just trying each identifier in order?
     * The guard's device knows exactly which hardware triggered the scan.
     * A QR scan will always have qrCode set; an NFC scan will always have
     * nfcTagId set.  Trying identifiers in order would mask bugs in the
     * client (e.g. an NFC scan accidentally sending a stale qrCode would
     * succeed for the wrong reason).
     *
     * Fixing bug #6: the original code called findByQrCode(req.qrCode())
     * unconditionally.  An NFC scan has qrCode=null → throws NPE or
     * "Invalid QR code" before even reaching the repository.
     */
    private Checkpoint resolveCheckpoint(TenantId tenantId, ScanRequest req, String scanType) {
        return switch (scanType) {
            case "QR" -> {
                if (req.qrCode() == null || req.qrCode().isBlank()) {
                    throw new HandyFlowException(
                            "QR code is required for QR scan type",
                            HttpStatus.BAD_REQUEST, "MISSING_QR_CODE");
                }
                yield checkpointRepository.findByQrCode(tenantId, req.qrCode())
                        .orElseThrow(() -> new HandyFlowException(
                                "Invalid or expired QR code",
                                HttpStatus.BAD_REQUEST, "INVALID_QR_CODE"));
            }
            case "NFC" -> {
                if (req.nfcTagId() == null || req.nfcTagId().isBlank()) {
                    throw new HandyFlowException(
                            "NFC tag ID is required for NFC scan type",
                            HttpStatus.BAD_REQUEST, "MISSING_NFC_ID");
                }
                yield checkpointRepository.findByNfcTagUid(tenantId, req.nfcTagId())
                        .orElseThrow(() -> new HandyFlowException(
                                "NFC tag not registered to any checkpoint at this site",
                                HttpStatus.BAD_REQUEST, "INVALID_NFC_TAG"));
            }
            case "BLE" -> {
                if (req.bleBeaconId() == null || req.bleBeaconId().isBlank()) {
                    throw new HandyFlowException(
                            "BLE beacon ID is required for BLE scan type",
                            HttpStatus.BAD_REQUEST, "MISSING_BLE_ID");
                }
                yield checkpointRepository.findByBleBeaconId(tenantId, req.bleBeaconId())
                        .orElseThrow(() -> new HandyFlowException(
                                "BLE beacon not registered to any checkpoint at this site",
                                HttpStatus.BAD_REQUEST, "INVALID_BLE_BEACON"));
            }
            case "GPS_PING", "MANUAL" ->
                // GPS_PING and MANUAL events log guard position, not a specific checkpoint.
                // These do not resolve a checkpoint — return a meaningful error
                // because the caller should use a different endpoint for these.
                // Phase 1: add POST /shifts/{id}/location-ping for GPS_PING events.
                    throw new HandyFlowException(
                            "Scan type " + scanType + " does not target a checkpoint. " +
                                    "Use the shift location endpoint instead.",
                            HttpStatus.BAD_REQUEST, "UNSUPPORTED_SCAN_TYPE_FOR_CHECKPOINT");
            default -> throw new HandyFlowException(
                    "Unknown scan type: " + scanType,
                    HttpStatus.BAD_REQUEST, "UNKNOWN_SCAN_TYPE");
        };
    }

    /**
     * Validates that the provided shiftId belongs to this tenant and is ACTIVE.
     *
     * WHY check that the shift is ACTIVE?
     * A guard could scan a checkpoint referencing a shift that hasn't started
     * yet (SCHEDULED) or has already ended (COMPLETED).  Both are potential
     * fraud patterns:
     * - Pre-scan: scanning checkpoints before the shift starts to build up
     *   a log, then not showing up.
     * - Post-scan: claiming to have patrolled during a shift that ended.
     * Only ACTIVE shifts allow new checkpoint scans.
     */
    private void validateShiftForScan(TenantId tenantId, UUID shiftId, Checkpoint checkpoint) {
        var shift = shiftRepository.findActiveById(tenantId, shiftId)
                .orElseThrow(() -> new HandyFlowException(
                        "Shift not found or does not belong to your account",
                        HttpStatus.BAD_REQUEST, "INVALID_SHIFT"));

        if (shift.getStatus().name().equals("SCHEDULED")) {
            throw new HandyFlowException(
                    "Cannot scan checkpoints before your shift has started",
                    HttpStatus.BAD_REQUEST, "SHIFT_NOT_STARTED");
        }
        if (!shift.getStatus().name().equals("ACTIVE")) {
            throw new HandyFlowException(
                    "This shift is " + shift.getStatus() + " — checkpoint scans are not allowed",
                    HttpStatus.BAD_REQUEST, "SHIFT_NOT_ACTIVE");
        }
        // Optionally validate that the checkpoint's site matches the shift's site —
        // Phase 1 enhancement: prevents scanning a checkpoint from Site B while
        // on a shift for Site A.
    }

    /**
     * Enforces the per-checkpoint cooldown within a shift (fixes bug #18).
     *
     * WHY within a shift, not globally?
     * A 12-hour shift might legitimately require hourly parking lot checks
     * (same checkpoint scanned 5–6 times).  A global cooldown would block the
     * second hourly check.  The cooldown is per-shift so it catches speed-run
     * fraud within a single patrol round, while allowing legitimate re-scans
     * between rounds.
     *
     * The cooldown window is SCAN_COOLDOWN_SECONDS (default 60s).
     * If the last scan of this checkpoint in this shift is within the window,
     * we reject with a clear error message telling the guard how long to wait.
     */
    private void enforceCheckpointCooldown(UUID checkpointId, UUID shiftId) {
        logRepository.findLastScanInShift(checkpointId, shiftId).ifPresent(lastScan -> {
            Instant cooldownExpiry = lastScan.getScannedAt()
                    .plusSeconds(SCAN_COOLDOWN_SECONDS);
            if (Instant.now().isBefore(cooldownExpiry)) {
                long secondsRemaining = cooldownExpiry.getEpochSecond()
                        - Instant.now().getEpochSecond();
                throw new HandyFlowException(
                        "This checkpoint was scanned too recently. " +
                                "Please wait " + secondsRemaining + " more second(s) before scanning again.",
                        HttpStatus.TOO_MANY_REQUESTS, "SCAN_COOLDOWN");
            }
        });
    }
}
