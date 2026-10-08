package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicPatientDirectoryService;
import za.co.handyflow.platform.clinic.dto.PatientDirectoryPage;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clinic/patients/directory")
@RequiredArgsConstructor
@Tag(name = "Clinic Patient Directory", description = "Patient list with saved views and visit facts")
public class ClinicPatientDirectoryController {

    private final ClinicPatientDirectoryService directory;

    @GetMapping
    @PreAuthorize("hasAuthority('CLINIC_PATIENT_READ')")
    @Operation(summary = "Patients by view (ALL, RECENT, TODAY, MINE, FOLLOW_UP, NEVER_SEEN, DUPLICATES) with last visit, next appointment and flags")
    public ResponseEntity<ApiResponse<PatientDirectoryPage>> list(
            @RequestParam(defaultValue = "ALL") String view,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(required = false) UUID practitionerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(ApiResponse.success("Success", directory.directory(
                TenantContext.getTenantIdAsObject(), view, search, includeArchived, practitionerId, page, size)));
    }
}
