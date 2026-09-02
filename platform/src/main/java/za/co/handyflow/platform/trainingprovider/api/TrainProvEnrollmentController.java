package za.co.handyflow.platform.trainingprovider.api;

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
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvEnrollmentService;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvEnrollment;
import za.co.handyflow.platform.trainingprovider.dto.CancelRequest;
import za.co.handyflow.platform.trainingprovider.dto.CompleteEnrollmentRequest;
import za.co.handyflow.platform.trainingprovider.dto.CreateEnrollmentRequest;
import za.co.handyflow.platform.trainingprovider.dto.EnrollmentResponse;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Enrollments", description = "Delegate enrollments in a scheduled session")
public class TrainProvEnrollmentController {

    private final TrainProvEnrollmentService enrollmentService;
    private final FeatureGuard featureGuard;

    @GetMapping("/enrollments")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<Page<EnrollmentResponse>>> list(
            @RequestParam(required = false) UUID sessionId,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(enrollmentService
                .list(TenantContext.getTenantIdAsObject(), sessionId, clientId, status, pageable)
                .map(this::toResponse)));
    }

    @GetMapping("/enrollments/{id}")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(
                toResponse(enrollmentService.get(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/sessions/{sessionId}/enrollments")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    @Operation(summary = "Enrol a delegate into a session")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> enroll(
            @PathVariable UUID sessionId, @Valid @RequestBody CreateEnrollmentRequest req) {
        featureGuard.requireModule("trainingprovider");
        TrainProvEnrollment enrollment = enrollmentService.enroll(
                TenantContext.getTenantIdAsObject(), sessionId, req.delegateId(), req.notes());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Delegate enrolled", toResponse(enrollment)));
    }

    @PostMapping("/enrollments/{id}/attended")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> markAttended(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(
                toResponse(enrollmentService.markAttended(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/enrollments/{id}/no-show")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> markNoShow(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(
                toResponse(enrollmentService.markNoShow(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/enrollments/{id}/complete")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> complete(
            @PathVariable UUID id, @RequestBody CompleteEnrollmentRequest req) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Outcome recorded", toResponse(
                enrollmentService.complete(TenantContext.getTenantIdAsObject(), id, req.score(), req.passed()))));
    }

    @PostMapping("/enrollments/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> cancel(
            @PathVariable UUID id, @RequestBody(required = false) CancelRequest req) {
        featureGuard.requireModule("trainingprovider");
        String reason = req != null ? req.reason() : null;
        return ResponseEntity.ok(ApiResponse.success(
                toResponse(enrollmentService.cancel(TenantContext.getTenantIdAsObject(), id, reason))));
    }

    private EnrollmentResponse toResponse(TrainProvEnrollment e) {
        return new EnrollmentResponse(e.getId(), e.getSessionId(), e.getDelegateId(), e.getClientId(),
                e.getDelegateNameSnapshot(), e.getStatus(), e.getEnrolledAt(), e.getCompletedAt(), e.getScore(),
                e.getPassed(), e.getNotes(), e.getCancelReason(), e.isInvoiced());
    }
}
