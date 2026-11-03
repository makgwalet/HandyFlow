package za.co.handyflow.platform.contracting.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.contracting.application.internal.ContractSharingService;
import za.co.handyflow.platform.contracting.dto.ContractSharingDtos.*;
import za.co.handyflow.platform.secureaccess.application.SecureAccessDtos.AccessLogView;
import za.co.handyflow.platform.secureaccess.application.SecureAccessDtos.RecipientView;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Secure sharing of a signed contract with people outside the company (a client's lawyer, a bank, an auditor): each gets their own
 * expiring link and confirms their email with a code before they can open it. Looking needs the contract read rights; giving or
 * taking away access needs the contract management rights.
 */
@RestController
@RequestMapping("/api/v1/contracts/{contractId}/sharing")
@RequiredArgsConstructor
public class ContractSharingController {

    private static final String READ = "hasAnyAuthority('USER_READ','USER_UPDATE','CONTRACTS_MANAGE','CONTRACTS_ADMIN')";
    private static final String CHANGE = "hasAnyAuthority('USER_UPDATE','CONTRACTS_MANAGE','CONTRACTS_ADMIN')";

    private final ContractSharingService sharing;

    @GetMapping
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<SharingView>> view(@PathVariable UUID contractId) {
        return ResponseEntity.ok(ApiResponse.success(sharing.view(TenantContext.getTenantIdAsObject(), contractId)));
    }

    @PostMapping("/recipients")
    @PreAuthorize(CHANGE)
    public ResponseEntity<ApiResponse<RecipientView>> add(@PathVariable UUID contractId, @RequestBody RecipientRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Link sent",
                sharing.addRecipient(TenantContext.getTenantIdAsObject(), contractId, req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/recipients/{recipientId}/reissue")
    @PreAuthorize(CHANGE)
    public ResponseEntity<ApiResponse<RecipientView>> reissue(@PathVariable UUID contractId, @PathVariable UUID recipientId,
                                                              @RequestBody(required = false) ReissueRequest req) {
        return ResponseEntity.ok(ApiResponse.success("New link sent; the previous link no longer works",
                sharing.reissue(TenantContext.getTenantIdAsObject(), contractId, recipientId, req == null ? null : req.expiryDays(),
                        TenantContext.getCurrentUserName())));
    }

    @PostMapping("/recipients/{recipientId}/revoke")
    @PreAuthorize(CHANGE)
    public ResponseEntity<ApiResponse<RecipientView>> revoke(@PathVariable UUID contractId, @PathVariable UUID recipientId,
                                                             @RequestBody RevokeRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Access revoked",
                sharing.revoke(TenantContext.getTenantIdAsObject(), contractId, recipientId, req == null ? null : req.reason(),
                        TenantContext.getCurrentUserName())));
    }

    @GetMapping("/log")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<AccessLogView>>> log(@PathVariable UUID contractId) {
        return ResponseEntity.ok(ApiResponse.success(sharing.accessLog(TenantContext.getTenantIdAsObject(), contractId)));
    }
}
