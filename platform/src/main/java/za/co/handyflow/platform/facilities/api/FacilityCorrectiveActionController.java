package za.co.handyflow.platform.facilities.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.facilities.application.internal.FacilityActionCentreService;
import za.co.handyflow.platform.facilities.application.internal.FacilityCorrectiveActionService;
import za.co.handyflow.platform.facilities.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/facilities")
@RequiredArgsConstructor
@Tag(name = "Facilities - Corrective actions", description = "Things to put right, and the compliance action centre")
public class FacilityCorrectiveActionController {

    private static final String CAN_READ = "hasAnyAuthority('FACILITIES_READ','FACILITIES_MANAGE','FACILITIES_ADMIN','FACILITIES_COMPLIANCE_MANAGE')";
    private static final String CAN_MANAGE = "hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN','FACILITIES_COMPLIANCE_MANAGE')";

    private final FacilityCorrectiveActionService service;
    private final FacilityActionCentreService actionCentreService;
    private final FeatureGuard featureGuard;

    @GetMapping("/action-centre")
    @PreAuthorize(CAN_READ)
    @Operation(summary = "Lapsed and expiring certificates plus critical or overdue corrective actions, most severe first")
    public ResponseEntity<ApiResponse<ActionCentreResponse>> actionCentre() {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(actionCentreService.get(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/corrective-actions")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<Page<CorrectiveActionResponse>>> list(
            @RequestParam(required = false) UUID siteId, @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), siteId, status, pageable)));
    }

    @GetMapping("/corrective-actions/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<CorrectiveActionResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/corrective-actions")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<CorrectiveActionResponse>> create(@Valid @RequestBody CorrectiveActionRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Action logged",
                service.create(TenantContext.getTenantIdAsObject(), request)));
    }

    @PostMapping("/corrective-actions/{id}/work-order")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Raise a corrective work order for this action and link it")
    public ResponseEntity<ApiResponse<CorrectiveActionResponse>> raiseWorkOrder(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Work order raised",
                service.raiseWorkOrder(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/corrective-actions/{id}/close")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Close an action with a note of what was done. A linked work order must be completed first")
    public ResponseEntity<ApiResponse<CorrectiveActionResponse>> close(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Action closed",
                service.close(TenantContext.getTenantIdAsObject(), id, request.reason())));
    }

    @PostMapping("/corrective-actions/{id}/cancel")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<CorrectiveActionResponse>> cancel(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Action cancelled",
                service.cancel(TenantContext.getTenantIdAsObject(), id, request.reason())));
    }

    @PostMapping("/corrective-actions/{id}/reschedule")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<CorrectiveActionResponse>> reschedule(@PathVariable UUID id, @Valid @RequestBody RescheduleActionRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Action rescheduled",
                service.reschedule(TenantContext.getTenantIdAsObject(), id, request.dueDate())));
    }
}
