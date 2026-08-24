// security/api/SiteController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.CheckpointQrPdfService;
import za.co.handyflow.platform.security.application.internal.SiteService;
import za.co.handyflow.platform.security.domain.model.Checkpoint;
import za.co.handyflow.platform.security.domain.model.Site;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Map;
import java.util.UUID;

/**
 * FIX: backlog 1.7 — see GuardController's own Javadoc for the full
 * rationale; this is the mirror fix for sites/checkpoints. Same tiers:
 * SECURITY_READ, SECURITY_MANAGE for routine writes (including
 * QR-secret regeneration and contract termination — neither invented as
 * a stricter tier, matching this session's own discipline of not adding
 * an ADMIN gate beyond what a finding explicitly calls for), and
 * SECURITY_ADMIN reserved for delete specifically.
 */
@RestController
@RequestMapping("/api/v1/security/sites")
@RequiredArgsConstructor
@Tag(name = "Security - Sites", description = "Client site management with QR/NFC checkpoints")
public class SiteController {

    private final SiteService           siteService;
    private final CheckpointQrPdfService checkpointQrPdfService;
    private final FeatureGuard          featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "List all active sites — checkpoints not included in list view")
    public ResponseEntity<ApiResponse<Page<SiteResponse>>> getSites(
            @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(
                siteService.getSites(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Get site detail with all checkpoints and their QR/NFC/BLE identifiers")
    public ResponseEntity<ApiResponse<SiteResponse>> getSite(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(
                siteService.getSite(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Register a new client site")
    public ResponseEntity<ApiResponse<SiteResponse>> createSite(
            @Valid @RequestBody CreateSiteRequest request) {
        featureGuard.requireModule("security");
        var site = siteService.createSite(TenantContext.getTenantIdAsObject(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Site created", site));
    }

    @PostMapping("/{id}/checkpoints")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Add a checkpoint to a site — generates unique QR code automatically")
    public ResponseEntity<ApiResponse<SiteResponse>> addCheckpoint(
            @PathVariable UUID id,
            @Valid @RequestBody CreateCheckpointRequest request) {
        featureGuard.requireModule("security");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Checkpoint added",
                        siteService.addCheckpoint(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SECURITY_ADMIN')")
    @Operation(summary = "Soft-delete a site (preserves shift/incident/scan history)")
    public ResponseEntity<ApiResponse<Void>> deleteSite(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        UUID deletedBy = TenantContext.getCurrentUserId();
        siteService.deleteSite(TenantContext.getTenantIdAsObject(), id, deletedBy);
        return ResponseEntity.ok(ApiResponse.success("Site deleted", null));
    }

    @PostMapping("/{id}/terminate")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(
            summary = "Terminate site contract",
            description = "Sets contractStatus=TERMINATED, records terminationReason and terminatedAt timestamp."
    )
    public ResponseEntity<ApiResponse<SiteResponse>> terminateSite(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        featureGuard.requireModule("security");
        UUID terminatedBy = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Contract terminated",
                siteService.terminateSite(TenantContext.getTenantIdAsObject(), id,
                        body.get("reason"), terminatedBy)));
    }

    // ── QR signing rollout (V215) ──────────────────────────────────────────────

    @GetMapping("/{siteId}/checkpoints/{checkpointId}/qr-payload")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(
            summary = "Get the signed QR payload for a checkpoint",
            description = """
            Returns the "{checkpointId}:{siteId}:{signature}" string that
            should be encoded into this checkpoint's physical/displayed QR
            image. Prefer the PDF endpoints below for an actual printable
            QR image -- this raw-string endpoint is mainly useful for
            debugging or a custom print pipeline.
            """)
    public ResponseEntity<ApiResponse<QrPayloadResponse>> getCheckpointQrPayload(
            @PathVariable UUID siteId, @PathVariable UUID checkpointId) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(
                siteService.getCheckpointQrPayload(
                        TenantContext.getTenantIdAsObject(), siteId, checkpointId)));
    }

    @PatchMapping("/{id}/qr-enforcement")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(
            summary = "Enable or disable QR HMAC signature enforcement for this site",
            description = """
            Per-site flag -- each site enables this independently, once ITS
            checkpoints have actually been reprinted with signed payloads.
            Enabling this before reprinting will cause every QR scan at
            this site to fail immediately with INVALID_QR_FORMAT.
            """)
    public ResponseEntity<ApiResponse<Void>> setQrEnforcement(
            @PathVariable UUID id, @Valid @RequestBody SetQrEnforcementRequest req) {
        featureGuard.requireModule("security");
        siteService.setQrEnforcement(TenantContext.getTenantIdAsObject(), id, req.requireSignedQr());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── QR regeneration + printing (V217) ─────────────────────────────────────

    @PostMapping("/{siteId}/checkpoints/{checkpointId}/qr-secret/regenerate")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(
            summary = "Regenerate a checkpoint's QR code (procedural rotation or compromise response)",
            description = """
            Rotates BOTH the legacy bare-UUID code and the signing secret
            for THIS checkpoint only -- every other checkpoint at the site
            is unaffected (V217 moved QR signing to a per-checkpoint
            secret specifically so a single compromised sticker doesn't
            force a whole-site reprint). The old physical sticker for this
            checkpoint stops working the moment this call succeeds --
            immediately reprint via the PDF endpoint below.
            """)
    public ResponseEntity<ApiResponse<QrPayloadResponse>> regenerateCheckpointQr(
            @PathVariable UUID siteId, @PathVariable UUID checkpointId) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(
                siteService.regenerateCheckpointQr(
                        TenantContext.getTenantIdAsObject(), siteId, checkpointId)));
    }

    @GetMapping("/{siteId}/checkpoints/{checkpointId}/qr-image")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(
            summary = "Raw QR code image for one checkpoint (PNG, for inline display)",
            description = "Unbranded, no header/footer -- just the code itself, for the admin " +
                    "UI to show inline (e.g. SitesTab's checkpoint tiles). Use the qr-pdf " +
                    "endpoint instead for anything meant to be printed.")
    public ResponseEntity<byte[]> getCheckpointQrImage(
            @PathVariable UUID siteId, @PathVariable UUID checkpointId) {
        featureGuard.requireModule("security");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        Site site = siteService.getSiteWithCheckpointsForPrinting(tenantId, siteId);
        Checkpoint checkpoint = site.getCheckpoints().stream()
                .filter(c -> c.getId().equals(checkpointId) && c.isActive())
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Checkpoint", checkpointId.toString()));
        byte[] png = checkpointQrPdfService.qrImagePng(checkpoint);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setContentLength(png.length);
        return ResponseEntity.ok().headers(headers).body(png);
    }

    @GetMapping("/{siteId}/checkpoints/{checkpointId}/qr-pdf")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(
            summary = "Printable QR code for one checkpoint (PDF)",
            description = "Use right after regenerating a compromised checkpoint's code, " +
                    "or to reprint a single sticker without reprinting the whole site.")
    public ResponseEntity<byte[]> getCheckpointQrPdf(
            @PathVariable UUID siteId, @PathVariable UUID checkpointId) {
        featureGuard.requireModule("security");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        Site site = siteService.getSiteWithCheckpointsForPrinting(tenantId, siteId);
        Checkpoint checkpoint = site.getCheckpoints().stream()
                .filter(c -> c.getId().equals(checkpointId) && c.isActive())
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Checkpoint", checkpointId.toString()));
        byte[] pdf = checkpointQrPdfService.singleCheckpointPdf(checkpoint, tenantId);
        return pdfResponse(pdf, "qr-" + checkpoint.getName().replaceAll("[^a-zA-Z0-9]", "-") + ".pdf");
    }

    @GetMapping("/{siteId}/checkpoints/qr-sheet")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(
            summary = "Printable QR sheet for every checkpoint at a site (PDF)",
            description = "One sheet, all active checkpoints in a grid — the realistic " +
                    "workflow: print once, cut out each QR, mount at its checkpoint.")
    public ResponseEntity<byte[]> getSiteQrSheet(@PathVariable UUID siteId) {
        featureGuard.requireModule("security");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        Site site = siteService.getSiteWithCheckpointsForPrinting(tenantId, siteId);
        byte[] pdf = checkpointQrPdfService.siteQrSheetPdf(site, tenantId);
        return pdfResponse(pdf, "qr-sheet-" + site.getName().replaceAll("[^a-zA-Z0-9]", "-") + ".pdf");
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(pdf);
    }
}