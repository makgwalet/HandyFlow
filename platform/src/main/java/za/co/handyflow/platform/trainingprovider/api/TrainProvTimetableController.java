package za.co.handyflow.platform.trainingprovider.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvTimetableService;
import za.co.handyflow.platform.trainingprovider.dto.CohortDtos.Reason;
import za.co.handyflow.platform.trainingprovider.dto.TimetableDtos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Timetable", description = "Dated teaching slots for cohorts")
public class TrainProvTimetableController {

    private static final String READ = "hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";

    private final TrainProvTimetableService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/cohorts/{cohortId}/timetable")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<SlotView>>> forCohort(@PathVariable UUID cohortId) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.forCohort(TenantContext.getTenantIdAsObject(), cohortId)));
    }

    @GetMapping("/timetable")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<SlotView>>> window(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                              @RequestParam(required = false) UUID venueId, @RequestParam(required = false) UUID practitionerId,
                                                              @RequestParam(required = false) UUID cohortId) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.window(TenantContext.getTenantIdAsObject(), from, to, venueId, practitionerId, cohortId)));
    }

    @PostMapping("/cohorts/{cohortId}/timetable")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<SlotView>> add(@PathVariable UUID cohortId, @Valid @RequestBody SlotRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Slot added", service.add(TenantContext.getTenantIdAsObject(), cohortId, r, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/timetable/{slotId}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<SlotView>> change(@PathVariable UUID slotId, @Valid @RequestBody SlotRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Slot updated", service.change(TenantContext.getTenantIdAsObject(), slotId, r, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/timetable/{slotId}/cancel")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<SlotView>> cancel(@PathVariable UUID slotId, @Valid @RequestBody Reason r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Slot cancelled", service.cancel(TenantContext.getTenantIdAsObject(), slotId, r.reason(), TenantContext.getCurrentUserId())));
    }
}
