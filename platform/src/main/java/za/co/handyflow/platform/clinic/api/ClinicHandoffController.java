package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicHandoffService;
import za.co.handyflow.platform.clinic.application.internal.ClinicService;
import za.co.handyflow.platform.clinic.domain.model.ClinicConsultation;
import za.co.handyflow.platform.clinic.dto.ConsultationResponse;
import za.co.handyflow.platform.clinic.dto.HandoffDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Nurse to doctor handoff. Module entitlement is enforced by ClinicModuleGuardConfig. */
@RestController
@RequestMapping("/api/v1/clinic/consultations")
@RequiredArgsConstructor
@Tag(name = "Clinic handoff", description = "Nurse to doctor handoff state machine")
public class ClinicHandoffController {

    private final ClinicHandoffService handoffService;
    private final ClinicService        clinicService;

    @PostMapping("/{id}/start-nurse-work")
    @PreAuthorize("hasAuthority('CLINIC_CLINICAL_WRITE')")
    @Operation(summary = "Mark a DRAFT as nurse-led work (NURSE_IN_PROGRESS)")
    public ResponseEntity<ApiResponse<ConsultationResponse>> startNurseWork(@PathVariable UUID id) {
        TenantId t = TenantContext.getTenantIdAsObject();
        return ok("Nurse work started", t, handoffService.startNurseWork(t, id));
    }

    @PostMapping("/{id}/send-to-doctor")
    @PreAuthorize("hasAuthority('CLINIC_CLINICAL_WRITE')")
    @Operation(summary = "Nurse hands over to the doctor (READY_FOR_DOCTOR); needs chief complaint and a vital sign")
    public ResponseEntity<ApiResponse<ConsultationResponse>> sendToDoctor(
            @PathVariable UUID id, @RequestBody(required = false) SendToDoctorRequest body) {
        TenantId t = TenantContext.getTenantIdAsObject();
        return ok("Sent to doctor", t, handoffService.sendToDoctor(t, id, body == null ? null : body.comment()));
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasAuthority('CLINIC_CLINICAL_SIGN')")
    @Operation(summary = "Doctor accepts the handoff (DOCTOR_REVIEWING)")
    public ResponseEntity<ApiResponse<ConsultationResponse>> accept(
            @PathVariable UUID id, @RequestBody(required = false) AcceptHandoffRequest body) {
        TenantId t = TenantContext.getTenantIdAsObject();
        return ok("Handoff accepted", t, handoffService.accept(t, id, body == null ? null : body.practitionerId()));
    }

    @PostMapping("/{id}/return-to-nurse")
    @PreAuthorize("hasAuthority('CLINIC_CLINICAL_SIGN')")
    @Operation(summary = "Doctor returns the consultation to the nurse; reasonCode and comment required")
    public ResponseEntity<ApiResponse<ConsultationResponse>> returnToNurse(
            @PathVariable UUID id, @RequestBody ReturnToNurseRequest body) {
        TenantId t = TenantContext.getTenantIdAsObject();
        return ok("Returned to nurse", t, handoffService.returnToNurse(t, id, body.reasonCode(), body.comment()));
    }

    @PostMapping("/{id}/resume-nurse-work")
    @PreAuthorize("hasAuthority('CLINIC_CLINICAL_WRITE')")
    @Operation(summary = "Nurse resumes a returned consultation")
    public ResponseEntity<ApiResponse<ConsultationResponse>> resume(@PathVariable UUID id) {
        TenantId t = TenantContext.getTenantIdAsObject();
        return ok("Nurse work resumed", t, handoffService.resumeNurseWork(t, id));
    }

    @PostMapping("/{id}/doctor-complete")
    @PreAuthorize("hasAuthority('CLINIC_CLINICAL_SIGN')")
    @Operation(summary = "Doctor finishes review (DOCTOR_COMPLETED); signing is a separate step")
    public ResponseEntity<ApiResponse<ConsultationResponse>> doctorComplete(@PathVariable UUID id) {
        TenantId t = TenantContext.getTenantIdAsObject();
        return ok("Doctor review completed", t, handoffService.doctorComplete(t, id));
    }

    @GetMapping("/handoff-queue")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Consultations waiting on or under doctor review, oldest first")
    public ResponseEntity<ApiResponse<List<ConsultationResponse>>> queue() {
        TenantId t = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success("Success",
                clinicService.toResponses(t, handoffService.queue(t))));
    }

    @GetMapping("/{id}/transitions")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Audit trail of handoff moves")
    public ResponseEntity<ApiResponse<List<TransitionResponse>>> transitions(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                handoffService.history(TenantContext.getTenantIdAsObject(), id)));
    }

    private ResponseEntity<ApiResponse<ConsultationResponse>> ok(String msg, TenantId t, ClinicConsultation c) {
        return ResponseEntity.ok(ApiResponse.success(msg, clinicService.toResponses(t, List.of(c)).get(0)));
    }
}
