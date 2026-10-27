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
import za.co.handyflow.platform.facilities.application.internal.FacilityInspectionScheduleService;
import za.co.handyflow.platform.facilities.dto.InspectionScheduleRequest;
import za.co.handyflow.platform.facilities.dto.InspectionScheduleResponse;
import za.co.handyflow.platform.facilities.dto.UpdateInspectionScheduleRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/facilities/inspection-schedules")
@RequiredArgsConstructor
@Tag(name = "Facilities - Inspection schedules", description = "Recurring inspections and their reminders")
public class FacilityInspectionScheduleController {

    private static final String CAN_READ = "hasAnyAuthority('FACILITIES_READ','FACILITIES_MANAGE','FACILITIES_ADMIN'," +
            "'FACILITIES_TECHNICIAN','FACILITIES_INSPECT','FACILITIES_COMPLIANCE_MANAGE')";
    private static final String CAN_MANAGE = "hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN','FACILITIES_COMPLIANCE_MANAGE')";

    private final FacilityInspectionScheduleService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<Page<InspectionScheduleResponse>>> list(
            @RequestParam(required = false) UUID siteId, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), siteId, pageable)));
    }

    @PostMapping
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Run a checklist at a site (or asset) every N days. 409 SCHEDULE_EXISTS if it is already scheduled")
    public ResponseEntity<ApiResponse<InspectionScheduleResponse>> create(@Valid @RequestBody InspectionScheduleRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Schedule created",
                service.create(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<InspectionScheduleResponse>> update(@PathVariable UUID id,
                                                                          @RequestBody UpdateInspectionScheduleRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Schedule updated", service.update(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<InspectionScheduleResponse>> deactivate(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Schedule paused", service.setActive(TenantContext.getTenantIdAsObject(), id, false)));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<InspectionScheduleResponse>> reactivate(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Schedule resumed", service.setActive(TenantContext.getTenantIdAsObject(), id, true)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        service.delete(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Schedule deleted", null));
    }
}
