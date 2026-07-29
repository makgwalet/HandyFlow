package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.clinic.application.internal.ClinicRecallService;
import za.co.handyflow.platform.clinic.dto.RecallResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;

@RestController
@RequestMapping("/api/v1/clinic/recalls")
@RequiredArgsConstructor
@Tag(name = "Clinic Recalls", description = "Patients currently due for a follow-up, derived from consultation followUpDays")
public class ClinicRecallController {

    private final ClinicRecallService recallService;

    @GetMapping
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "List patients currently due (or overdue) for a follow-up")
    public ResponseEntity<ApiResponse<List<RecallResponse>>> getDueRecalls() {
        return ResponseEntity.ok(ApiResponse.success("Success",
                recallService.getDueRecalls(TenantContext.getTenantIdAsObject())));
    }
}