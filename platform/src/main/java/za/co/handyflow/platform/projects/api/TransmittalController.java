package za.co.handyflow.platform.projects.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.projects.application.internal.TransmittalAccessService;
import za.co.handyflow.platform.projects.application.internal.TransmittalService;
import za.co.handyflow.platform.projects.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Transmittal register — a record of which revisions were sent to whom, with per-recipient secure links. Project-first URLs, so project access rules apply automatically. */
@Validated
@RestController
@RequestMapping("/api/v1/projects/{projectId}/transmittals")
@RequiredArgsConstructor
@Tag(name = "Transmittals", description = "Record of documents issued to recipients")
public class TransmittalController {

    private final TransmittalService transmittalService;
    private final TransmittalAccessService accessService;

    @GetMapping
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Transmittal register, newest first")
    public ResponseEntity<ApiResponse<List<TransmittalResponse>>> list(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                transmittalService.list(TenantContext.getTenantIdAsObject(), projectId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "One transmittal with the exact revisions it covered")
    public ResponseEntity<ApiResponse<TransmittalResponse>> get(@PathVariable UUID projectId, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                transmittalService.get(TenantContext.getTenantIdAsObject(), projectId, id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Record a transmittal. Every document must be live (CURRENT / FOR_REVIEW / APPROVED) — otherwise the whole request is refused. Each recipient with an email address is emailed their own secure link; nothing is attached.")
    public ResponseEntity<ApiResponse<TransmittalResponse>> create(
            @PathVariable UUID projectId, @Valid @RequestBody CreateTransmittalRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Transmittal recorded",
                transmittalService.create(TenantContext.getTenantIdAsObject(), projectId, req,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Record that the recipient acknowledged receipt (once only)")
    public ResponseEntity<ApiResponse<TransmittalResponse>> acknowledge(
            @PathVariable UUID projectId, @PathVariable UUID id,
            @Valid @RequestBody AcknowledgeTransmittalRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Acknowledgement recorded",
                transmittalService.acknowledge(TenantContext.getTenantIdAsObject(), projectId, id, req.acknowledgedBy())));
    }

    // ── Recipient access: links, revocation, audit ────────────────────────────

    @GetMapping("/{id}/recipients")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Who has a link to this transmittal, and what state each link is in")
    public ResponseEntity<ApiResponse<List<TransmittalRecipientResponse>>> recipients(
            @PathVariable UUID projectId, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                accessService.recipients(TenantContext.getTenantIdAsObject(), projectId, id)));
    }

    @PostMapping("/{id}/recipients")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Give one more recipient their own secure link (emailed; never attached)")
    public ResponseEntity<ApiResponse<TransmittalRecipientResponse>> addRecipient(
            @PathVariable UUID projectId, @PathVariable UUID id,
            @Valid @RequestBody TransmittalRecipientRequest req, @RequestParam(required = false) Integer expiryDays,
            @RequestParam(defaultValue = "false") boolean attachFiles) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Recipient added",
                accessService.addRecipient(TenantContext.getTenantIdAsObject(), projectId, id, req, expiryDays, attachFiles,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/{id}/legacy-link")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "For a transmittal recorded before secure links existed: send its recorded recipient a link now")
    public ResponseEntity<ApiResponse<TransmittalRecipientResponse>> legacyLink(
            @PathVariable UUID projectId, @PathVariable UUID id, @RequestParam(required = false) Integer expiryDays) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Link sent",
                accessService.issueLegacyRecipient(TenantContext.getTenantIdAsObject(), projectId, id, expiryDays,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/{id}/recipients/{recipientId}/reissue")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Send a fresh link. The previous link and any open session stop working.")
    public ResponseEntity<ApiResponse<TransmittalRecipientResponse>> reissue(
            @PathVariable UUID projectId, @PathVariable UUID id, @PathVariable UUID recipientId,
            @Valid @RequestBody(required = false) ReissueLinkRequest req) {
        return ResponseEntity.ok(ApiResponse.success("New link sent",
                accessService.reissue(TenantContext.getTenantIdAsObject(), projectId, id, recipientId,
                        req == null ? null : req.expiryDays(),
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/{id}/recipients/{recipientId}/revoke")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Cut off a recipient's access immediately. Only the sender or a document controller.")
    public ResponseEntity<ApiResponse<TransmittalRecipientResponse>> revoke(
            @PathVariable UUID projectId, @PathVariable UUID id, @PathVariable UUID recipientId,
            @Valid @RequestBody RevokeRecipientRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Access revoked",
                accessService.revoke(TenantContext.getTenantIdAsObject(), projectId, id, recipientId, req.reason(),
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/{id}/access-log")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Everything done with or to this transmittal's links, newest first")
    public ResponseEntity<ApiResponse<List<TransmittalAccessLogResponse>>> accessLog(
            @PathVariable UUID projectId, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                accessService.accessLog(TenantContext.getTenantIdAsObject(), projectId, id)));
    }
}
