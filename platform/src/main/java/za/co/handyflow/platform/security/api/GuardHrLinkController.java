// security/api/GuardHrLinkController.java
package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.GuardHrLinkService;
import za.co.handyflow.platform.security.dto.GuardHrLinkDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** The link between a guard and the HR employee record of the same person. Reading needs SECURITY_READ; changing it SECURITY_MANAGE. */
@RestController
@RequestMapping("/api/v1/security/guards")
@RequiredArgsConstructor
@Tag(name = "Security - Guard HR link")
public class GuardHrLinkController {

    private final GuardHrLinkService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/{guardId}/hr-link")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "The HR employee record this guard is linked to, if any")
    public ResponseEntity<ApiResponse<HrLink>> get(@PathVariable UUID guardId) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), guardId)));
    }

    @GetMapping("/hr-employees")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Search HR employees to link a guard to (at least two characters)")
    public ResponseEntity<ApiResponse<List<EmployeeOption>>> search(@RequestParam String q) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(service.search(TenantContext.getTenantIdAsObject(), q)));
    }

    @PutMapping("/{guardId}/hr-link")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Link the guard to an HR employee record")
    public ResponseEntity<ApiResponse<HrLink>> link(@PathVariable UUID guardId, @Valid @RequestBody LinkRequest req) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("Guard linked to HR record", service.link(TenantContext.getTenantIdAsObject(), guardId, req.employeeId())));
    }

    @DeleteMapping("/{guardId}/hr-link")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Remove the link to the HR employee record")
    public ResponseEntity<ApiResponse<HrLink>> unlink(@PathVariable UUID guardId) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("Link removed", service.unlink(TenantContext.getTenantIdAsObject(), guardId)));
    }
}
