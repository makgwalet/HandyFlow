package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.ControlService;
import za.co.handyflow.platform.legalcompliance.application.internal.CoverageService;
import za.co.handyflow.platform.legalcompliance.application.internal.GrcLinkService;
import za.co.handyflow.platform.legalcompliance.domain.model.ControlStatus;
import za.co.handyflow.platform.legalcompliance.dto.GrcDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Control catalogue, plus the Obligation ↔ Risk ↔ Control links and the coverage view. */
@RestController
@RequestMapping("/api/v1/legalcompliance/controls")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Controls", description = "Internal controls, their links to risks and obligations, and coverage gaps")
public class ControlController {

    private static final String CAN_READ = "hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_CONTROL_MANAGE','LEGALCOMPLIANCE_RISK_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_MANAGE = "hasAnyAuthority('LEGALCOMPLIANCE_CONTROL_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_ASSESS = "hasAnyAuthority('LEGALCOMPLIANCE_CONTROL_ASSESS','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_LINK = "hasAnyAuthority('LEGALCOMPLIANCE_CONTROL_MANAGE','LEGALCOMPLIANCE_RISK_MANAGE','LEGALCOMPLIANCE_ADMIN')";

    private final ControlService controls;
    private final GrcLinkService linkService;
    private final CoverageService coverage;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<ControlResponse>>> list(@RequestParam(required = false) ControlStatus status, @RequestParam(defaultValue = "false") boolean includeRetired) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(controls.list(tenant(), status, includeRetired)));
    }

    @GetMapping("/coverage")
    @PreAuthorize(CAN_READ)
    @Operation(summary = "Obligations without an active control, risks without one, and unlinked draft controls")
    public ResponseEntity<ApiResponse<CoverageResponse>> coverage() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(coverage.coverage(tenant())));
    }

    @GetMapping("/links")
    @PreAuthorize(CAN_READ)
    @Operation(summary = "Everything linked to one RISK, CONTROL or OBLIGATION")
    public ResponseEntity<ApiResponse<List<LinkItem>>> links(@RequestParam String kind, @RequestParam UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(linkService.linksFor(tenant(), kind.toUpperCase(), id)));
    }

    @PostMapping("/links")
    @PreAuthorize(CAN_LINK)
    public ResponseEntity<ApiResponse<LinkItem>> link(@Valid @RequestBody LinkRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Linked", linkService.link(tenant(), req, uid(), uname())));
    }

    @DeleteMapping("/links/{linkId}")
    @PreAuthorize(CAN_LINK)
    public ResponseEntity<ApiResponse<Void>> unlink(@PathVariable UUID linkId) {
        featureGuard.requireModule("legalcompliance");
        linkService.unlink(tenant(), linkId, uid(), uname());
        return ResponseEntity.ok(ApiResponse.success("Unlinked", null));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<ControlDetailResponse>> detail(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(controls.detail(tenant(), id)));
    }

    @PostMapping
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ControlResponse>> create(@Valid @RequestBody ControlRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Control created", controls.create(tenant(), req, uid(), uname())));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ControlResponse>> update(@PathVariable UUID id, @Valid @RequestBody ControlRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Control updated", controls.update(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Put the control into operation. Needs an objective, procedure, expected evidence and at least one link. Does NOT make it effective.")
    public ResponseEntity<ApiResponse<ControlResponse>> activate(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Control activated", controls.activate(tenant(), id, uid(), uname())));
    }

    @PostMapping("/{id}/design-assessment")
    @PreAuthorize(CAN_ASSESS)
    public ResponseEntity<ApiResponse<ControlResponse>> assessDesign(@PathVariable UUID id, @Valid @RequestBody DesignAssessmentRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Design assessed", controls.assessDesign(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/retire")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ControlResponse>> retire(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Control retired", controls.retire(tenant(), id, req, uid(), uname())));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
