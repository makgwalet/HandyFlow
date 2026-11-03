package za.co.handyflow.platform.facilitiesmanagement.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmSharingService;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmSharingDtos.*;
import za.co.handyflow.platform.secureaccess.application.SecureAccessDtos.AccessLogView;
import za.co.handyflow.platform.secureaccess.application.SecureAccessDtos.RecipientView;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Secure sharing of a completed work order's files with the client's people or a third party: each gets their own expiring link
 * and confirms their email with a code. Looking needs FM read rights; giving or taking away access needs FM management rights.
 */
@RestController
@RequestMapping("/api/v1/facilitiesmanagement/sharing")
@RequiredArgsConstructor
public class FmSharingController {

    private static final String READ = "hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')";
    private static final String CHANGE = "hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')";

    private final FmSharingService sharing;
    private final FeatureGuard featureGuard;

    @GetMapping("/work-orders")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<WorkOrderRow>>> completed(@RequestParam(required = false) String q,
                                                                     @RequestParam(required = false) Integer limit) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(sharing.completed(TenantContext.getTenantIdAsObject(), q, limit)));
    }

    @GetMapping("/work-orders/{workOrderId}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<SharingView>> view(@PathVariable UUID workOrderId) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(sharing.view(TenantContext.getTenantIdAsObject(), workOrderId)));
    }

    @PostMapping("/work-orders/{workOrderId}/recipients")
    @PreAuthorize(CHANGE)
    public ResponseEntity<ApiResponse<RecipientView>> add(@PathVariable UUID workOrderId, @RequestBody RecipientRequest req) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Link sent",
                sharing.addRecipient(TenantContext.getTenantIdAsObject(), workOrderId, req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/work-orders/{workOrderId}/recipients/{recipientId}/reissue")
    @PreAuthorize(CHANGE)
    public ResponseEntity<ApiResponse<RecipientView>> reissue(@PathVariable UUID workOrderId, @PathVariable UUID recipientId,
                                                              @RequestBody(required = false) ReissueRequest req) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("New link sent; the previous link no longer works",
                sharing.reissue(TenantContext.getTenantIdAsObject(), workOrderId, recipientId, req == null ? null : req.expiryDays(),
                        TenantContext.getCurrentUserName())));
    }

    @PostMapping("/work-orders/{workOrderId}/recipients/{recipientId}/revoke")
    @PreAuthorize(CHANGE)
    public ResponseEntity<ApiResponse<RecipientView>> revoke(@PathVariable UUID workOrderId, @PathVariable UUID recipientId,
                                                             @RequestBody RevokeRequest req) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Access revoked",
                sharing.revoke(TenantContext.getTenantIdAsObject(), workOrderId, recipientId, req == null ? null : req.reason(),
                        TenantContext.getCurrentUserName())));
    }

    @GetMapping("/work-orders/{workOrderId}/log")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<AccessLogView>>> log(@PathVariable UUID workOrderId) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(sharing.accessLog(TenantContext.getTenantIdAsObject(), workOrderId)));
    }
}
