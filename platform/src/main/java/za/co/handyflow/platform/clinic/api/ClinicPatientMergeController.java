package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicPatientMergeService;
import za.co.handyflow.platform.clinic.dto.PatientMergeDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clinic/patients")
@RequiredArgsConstructor
@Tag(name = "Clinic patient merge", description = "Merge two records of the same patient")
public class ClinicPatientMergeController {

    private final ClinicPatientMergeService service;

    @GetMapping("/merge-preview")
    @PreAuthorize("hasAuthority('CLINIC_PATIENT_MERGE')")
    @Operation(summary = "What merging would do: what moves, what disagrees, and anything that stops it. Changes nothing.")
    public ResponseEntity<ApiResponse<Preview>> preview(@RequestParam UUID survivorId, @RequestParam UUID mergedId) {
        return ResponseEntity.ok(ApiResponse.success("Success", service.preview(TenantContext.getTenantIdAsObject().getValue(), survivorId, mergedId)));
    }

    @PostMapping("/merge")
    @PreAuthorize("hasAuthority('CLINIC_PATIENT_MERGE')")
    @Operation(summary = "Merge the second record into the first: everything moves across, the second is archived, and the merge is recorded")
    public ResponseEntity<ApiResponse<MergeResult>> merge(@RequestBody MergeRequest body) {
        UUID me;
        try { me = UserContext.getCurrentUserId(); } catch (RuntimeException e) { me = null; }
        return ResponseEntity.ok(ApiResponse.success("Merged", service.merge(TenantContext.getTenantIdAsObject().getValue(), me, body)));
    }
}
