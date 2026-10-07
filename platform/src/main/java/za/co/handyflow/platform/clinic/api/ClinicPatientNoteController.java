package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicPatientNoteService;
import za.co.handyflow.platform.clinic.dto.PatientNoteDtos.CreateNoteRequest;
import za.co.handyflow.platform.clinic.dto.PatientNoteDtos.NoteResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clinic/patients/{patientId}/notes")
@RequiredArgsConstructor
@Tag(name = "Clinic patient notes", description = "Sticky notes and alerts on a patient's file")
public class ClinicPatientNoteController {

    private final ClinicPatientNoteService notes;

    @GetMapping
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Open notes and alerts for the patient; includeResolved=true adds the resolved ones")
    public ResponseEntity<ApiResponse<List<NoteResponse>>> list(@PathVariable UUID patientId,
                                                                @RequestParam(defaultValue = "false") boolean includeResolved) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                notes.list(TenantContext.getTenantIdAsObject(), patientId, includeResolved)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CLINIC_CLINICAL_WRITE')")
    @Operation(summary = "Add a note or an alert")
    public ResponseEntity<ApiResponse<NoteResponse>> create(@PathVariable UUID patientId, @RequestBody CreateNoteRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                notes.create(TenantContext.getTenantIdAsObject(), patientId, req)));
    }

    @PostMapping("/{noteId}/resolve")
    @PreAuthorize("hasAuthority('CLINIC_CLINICAL_WRITE')")
    @Operation(summary = "Mark a note or alert as no longer applying (it stays on record)")
    public ResponseEntity<ApiResponse<NoteResponse>> resolve(@PathVariable UUID patientId, @PathVariable UUID noteId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                notes.resolve(TenantContext.getTenantIdAsObject(), patientId, noteId)));
    }
}
