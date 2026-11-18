package za.co.handyflow.platform.supplychain.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.supplychain.application.internal.ScmMatchTolerances;
import za.co.handyflow.platform.supplychain.application.internal.ScmMatchTolerances.Tolerances;
import za.co.handyflow.platform.supplychain.application.internal.ScmMatchTolerances.View;

/** The company's three-way-match tolerances. Reading is open to Supply Chain readers; changing needs SCM_ADMIN. */
@RestController
@RequestMapping("/api/v1/supply-chain/match-tolerances")
@RequiredArgsConstructor
@Tag(name = "Supply Chain", description = "Match tolerances")
public class ScmMatchSettingsController {

    private final ScmMatchTolerances tolerances;

    @GetMapping
    @PreAuthorize("hasAuthority('SCM_READ')")
    @Operation(summary = "Effective tolerances, the defaults, and whether the company has its own")
    public ResponseEntity<ApiResponse<View>> get() {
        return ResponseEntity.ok(ApiResponse.success("Success", tolerances.view(TenantContext.getTenantIdAsObject().getValue())));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('SCM_ADMIN')")
    @Operation(summary = "Set the company's tolerances — applies to matches run from now on and is written to the audit trail")
    public ResponseEntity<ApiResponse<View>> put(@RequestBody Tolerances body) {
        return ResponseEntity.ok(ApiResponse.success("Tolerances saved",
                tolerances.save(TenantContext.getTenantIdAsObject().getValue(), body,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }
}
