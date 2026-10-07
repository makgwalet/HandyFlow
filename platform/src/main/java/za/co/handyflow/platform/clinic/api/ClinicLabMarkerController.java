package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicLabMarkerService;
import za.co.handyflow.platform.clinic.dto.lab.LabMarkerDtos.CriticalLabItem;
import za.co.handyflow.platform.clinic.dto.lab.LabMarkerDtos.SaveMarkersRequest;
import za.co.handyflow.platform.clinic.dto.lab.LabResultResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clinic/lab")
@RequiredArgsConstructor
@Tag(name = "Clinic lab markers", description = "Lab markers typed from the report, flagged against the lab's own ranges")
public class ClinicLabMarkerController {

    private final ClinicLabMarkerService markers;

    @PutMapping("/results/{id}/markers")
    @PreAuthorize("hasAuthority('CLINIC_LAB_WRITE')")
    @Operation(summary = "Replace the markers of an unreviewed result. Flags are worked out from the reference range and critical limits supplied; nothing is built in.")
    public ResponseEntity<ApiResponse<LabResultResponse>> save(@PathVariable UUID id, @Valid @RequestBody SaveMarkersRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Markers saved", markers.saveMarkers(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @GetMapping("/critical")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Unreviewed results with a critical marker, oldest first")
    public ResponseEntity<ApiResponse<List<CriticalLabItem>>> critical() {
        return ResponseEntity.ok(ApiResponse.success("Success", markers.criticalQueue(TenantContext.getTenantIdAsObject())));
    }
}
