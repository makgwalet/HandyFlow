package za.co.handyflow.platform.supplychain.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.application.internal.ApprovalLimitService;
import za.co.handyflow.platform.supplychain.dto.ApprovalLimitDtos.*;

import java.util.UUID;

/** Monetary approval limits: who may approve how much, per document type. */
@RestController
@RequestMapping("/api/v1/supply-chain/approval-limits")
@RequiredArgsConstructor
@Tag(name = "Supply Chain", description = "Monetary approval limits")
public class ScmApprovalLimitController {

    private final FeatureGuard featureGuard;
    private final ApprovalLimitService service;

    private static final String CAN_MANAGE = "hasAnyAuthority('SCM_APPROVAL_LIMIT_MANAGE','SCM_ADMIN')";
    private static final String CAN_READ = "hasAnyAuthority('SCM_APPROVAL_LIMIT_MANAGE','SCM_READ','SCM_ADMIN')";

    /** Module entitlement: a company whose plan does not include Supply Chain cannot reach any endpoint here. Runs before every handler. */
    @ModelAttribute
    void requireSupplyChainModule() { featureGuard.requireModule("supply_chain"); }

    @GetMapping
    @PreAuthorize(CAN_READ)
    @Operation(summary = "Every limit, the enforcement mode, and whether enforcement can safely be switched on")
    public ResponseEntity<ApiResponse<LimitsView>> list() { return ok("Success", service.view(tenant())); }

    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "What the signed-in person may approve, per document type")
    public ResponseEntity<ApiResponse<MyLimits>> mine() { return ok("Success", service.mine(tenant(), uid())); }

    @PutMapping
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Set (or change) a limit for a person or a role; maxAmount null = unlimited — audited")
    public ResponseEntity<ApiResponse<LimitsView>> save(@RequestBody SaveLimitRequest req) {
        return ok("Limit saved", service.save(tenant(), uid(), uname(), req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Remove a limit — audited")
    public ResponseEntity<ApiResponse<LimitsView>> delete(@PathVariable UUID id) {
        return ok("Limit removed", service.delete(tenant(), uid(), uname(), id));
    }

    @PutMapping("/mode")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Switch enforcement OFF or ENFORCE; refused unless someone holds an unlimited limit for everything")
    public ResponseEntity<ApiResponse<LimitsView>> mode(@RequestBody ModeRequest req) {
        return ok("Mode updated", service.setMode(tenant(), uid(), uname(), req.mode()));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String m, T d) { return ResponseEntity.ok(ApiResponse.success(m, d)); }
    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
