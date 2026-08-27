// security/api/CameraController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.security.application.internal.CameraService;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

@Tag(name = "Security - CCTV")
@RestController
@RequestMapping("/api/v1/security/cameras")
@RequiredArgsConstructor
public class CameraController {

    private final CameraService cameraService;

    // ── Registry CRUD ──────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "List all active (non-decommissioned) cameras for this tenant, paginated")
    public ResponseEntity<ApiResponse<Page<CameraResponse>>> getAll(
            @PageableDefault(size = 100) Pageable pageable) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(cameraService.getAllForTenant(tenantId, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Get a single camera's registry record")
    public ResponseEntity<ApiResponse<CameraResponse>> getById(@PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(cameraService.getById(tenantId, id)));
    }

    @GetMapping("/site/{siteId}")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "List all active cameras for a site")
    public ResponseEntity<ApiResponse<List<CameraResponse>>> getForSite(
            @PathVariable UUID siteId) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(cameraService.getForSite(tenantId, siteId)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(
            summary = "Register a new camera",
            description = "Does not auto-generate a webhook secret — call " +
                    "POST /{id}/webhook-secret afterward to get one.")
    public ResponseEntity<ApiResponse<CameraResponse>> register(
            @Valid @RequestBody RegisterCameraRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(cameraService.register(tenantId, req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Update a camera's registry details")
    public ResponseEntity<ApiResponse<CameraResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateCameraRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(cameraService.update(tenantId, id, req)));
    }

    @PostMapping("/{id}/offline")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Mark a camera offline (manual — not auto-detected from missed events)")
    public ResponseEntity<ApiResponse<CameraResponse>> markOffline(@PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(cameraService.markOffline(tenantId, id)));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Reactivate an offline camera")
    public ResponseEntity<ApiResponse<CameraResponse>> markActive(@PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(cameraService.markActive(tenantId, id)));
    }

    @PostMapping("/{id}/decommission")
    @PreAuthorize("hasAuthority('SECURITY_ADMIN')")
    @Operation(summary = "Permanently retire a camera from the registry")
    public ResponseEntity<ApiResponse<CameraResponse>> decommission(@PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(cameraService.decommission(tenantId, id)));
    }

    // ── Webhook Secret ─────────────────────────────────────────────────────────

    @PostMapping("/{id}/webhook-secret")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(
            summary = "Generate or regenerate a camera's webhook secret",
            description = "Returned exactly once — copy it into the camera/NVR's webhook " +
                    "config immediately. Regenerating invalidates the previous secret.")
    public ResponseEntity<ApiResponse<CameraWebhookSecretResponse>> generateWebhookSecret(
            @PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                cameraService.generateWebhookSecret(tenantId, id)));
    }

    // ── Public Motion Webhook ──────────────────────────────────────────────────

    @PostMapping("/motion-webhook")
    @Operation(
            summary = "Camera motion event webhook (public — no JWT)",
            description = """
            Called directly by the camera/NVR or vendor cloud platform.
            Authenticates via cameraId + webhookSecret matching a registered
            camera — no tenant JWT is possible here since the caller is a
            device, not a logged-in user. Tenant and site are derived from
            the matched camera, never trusted from the request body.

            On success, creates an AlarmEvent (source=CCTV_MOTION) via the
            existing control room pipeline and updates the camera's
            lastEventAt liveness signal.

            MUST be added to SecurityConfig's permitAll() list — see class
            javadoc for the exact path to add.
            """)
    public ResponseEntity<ApiResponse<Void>> motionWebhook(
            @Valid @RequestBody CameraMotionWebhookRequest req) {
        cameraService.ingestMotionEvent(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }
}