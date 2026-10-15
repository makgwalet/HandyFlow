package za.co.handyflow.platform.earthmoving.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.earthmoving.application.internal.EarthInspectionService;
import za.co.handyflow.platform.earthmoving.dto.CreateInspectionRequest;
import za.co.handyflow.platform.earthmoving.dto.InspectionResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/earthmoving/inspections")
@RequiredArgsConstructor
@Tag(name = "Earthmoving inspections")
public class EarthInspectionController {

    private final EarthInspectionService inspections;
    private final FeatureGuard featureGuard;

    @GetMapping("/checklist")
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    @Operation(summary = "The items every inspection must answer")
    public ResponseEntity<ApiResponse<List<String>>> checklist() {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Checklist", inspections.checklist()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    @Operation(summary = "Recent inspections, newest first, optionally for one machine")
    public ResponseEntity<ApiResponse<List<InspectionResponse>>> recent(@RequestParam(required = false) UUID assetId) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Inspections", inspections.recent(TenantContext.getTenantIdAsObject(), assetId)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EARTHMOVING_WRITE')")
    @Operation(summary = "Record a pre-use inspection; an unsafe finding takes the machine out of service")
    public ResponseEntity<ApiResponse<InspectionResponse>> create(@Valid @RequestBody CreateInspectionRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Inspection recorded",
                inspections.create(TenantContext.getTenantIdAsObject(), request, UserContext.getCurrentUserId())));
    }
}
