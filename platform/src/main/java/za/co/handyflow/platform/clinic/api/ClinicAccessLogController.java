package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicAccessLogService;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clinic/access-log")
@RequiredArgsConstructor
@Tag(name = "Clinic access log", description = "Who viewed which patient record")
public class ClinicAccessLogController {

    private final ClinicAccessLogService service;

    @GetMapping
    @PreAuthorize("hasAuthority('CLINIC_ADMIN')")
    @Operation(summary = "Recent record reads, newest first; filter by patient or user (max 500)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(defaultValue = "100") int limit) {
        UUID tenant = TenantContext.getTenantIdAsObject().getValue();
        return ResponseEntity.ok(ApiResponse.success("Success", service.query(tenant, patientId, userId, limit)));
    }
}
