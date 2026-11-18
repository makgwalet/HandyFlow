package za.co.handyflow.platform.supplychain.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.application.internal.SupplierPortalAdminService;
import za.co.handyflow.platform.supplychain.dto.SupplierPortalDtos.GrantRow;
import za.co.handyflow.platform.supplychain.dto.SupplierPortalDtos.InviteRequest;

import java.util.List;
import java.util.UUID;

/** Who at a supplier may log in to the supplier portal and quote online. */
@RestController
@RequestMapping("/api/v1/supply-chain")
@RequiredArgsConstructor
@Tag(name = "Supply Chain", description = "Supplier portal access")
public class ScmSupplierPortalAdminController {

    private final FeatureGuard featureGuard;
    private final SupplierPortalAdminService service;

    private static final String CAN_MANAGE = "hasAnyAuthority('SCM_SUPPLIER_MANAGE','SCM_ADMIN')";
    private static final String CAN_READ = "hasAnyAuthority('SCM_SUPPLIER_MANAGE','SCM_READ','SCM_ADMIN')";

    /** Module entitlement: a company whose plan does not include Supply Chain cannot reach any endpoint here. Runs before every handler. */
    @ModelAttribute
    void requireSupplyChainModule() { featureGuard.requireModule("supply_chain"); }

    @GetMapping("/suppliers/{supplierId}/portal-access")
    @PreAuthorize(CAN_READ)
    @Operation(summary = "People at this supplier who have (or have been offered) portal access")
    public ResponseEntity<ApiResponse<List<GrantRow>>> list(@PathVariable UUID supplierId) {
        return ok("Success", service.list(tenant(), supplierId));
    }

    @PostMapping("/suppliers/{supplierId}/portal-access")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Invite someone at the supplier to quote online (emails a single-use link valid for 7 days)")
    public ResponseEntity<ApiResponse<GrantRow>> invite(@PathVariable UUID supplierId, @RequestBody InviteRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Invite sent",
                service.invite(tenant(), supplierId, req.email(), uid(), uname())));
    }

    @PostMapping("/supplier-portal-access/{grantId}/resend")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Send a pending invite again with a fresh link")
    public ResponseEntity<ApiResponse<GrantRow>> resend(@PathVariable UUID grantId) {
        return ok("Invite re-sent", service.resend(tenant(), grantId, uid(), uname()));
    }

    @DeleteMapping("/supplier-portal-access/{grantId}")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Revoke a person's portal access — audited")
    public ResponseEntity<ApiResponse<GrantRow>> revoke(@PathVariable UUID grantId) {
        return ok("Access revoked", service.revoke(tenant(), grantId, uid(), uname()));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String m, T d) { return ResponseEntity.ok(ApiResponse.success(m, d)); }
    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
