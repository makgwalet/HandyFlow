package za.co.handyflow.platform.auditor.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.auditor.application.internal.AuditQueryService;
import za.co.handyflow.platform.auditor.application.internal.EvidenceRequestService.Actor;
import za.co.handyflow.platform.auditor.application.internal.EvidenceRequestService.Scope;
import za.co.handyflow.platform.auditor.dto.AuditQueryDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** The business's side of audit queries and external review notes. */
@RestController
@RequestMapping("/api/v1/auditors/queries")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('AUDIT_MANAGE','AUDIT_ADMIN')")
@Tag(name = "Audit queries", description = "Queries and review notes raised by external auditors")
public class AuditQueryController {

    private final AuditQueryService service;

    private static Actor me() { return Actor.staff(TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()); }

    @GetMapping
    public ResponseEntity<ApiResponse<List<QueryView>>> list() {
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), Scope.all(), me())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<QueryView>> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.detail(TenantContext.getTenantIdAsObject(), Scope.all(), me(), id)));
    }

    @PostMapping("/{id}/answer")
    public ResponseEntity<ApiResponse<QueryView>> answer(@PathVariable UUID id, @RequestBody TextRequest req) {
        return ResponseEntity.ok(ApiResponse.success(service.answer(TenantContext.getTenantIdAsObject(), Scope.all(), me(), id, req.text())));
    }

    @PostMapping("/{id}/link")
    public ResponseEntity<ApiResponse<QueryView>> link(@PathVariable UUID id, @RequestBody LinkRequest req) {
        return ResponseEntity.ok(ApiResponse.success(service.link(TenantContext.getTenantIdAsObject(), me(), id, req.findingId())));
    }
}
