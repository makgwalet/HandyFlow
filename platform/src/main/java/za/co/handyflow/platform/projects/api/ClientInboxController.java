package za.co.handyflow.platform.projects.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.projects.application.internal.clientportal.ClientPortalInboxService;
import za.co.handyflow.platform.projects.dto.ClientPortalDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** A project's inbox of client requests and client files. Every route carries the project id, so the project-access interceptor applies. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
@RequiredArgsConstructor
@Tag(name = "Projects - Client inbox", description = "Questions, change requests and files clients sent through the portal")
public class ClientInboxController {

    private final ClientPortalInboxService inbox;

    @GetMapping("/client-requests")
    @PreAuthorize("hasAuthority('PM_READ')")
    public ResponseEntity<ApiResponse<List<RequestView>>> requests(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success(inbox.requests(tenant(), projectId)));
    }

    @PostMapping("/client-requests/{requestId}/decide")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "ACCEPT (a question becomes a draft RFI, a change request a draft change order), ANSWER or DECLINE, with a reply to the client")
    public ResponseEntity<ApiResponse<RequestView>> decide(@PathVariable UUID projectId, @PathVariable UUID requestId, @Valid @RequestBody DecideRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Request updated",
                inbox.decide(tenant(), projectId, requestId, req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/client-uploads")
    @PreAuthorize("hasAuthority('PM_READ')")
    public ResponseEntity<ApiResponse<List<UploadView>>> uploads(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success(inbox.uploads(tenant(), projectId)));
    }

    @GetMapping("/client-uploads/{uploadId}/download")
    @PreAuthorize("hasAuthority('PM_READ')")
    public ResponseEntity<byte[]> download(@PathVariable UUID projectId, @PathVariable UUID uploadId) {
        return DocumentDownloads.response(inbox.downloadUpload(tenant(), projectId, uploadId));
    }

    @PostMapping("/client-uploads/{uploadId}/accept")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Add the file to the document register (checked and scanned again)")
    public ResponseEntity<ApiResponse<UploadView>> accept(@PathVariable UUID projectId, @PathVariable UUID uploadId, @Valid @RequestBody AcceptUpload req) {
        return ResponseEntity.ok(ApiResponse.success("File added to the register",
                inbox.acceptUpload(tenant(), projectId, uploadId, req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/client-uploads/{uploadId}/reject")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    public ResponseEntity<ApiResponse<UploadView>> reject(@PathVariable UUID projectId, @PathVariable UUID uploadId, @Valid @RequestBody RejectUpload req) {
        return ResponseEntity.ok(ApiResponse.success("File not accepted",
                inbox.rejectUpload(tenant(), projectId, uploadId, req.reason(), TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
}
