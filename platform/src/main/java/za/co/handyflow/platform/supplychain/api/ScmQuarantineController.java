package za.co.handyflow.platform.supplychain.api;

import za.co.handyflow.platform.billing.FeatureGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.application.internal.QuarantineService;
import za.co.handyflow.platform.supplychain.application.internal.QuarantineService.Hold;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Stock held in quarantine after failing inspection. Returning it to the supplier is done by dispatching the return raised from the inspection. */
@RestController
@RequestMapping("/api/v1/supply-chain/quarantine")
@RequiredArgsConstructor
@Tag(name = "Supply Chain", description = "Quarantine")
public class ScmQuarantineController {

    private static final String CAN_READ = "hasAnyAuthority('SCM_READ','SCM_QUALITY_MANAGE','SCM_ADMIN')";
    private static final String CAN_MANAGE = "hasAnyAuthority('SCM_QUALITY_MANAGE','SCM_ADMIN')";

    public record Action(BigDecimal qty, String reason) {}

    private final FeatureGuard featureGuard;
    private final QuarantineService service;

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static <T> ResponseEntity<ApiResponse<T>> ok(String m, T body) { return ResponseEntity.ok(ApiResponse.success(m, body)); }


    /** Module entitlement: a company whose plan does not include Supply Chain cannot reach any endpoint here. Runs before every handler. */
    @ModelAttribute
    void requireSupplyChainModule() { featureGuard.requireModule("supply_chain"); }

    @GetMapping
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<Hold>>> list(@RequestParam(required = false) String status) { return ok("Success", service.list(tenant(), status)); }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<Hold>> get(@PathVariable UUID id) { return ok("Success", service.get(tenant(), id)); }

    @PostMapping("/{id}/release")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Put quarantined stock back where it came from (it passed a re-check, or a concession was agreed). A reason is required")
    public ResponseEntity<ApiResponse<Hold>> release(@PathVariable UUID id, @RequestBody Action body) {
        return ok("Released", service.release(tenant(), id, body.qty(), TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName(), body.reason()));
    }

    @PostMapping("/{id}/write-off")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Scrap quarantined stock (recorded as waste). A reason is required")
    public ResponseEntity<ApiResponse<Hold>> writeOff(@PathVariable UUID id, @RequestBody Action body) {
        return ok("Written off", service.writeOff(tenant(), id, body.qty(), TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName(), body.reason()));
    }
}
