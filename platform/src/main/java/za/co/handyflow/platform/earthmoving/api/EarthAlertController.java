package za.co.handyflow.platform.earthmoving.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.earthmoving.application.internal.EarthAlertService;
import za.co.handyflow.platform.earthmoving.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/earthmoving/alerts")
@RequiredArgsConstructor
@Tag(name = "Earthmoving - Alerts", description = "Alerts, acknowledgement, escalation and who is told")
public class EarthAlertController {

    private final EarthAlertService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    @Operation(summary = "List alerts with who they were sent to; open ones by default")
    public ResponseEntity<ApiResponse<Page<AlertResponse>>> list(@RequestParam(defaultValue = "true") boolean openOnly,
                                                                  @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), openOnly, pageable)));
    }

    @PatchMapping("/{id}/acknowledge")
    @PreAuthorize("hasAuthority('EARTHMOVING_WRITE')")
    public ResponseEntity<ApiResponse<AlertResponse>> acknowledge(@PathVariable UUID id) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success(service.acknowledge(TenantContext.getTenantIdAsObject(), id, UserContext.getCurrentUserId())));
    }

    @PatchMapping("/{id}/resolve")
    @PreAuthorize("hasAuthority('EARTHMOVING_MAINTAIN')")
    public ResponseEntity<ApiResponse<AlertResponse>> resolve(@PathVariable UUID id, @RequestBody(required = false) ResolveAlertRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success(service.resolve(TenantContext.getTenantIdAsObject(), id,
                request == null ? null : request.note(), UserContext.getCurrentUserId())));
    }

    @GetMapping("/rules")
    @PreAuthorize("hasAuthority('EARTHMOVING_ADMIN')")
    public ResponseEntity<ApiResponse<List<AlertRuleResponse>>> rules() {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success(service.listRules(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/rules/{type}")
    @PreAuthorize("hasAuthority('EARTHMOVING_ADMIN')")
    @Operation(summary = "Choose who is told about one kind of alert, and who is told next if nobody acknowledges it")
    public ResponseEntity<ApiResponse<AlertRuleResponse>> saveRule(@PathVariable String type, @RequestBody SaveAlertRuleRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success(service.saveRule(TenantContext.getTenantIdAsObject(), type, request)));
    }
}
