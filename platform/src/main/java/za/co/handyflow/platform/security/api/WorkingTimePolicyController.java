// security/api/WorkingTimePolicyController.java
package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.WorkingTimePolicy.Policy;
import za.co.handyflow.platform.security.application.internal.WorkingTimePolicyService;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Security - Working-time policies")
public class WorkingTimePolicyController {

    public record PolicyRequest(@NotBlank @Size(max = 40) String category,
                                @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveFrom,
                                @NotNull Double warnAt, @NotNull Double ordinaryWeekly, @NotNull Double maxOvertimeWeekly,
                                @NotNull Double maxDaily, @NotNull Double maxDailyOvertime, @NotNull Double minRestHours,
                                @Size(max = 500) String legalBasis) {}
    public record CategoryRequest(@NotBlank @Size(max = 40) String category) {}

    private final WorkingTimePolicyService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/api/v1/security/working-time-policies")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Working-time policies of this company, newest first within each category",
            description = "DEFAULT is the company-wide policy; other categories override it for the guards assigned to them.")
    public ResponseEntity<ApiResponse<List<Policy>>> list() {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("OK", service.list(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/api/v1/security/working-time-policies")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Add a working-time policy that starts on a date (today or later)",
            description = "Policies are never edited: add a new one with a later date. The combined weekly ceiling is ordinary + overtime. " +
                    "Limits beyond the general BCEA figures (45 ordinary, 10 overtime, 12 daily, 3 daily overtime, 12 h rest) need a legal basis. " +
                    "A policy dated mid-week first applies from the following Monday's week. Audited.")
    public ResponseEntity<ApiResponse<Policy>> add(@Valid @RequestBody PolicyRequest r) {
        featureGuard.requireModule("security");
        Policy p = new Policy(null, r.category(), r.effectiveFrom(), r.warnAt(), r.ordinaryWeekly(), r.maxOvertimeWeekly(), r.maxDaily(),
                r.maxDailyOvertime(), r.minRestHours(), r.legalBasis());
        return ResponseEntity.ok(ApiResponse.success("OK", service.create(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), p)));
    }

    @PutMapping("/api/v1/security/guards/{guardId}/working-time-category")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Put a guard in a working-time category (DEFAULT clears it)", description = "The category must already have a policy. Audited.")
    public ResponseEntity<ApiResponse<Map<String, String>>> assign(@PathVariable UUID guardId, @Valid @RequestBody CategoryRequest r) {
        featureGuard.requireModule("security");
        String c = service.assign(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), guardId, r.category());
        return ResponseEntity.ok(ApiResponse.success("OK", Map.of("category", c)));
    }
}
