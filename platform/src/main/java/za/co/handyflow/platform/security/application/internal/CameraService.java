// security/application/internal/CameraService.java

package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.AlarmEvent;
import za.co.handyflow.platform.security.domain.model.Camera;
import za.co.handyflow.platform.security.domain.model.Site;
import za.co.handyflow.platform.security.domain.repository.CameraRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * CameraService — CCTV registry CRUD and motion event ingestion.
 *
 * Reuses the existing ControlRoomService.ingest() pipeline (Phase 3 Control
 * Room) for the actual alarm-event creation — a camera motion event is just
 * an AlarmEvent with source=CCTV_MOTION and cameraId linked. This is
 * deliberate: building a parallel triage/dispatch/SLA system for camera
 * events would duplicate everything the control room already does. The only
 * camera-specific work here is webhook authentication and updating the
 * camera's lastEventAt liveness signal.
 *
 * WHY a webhook secret per camera rather than relying on the tenant JWT?
 * The motion webhook endpoint (POST /cameras/motion-webhook) is called
 * directly by the camera/NVR or vendor cloud platform — it cannot carry a
 * user's JWT. Authentication instead happens by matching cameraId +
 * webhookSecret against the registered Camera row, same pattern the design
 * doc describes for alarm panel integrations generally.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CameraService {

    private final CameraRepository    cameraRepository;
    private final SiteRepository      siteRepository;
    private final ControlRoomService  controlRoomService;

    // ── Registry CRUD ──────────────────────────────────────────────────────────

    @Transactional
    public CameraResponse register(TenantId tenantId, RegisterCameraRequest req) {
        siteRepository.findActiveById(tenantId, req.siteId())
                .orElseThrow(() -> new ResourceNotFoundException("Site", req.siteId().toString()));

        Camera.CameraProvider provider = parseProvider(req.provider());

        Camera camera = Camera.register(
                tenantId, req.siteId(), req.name(), provider,
                req.connectionConfigJson(), null, req.notes());
        cameraRepository.save(camera);

        log.info("[Security] Camera registered id={} site={} provider={}",
                camera.getId(), req.siteId(), provider);

        return toResponse(camera, tenantId);
    }

    @Transactional(readOnly = true)
    public CameraResponse getById(TenantId tenantId, UUID id) {
        return toResponse(findCamera(tenantId, id), tenantId);
    }

    @Transactional(readOnly = true)
    public List<CameraResponse> getForSite(TenantId tenantId, UUID siteId) {
        return cameraRepository.findActiveBySite(tenantId, siteId).stream()
                .map(c -> toResponse(c, tenantId))
                .toList();
    }

    @Transactional
    public CameraResponse update(TenantId tenantId, UUID id, UpdateCameraRequest req) {
        Camera camera = findCamera(tenantId, id);
        camera.update(req.name(), parseProvider(req.provider()),
                req.connectionConfigJson(), req.notes());
        cameraRepository.save(camera);
        return toResponse(camera, tenantId);
    }

    @Transactional
    public CameraResponse markOffline(TenantId tenantId, UUID id) {
        Camera camera = findCamera(tenantId, id);
        camera.markOffline();
        cameraRepository.save(camera);
        return toResponse(camera, tenantId);
    }

    @Transactional
    public CameraResponse markActive(TenantId tenantId, UUID id) {
        Camera camera = findCamera(tenantId, id);
        camera.markActive();
        cameraRepository.save(camera);
        return toResponse(camera, tenantId);
    }

    @Transactional
    public CameraResponse decommission(TenantId tenantId, UUID id) {
        Camera camera = findCamera(tenantId, id);
        camera.decommission();
        cameraRepository.save(camera);
        return toResponse(camera, tenantId);
    }

    // ── Webhook Secret Management ──────────────────────────────────────────────

    /**
     * Generates (or regenerates) the webhook secret for a camera.
     * Returned exactly once — same one-time-reveal pattern as the guard PIN
     * reset flow (Phase 1.5). The supervisor must copy it into the camera/NVR's
     * webhook config immediately.
     */
    @Transactional
    public CameraWebhookSecretResponse generateWebhookSecret(TenantId tenantId, UUID id) {
        Camera camera = findCamera(tenantId, id);

        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        camera.rotateWebhookSecret(secret);
        cameraRepository.save(camera);

        log.info("[Security] Webhook secret rotated for camera={}", id);
        return new CameraWebhookSecretResponse(id, secret);
    }

    // ── Motion Event Ingestion (public webhook target) ────────────────────────

    /**
     * Verifies the webhook secret and ingests a motion event via the existing
     * control room pipeline. Tenant and site are derived from the matched
     * Camera row — never trusted from the request body, since this endpoint
     * has no JWT to authenticate the caller otherwise.
     */
    @Transactional
    public AlarmEvent ingestMotionEvent(CameraMotionWebhookRequest req) {
        Camera camera = cameraRepository.findById(req.cameraId())
                .orElseThrow(() -> new HandyFlowException(
                        "Unknown camera", HttpStatus.UNAUTHORIZED, "UNKNOWN_CAMERA"));

        if (camera.getWebhookSecret() == null
                || !camera.getWebhookSecret().equals(req.webhookSecret())) {
            log.warn("[Security] Camera webhook auth failed cameraId={}", req.cameraId());
            throw new HandyFlowException(
                    "Invalid webhook secret", HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK_SECRET");
        }

        if (!camera.isActive()) {
            log.warn("[Security] Motion event from non-active camera id={} status={}",
                    camera.getId(), camera.getStatus());
            throw new HandyFlowException(
                    "Camera is " + camera.getStatus(), HttpStatus.FORBIDDEN, "CAMERA_NOT_ACTIVE");
        }

        IngestAlarmEventRequest alarmReq = new IngestAlarmEventRequest(
                camera.getSiteId(), "CCTV_MOTION", req.rawPayload(),
                req.severity(), null, null, null, req.description());

        AlarmEvent event = controlRoomService.ingest(camera.getTenantId(), alarmReq);
        event.linkCamera(camera.getId());

        camera.recordEvent();
        cameraRepository.save(camera);

        log.info("[Security] Motion event ingested from camera={} eventId={}",
                camera.getId(), event.getId());

        return event;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Camera findCamera(TenantId tenantId, UUID id) {
        return cameraRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Camera", id.toString()));
    }

    private Camera.CameraProvider parseProvider(String raw) {
        if (raw == null) return Camera.CameraProvider.NONE;
        try {
            return Camera.CameraProvider.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException("Invalid provider: " + raw,
                    HttpStatus.BAD_REQUEST, "INVALID_PROVIDER");
        }
    }

    private CameraResponse toResponse(Camera c, TenantId tenantId) {
        String siteName = siteRepository.findActiveById(tenantId, c.getSiteId())
                .map(Site::getName).orElse("Unknown");
        return new CameraResponse(
                c.getId(), c.getSiteId(), siteName, c.getName(), c.getProvider().name(),
                c.getConnectionConfig(), c.getStatus().name(), c.getLastEventAt(),
                c.getNotes(), c.getCreatedAt());
    }
}
