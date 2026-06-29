// security/application/internal/CheckpointScanService.java

package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.Checkpoint;
import za.co.handyflow.platform.security.domain.model.CheckpointLog;
import za.co.handyflow.platform.security.domain.model.Site;
import za.co.handyflow.platform.security.domain.repository.CheckpointLogRepository;
import za.co.handyflow.platform.security.domain.repository.CheckpointRepository;
import za.co.handyflow.platform.security.domain.repository.ShiftRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.security.dto.ScanRequest;
import za.co.handyflow.platform.security.dto.ScanResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * CheckpointScanService — fixes bugs #6, #7, #11, #13, #18.
 *
 * Bug #13: guardId resolved from authenticated session, NOT the request body.
 * Bug #7:  HMAC-SHA256 QR verification using site.qrSecret (ENFORCE_QR_HMAC flag
 *          is false until guard app ships signed QRs).
 * Bug #6:  scanType routing — NFC/BLE have dedicated lookup methods.
 * Bug #11: All checkpoint lookups include tenantId scoping.
 * Bug #18: 60s cooldown per checkpoint per shift.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckpointScanService {

    static final int     SCAN_COOLDOWN_SECONDS = 60;
    // Set to true once the guard app ships HMAC-signed QR payloads (Phase 1).
    static final boolean ENFORCE_QR_HMAC       = false;

    private final CheckpointRepository    checkpointRepository;
    private final CheckpointLogRepository logRepository;
    private final ShiftRepository         shiftRepository;
    private final SiteRepository          siteRepository;

    /**
     * @param authenticatedGuardId Resolved from HTTP session by the controller.
     *                             The request body's guardId is ignored (bug #13 fix).
     */
    @Transactional
    public ScanResponse scan(TenantId tenantId, ScanRequest req, UUID authenticatedGuardId) {
        String scanType = req.scanType() != null ? req.scanType().toUpperCase() : "QR";

        Checkpoint checkpoint = resolveCheckpoint(tenantId, req, scanType);

        if ("QR".equals(scanType) && ENFORCE_QR_HMAC) {
            verifyQrHmac(checkpoint, req.qrCode());
        }

        if (req.shiftId() != null) {
            validateShiftForScan(tenantId, req.shiftId(), checkpoint, authenticatedGuardId);
            enforceCheckpointCooldown(checkpoint.getId(), req.shiftId());
        }

        CheckpointLog entry = CheckpointLog.create(
                tenantId,
                checkpoint.getId(),
                authenticatedGuardId,   // session identity, NOT req.guardId()
                req.shiftId(),
                req.latitude(),
                req.longitude(),
                scanType                // persisted for audit trail
        );
        logRepository.save(entry);

        log.info("[Security] Checkpoint scanned type={} checkpoint='{}' guard={} shift={}",
                scanType, checkpoint.getName(), authenticatedGuardId, req.shiftId());

        return new ScanResponse(
                entry.getId(),
                checkpoint.getName(),
                checkpoint.getSite().getName(),
                entry.getScannedAt()
        );
    }

    // ── HMAC QR verification (bug #7) ─────────────────────────────────────────

    /**
     * Wire format: "{checkpointId}:{siteId}:{Base64url(HMAC-SHA256(secret, payload))}"
     * The guard app generates QR images from this string; scanning returns this string.
     * Phase 1: add timestamp segment for expiry window.
     */
    private void verifyQrHmac(Checkpoint checkpoint, String qrCode) {
        String[] parts = qrCode != null ? qrCode.split(":") : new String[0];
        if (parts.length < 3) {
            throw new HandyFlowException(
                    "QR code format is invalid — please regenerate the QR for this checkpoint",
                    HttpStatus.BAD_REQUEST, "INVALID_QR_FORMAT");
        }
        String payload   = parts[0] + ":" + parts[1];
        String signature = parts[2];

        String secret = siteRepository.findById(checkpoint.getSite().getId())
                .map(Site::getQrSecret)
                .orElseThrow(() -> new HandyFlowException(
                        "Site configuration error", HttpStatus.INTERNAL_SERVER_ERROR, "SITE_NOT_FOUND"));
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
            String expected = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes()));
            if (!expected.equals(signature)) {
                log.warn("[Security] QR HMAC mismatch checkpoint={}", checkpoint.getId());
                throw new HandyFlowException(
                        "QR code signature is invalid — scan the original printed QR",
                        HttpStatus.UNAUTHORIZED, "INVALID_QR_SIGNATURE");
            }
        } catch (HandyFlowException e) {
            throw e;
        } catch (Exception e) {
            throw new HandyFlowException("QR verification failed",
                    HttpStatus.INTERNAL_SERVER_ERROR, "HMAC_ERROR");
        }
    }

    /** Generates a signed QR payload for a checkpoint. Called by Phase 1 QR generation endpoint. */
    public String generateQrPayload(Checkpoint checkpoint, String siteSecret) {
        try {
            String payload = checkpoint.getId() + ":" + checkpoint.getSite().getId();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(siteSecret.getBytes(), "HmacSHA256"));
            String sig = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes()));
            return payload + ":" + sig;
        } catch (Exception e) {
            throw new RuntimeException("QR generation failed", e);
        }
    }

    // ── Checkpoint resolution ──────────────────────────────────────────────────

    private Checkpoint resolveCheckpoint(TenantId tenantId, ScanRequest req, String scanType) {
        return switch (scanType) {
            case "QR" -> {
                if (req.qrCode() == null || req.qrCode().isBlank()) throw new HandyFlowException(
                        "QR code is required for QR scan type", HttpStatus.BAD_REQUEST, "MISSING_QR_CODE");
                // HMAC format: "{checkpointId}:{siteId}:{sig}" — try ID lookup first
                String lookupCode = req.qrCode().contains(":")
                        ? req.qrCode().split(":")[0] : req.qrCode();
                yield tryUuidLookup(tenantId, lookupCode)
                        .or(() -> checkpointRepository.findByQrCode(tenantId, req.qrCode()))
                        .orElseThrow(() -> new HandyFlowException(
                                "Invalid or expired QR code", HttpStatus.BAD_REQUEST, "INVALID_QR_CODE"));
            }
            case "NFC" -> {
                if (req.nfcTagId() == null || req.nfcTagId().isBlank()) throw new HandyFlowException(
                        "NFC tag ID is required", HttpStatus.BAD_REQUEST, "MISSING_NFC_ID");
                yield checkpointRepository.findByNfcTagUid(tenantId, req.nfcTagId())
                        .orElseThrow(() -> new HandyFlowException(
                                "NFC tag not registered", HttpStatus.BAD_REQUEST, "INVALID_NFC_TAG"));
            }
            case "BLE" -> {
                if (req.bleBeaconId() == null || req.bleBeaconId().isBlank()) throw new HandyFlowException(
                        "BLE beacon ID is required", HttpStatus.BAD_REQUEST, "MISSING_BLE_ID");
                yield checkpointRepository.findByBleBeaconId(tenantId, req.bleBeaconId())
                        .orElseThrow(() -> new HandyFlowException(
                                "BLE beacon not registered", HttpStatus.BAD_REQUEST, "INVALID_BLE_BEACON"));
            }
            case "GPS_PING", "MANUAL" -> throw new HandyFlowException(
                    "Use the shift location endpoint for " + scanType + " (Phase 1)",
                    HttpStatus.BAD_REQUEST, "UNSUPPORTED_SCAN_TYPE_FOR_CHECKPOINT");
            default -> throw new HandyFlowException(
                    "Unknown scan type: " + scanType, HttpStatus.BAD_REQUEST, "UNKNOWN_SCAN_TYPE");
        };
    }

    private Optional<Checkpoint> tryUuidLookup(TenantId tenantId, String maybeUuid) {
        try {
            UUID id = UUID.fromString(maybeUuid);
            return checkpointRepository.findById(id)
                    .filter(c -> c.getSite().getTenantId().equals(tenantId) && c.isActive());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    // ── Shift validation ───────────────────────────────────────────────────────

    private void validateShiftForScan(TenantId tenantId, UUID shiftId,
                                      Checkpoint checkpoint, UUID guardId) {
        var shift = shiftRepository.findActiveById(tenantId, shiftId)
                .orElseThrow(() -> new HandyFlowException(
                        "Shift not found", HttpStatus.BAD_REQUEST, "INVALID_SHIFT"));

        // Guard must own the shift — catches the ghost-guard fraud pattern
        if (!shift.getGuardId().equals(guardId)) {
            log.warn("[Security] Guard {} tried to scan on shift owned by guard {} — rejected",
                    guardId, shift.getGuardId());
            throw new HandyFlowException(
                    "This shift is not assigned to you", HttpStatus.FORBIDDEN, "SHIFT_NOT_ASSIGNED");
        }
        if (shift.getStatus().name().equals("SCHEDULED")) throw new HandyFlowException(
                "Cannot scan before your shift has started", HttpStatus.BAD_REQUEST, "SHIFT_NOT_STARTED");
        if (!shift.getStatus().name().equals("ACTIVE")) throw new HandyFlowException(
                "Shift is " + shift.getStatus(), HttpStatus.BAD_REQUEST, "SHIFT_NOT_ACTIVE");
    }

    // ── Cooldown ───────────────────────────────────────────────────────────────

    private void enforceCheckpointCooldown(UUID checkpointId, UUID shiftId) {
        logRepository.findLastScanInShift(checkpointId, shiftId).ifPresent(last -> {
            Instant expiry = last.getScannedAt().plusSeconds(SCAN_COOLDOWN_SECONDS);
            if (Instant.now().isBefore(expiry)) {
                long s = expiry.getEpochSecond() - Instant.now().getEpochSecond();
                throw new HandyFlowException(
                        "Checkpoint scanned too recently. Wait " + s + "s.",
                        HttpStatus.TOO_MANY_REQUESTS, "SCAN_COOLDOWN");
            }
        });
    }

    // ── Haversine (used by IncidentService for bug #21) ────────────────────────

    /**
     * Calculates the distance in metres between two GPS coordinates (Haversine formula).
     * Accurate to ~0.5% for distances under 20km — sufficient for site proximity checks.
     */
    public static double haversineMetres(BigDecimal lat1, BigDecimal lon1,
                                         BigDecimal lat2, BigDecimal lon2) {
        final double R = 6_371_000;
        double φ1 = Math.toRadians(lat1.doubleValue());
        double φ2 = Math.toRadians(lat2.doubleValue());
        double Δφ = Math.toRadians(lat2.subtract(lat1).doubleValue());
        double Δλ = Math.toRadians(lon2.subtract(lon1).doubleValue());
        double a = Math.sin(Δφ/2)*Math.sin(Δφ/2) + Math.cos(φ1)*Math.cos(φ2)*Math.sin(Δλ/2)*Math.sin(Δλ/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    }
}
