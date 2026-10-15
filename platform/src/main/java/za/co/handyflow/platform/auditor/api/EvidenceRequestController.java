package za.co.handyflow.platform.auditor.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.auditor.application.internal.EvidenceRequestService;
import za.co.handyflow.platform.auditor.application.internal.EvidenceRequestService.Actor;
import za.co.handyflow.platform.auditor.application.internal.EvidenceRequestService.Scope;
import za.co.handyflow.platform.auditor.dto.EvidenceRequestDtos.*;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** The business's side of evidence requests. Files are attached with the shared evidence endpoint (sourceModule=auditor, relatedEntityType=AUDIT_EVIDENCE_REQUEST). */
@RestController
@RequestMapping("/api/v1/auditors/evidence-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('AUDIT_MANAGE','AUDIT_ADMIN')")
@Tag(name = "Evidence requests", description = "Requests for evidence between the business and its external auditors")
public class EvidenceRequestController {

    private final EvidenceRequestService service;

    private static Actor me() { return Actor.staff(TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()); }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RequestView>>> list() {
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), Scope.all())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RequestView>> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.detail(TenantContext.getTenantIdAsObject(), Scope.all(), id)));
    }

    @PostMapping
    @Operation(summary = "Raise an evidence request on behalf of the business")
    public ResponseEntity<ApiResponse<RequestView>> create(@RequestBody CreateRequest req) {
        return ResponseEntity.ok(ApiResponse.success(service.create(TenantContext.getTenantIdAsObject(), Scope.all(), me(), req)));
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<ApiResponse<RequestView>> assign(@PathVariable UUID id, @RequestBody AssignRequest req) {
        return ResponseEntity.ok(ApiResponse.success(service.assign(TenantContext.getTenantIdAsObject(), Scope.all(), me(), id, req.assignedTo(), req.dueDate())));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<RequestView>> comment(@PathVariable UUID id, @RequestBody TextRequest req) {
        return ResponseEntity.ok(ApiResponse.success(service.comment(TenantContext.getTenantIdAsObject(), Scope.all(), me(), id, req.text())));
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Send the files attached since the last round to the auditor")
    public ResponseEntity<ApiResponse<RequestView>> submit(@PathVariable UUID id, @RequestBody TextRequest req) {
        return ResponseEntity.ok(ApiResponse.success(service.submit(TenantContext.getTenantIdAsObject(), Scope.all(), me(), id, req.text())));
    }

    @PostMapping("/{id}/not-available")
    @Operation(summary = "Tell the auditor the item cannot be provided, and why")
    public ResponseEntity<ApiResponse<RequestView>> notAvailable(@PathVariable UUID id, @RequestBody TextRequest req) {
        return ResponseEntity.ok(ApiResponse.success(service.notAvailable(TenantContext.getTenantIdAsObject(), Scope.all(), me(), id, req.text())));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<RequestView>> cancel(@PathVariable UUID id, @RequestBody TextRequest req) {
        return ResponseEntity.ok(ApiResponse.success(service.cancel(TenantContext.getTenantIdAsObject(), Scope.all(), me(), id, req.text())));
    }

    @GetMapping("/{id}/files/{evidenceId}")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, @PathVariable UUID evidenceId) {
        EvidenceFacade.DownloadedEvidence f = service.download(TenantContext.getTenantIdAsObject(), Scope.all(), id, evidenceId);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + f.fileName() + "\"")
                .contentType(MediaType.parseMediaType(f.contentType())).body(f.content());
    }
}
