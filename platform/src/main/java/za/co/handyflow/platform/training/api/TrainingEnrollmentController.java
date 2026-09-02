package za.co.handyflow.platform.training.api;

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
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.training.application.internal.TrainingEnrollmentService;
import za.co.handyflow.platform.training.domain.model.TrainingEnrollment;
import za.co.handyflow.platform.training.dto.CancelRequest;
import za.co.handyflow.platform.training.dto.CompleteEnrollmentRequest;
import za.co.handyflow.platform.training.dto.CreateEnrollmentRequest;
import za.co.handyflow.platform.training.dto.EnrollmentResponse;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training")
@RequiredArgsConstructor
@Tag(name = "Training - Enrollments", description = "Employee enrollments in a scheduled session")
public class TrainingEnrollmentController {

    private final TrainingEnrollmentService enrollmentService;
    private final FeatureGuard featureGuard;

    @GetMapping("/enrollments")
    @PreAuthorize("hasAnyAuthority('TRAINING_READ','TRAINING_MANAGE','TRAINING_ADMIN')")
    public ResponseEntity<ApiResponse<Page<EnrollmentResponse>>> list(
            @RequestParam(required = false) UUID sessionId,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("training");
        return ResponseEntity.ok(ApiResponse.success(enrollmentService
                .list(TenantContext.getTenantIdAsObject(), sessionId, employeeId, status, pageable)
                .map(this::toResponse)));
    }

    @GetMapping("/enrollments/{id}")
    @PreAuthorize("hasAnyAuthority('TRAINING_READ','TRAINING_MANAGE','TRAINING_ADMIN')")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("training");
        return ResponseEntity.ok(ApiResponse.success(
                toResponse(enrollmentService.get(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/sessions/{sessionId}/enrollments")
    @PreAuthorize("hasAnyAuthority('TRAINING_MANAGE','TRAINING_ADMIN')")
    @Operation(summary = "Enrol an employee (looked up in HR by id) into a session")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> enroll(
            @PathVariable UUID sessionId, @Valid @RequestBody CreateEnrollmentRequest req) {
        featureGuard.requireModule("training");
        TrainingEnrollment enrollment = enrollmentService.enroll(
                TenantContext.getTenantIdAsObject(), sessionId, req.employeeId(), req.notes());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Employee enrolled", toResponse(enrollment)));
    }

    @PostMapping("/enrollments/{id}/attended")
    @PreAuthorize("hasAnyAuthority('TRAINING_MANAGE','TRAINING_ADMIN')")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> markAttended(@PathVariable UUID id) {
        featureGuard.requireModule("training");
        return ResponseEntity.ok(ApiResponse.success(
                toResponse(enrollmentService.markAttended(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/enrollments/{id}/no-show")
    @PreAuthorize("hasAnyAuthority('TRAINING_MANAGE','TRAINING_ADMIN')")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> markNoShow(@PathVariable UUID id) {
        featureGuard.requireModule("training");
        return ResponseEntity.ok(ApiResponse.success(
                toResponse(enrollmentService.markNoShow(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/enrollments/{id}/complete")
    @PreAuthorize("hasAnyAuthority('TRAINING_MANAGE','TRAINING_ADMIN')")
    @Operation(summary = "Record the outcome (score/pass-fail) — moves the enrollment to COMPLETED or FAILED")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> complete(
            @PathVariable UUID id, @RequestBody CompleteEnrollmentRequest req) {
        featureGuard.requireModule("training");
        return ResponseEntity.ok(ApiResponse.success("Outcome recorded", toResponse(
                enrollmentService.complete(TenantContext.getTenantIdAsObject(), id, req.score(), req.passed()))));
    }

    @PostMapping("/enrollments/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('TRAINING_MANAGE','TRAINING_ADMIN')")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> cancel(
            @PathVariable UUID id, @RequestBody(required = false) CancelRequest req) {
        featureGuard.requireModule("training");
        String reason = req != null ? req.reason() : null;
        return ResponseEntity.ok(ApiResponse.success(
                toResponse(enrollmentService.cancel(TenantContext.getTenantIdAsObject(), id, reason))));
    }

    private EnrollmentResponse toResponse(TrainingEnrollment e) {
        return new EnrollmentResponse(e.getId(), e.getSessionId(), e.getEmployeeId(), e.getEmployeeNameSnapshot(),
                e.getEmployeeNumberSnapshot(), e.getStatus(), e.getEnrolledAt(), e.getCompletedAt(), e.getScore(),
                e.getPassed(), e.getNotes(), e.getCancelReason());
    }
}
