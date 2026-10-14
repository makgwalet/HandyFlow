package za.co.handyflow.platform.auditor.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.auditor.application.internal.AuditShareService;
import za.co.handyflow.platform.auditor.domain.model.AuditShare;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auditors/shares")
@RequiredArgsConstructor
@Tag(name = "Auditor Sharing", description = "Choose which evidence and control exceptions external auditors can see")
public class AuditShareController {

    private final AuditShareService service;

    public record ShareRequest(@NotNull String itemType, @NotNull UUID itemId, String reason) {}
    public record WithdrawRequest(String reason) {}
    public record ShareResponse(UUID id, String itemType, UUID itemId, String reason, String sharedByName, Instant sharedAt,
                                Instant withdrawnAt, String withdrawnByName, String withdrawnReason, boolean live) {
        static ShareResponse of(AuditShare s) {
            return new ShareResponse(s.getId(), s.getItemType(), s.getItemId(), s.getReason(), s.getSharedByName(), s.getSharedAt(),
                    s.getWithdrawnAt(), s.getWithdrawnByName(), s.getWithdrawnReason(), s.isLive());
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('AUDIT_MANAGE','AUDIT_ADMIN')")
    @Operation(summary = "Everything shared with auditors, and what was withdrawn, newest first")
    public ResponseEntity<ApiResponse<List<ShareResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject()).stream().map(ShareResponse::of).toList()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('AUDIT_ADMIN')")
    @Operation(summary = "Show an evidence item or a control exception to external auditors")
    public ResponseEntity<ApiResponse<ShareResponse>> share(@RequestBody ShareRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Shared with auditors", ShareResponse.of(service.share(
                TenantContext.getTenantIdAsObject(), req.itemType(), req.itemId(), req.reason(),
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()))));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasAuthority('AUDIT_ADMIN')")
    @Operation(summary = "Stop showing an item to auditors; the record of the share stays")
    public ResponseEntity<ApiResponse<ShareResponse>> withdraw(@PathVariable UUID id, @RequestBody WithdrawRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Withdrawn", ShareResponse.of(service.withdraw(
                TenantContext.getTenantIdAsObject(), id, req.reason(), TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()))));
    }
}
