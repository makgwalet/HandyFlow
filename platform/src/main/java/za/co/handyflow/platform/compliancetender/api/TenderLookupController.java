package za.co.handyflow.platform.compliancetender.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.compliancetender.application.internal.TenderLookupService;
import za.co.handyflow.platform.compliancetender.dto.AddTenderLookupValueRequest;
import za.co.handyflow.platform.compliancetender.dto.TenderLookupValueResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance/lookups")
@RequiredArgsConstructor
@Tag(name = "Compliance - Pick-lists", description = "A company's own additions to the pick-lists on the compliance and tender screens")
public class TenderLookupController {

    private final TenderLookupService lookups;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_READ','COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Every value this company has added, across all lists")
    public ResponseEntity<ApiResponse<List<TenderLookupValueResponse>>> list() {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.ok(ApiResponse.success(lookups.list(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/{listKey}")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Add a value to one of the company's pick-lists (adding one that exists returns it)")
    public ResponseEntity<ApiResponse<TenderLookupValueResponse>> add(@PathVariable String listKey, @Valid @RequestBody AddTenderLookupValueRequest request) {
        featureGuard.requireModule("compliancetender");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                lookups.add(TenantContext.getTenantIdAsObject(), listKey, request.value(), TenantContext.getCurrentUserId())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','COMPLIANCE_ADMIN')")
    @Operation(summary = "Remove a value the company added")
    public ResponseEntity<Void> remove(@PathVariable UUID id) {
        featureGuard.requireModule("compliancetender");
        lookups.remove(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.noContent().build();
    }
}
