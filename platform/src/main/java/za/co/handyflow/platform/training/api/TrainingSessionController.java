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
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.training.application.internal.TrainingCourseService;
import za.co.handyflow.platform.training.application.internal.TrainingPdfService;
import za.co.handyflow.platform.training.application.internal.TrainingSessionService;
import za.co.handyflow.platform.training.domain.model.TrainingCourse;
import za.co.handyflow.platform.training.domain.model.TrainingSession;
import za.co.handyflow.platform.training.domain.repository.TrainingEnrollmentRepository;
import za.co.handyflow.platform.training.dto.CreateSessionRequest;
import za.co.handyflow.platform.training.dto.RescheduleSessionRequest;
import za.co.handyflow.platform.training.dto.SessionResponse;
import za.co.handyflow.platform.training.dto.CancelRequest;
import za.co.handyflow.platform.training.dto.UpdateSessionRequest;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training/sessions")
@RequiredArgsConstructor
@Tag(name = "Training - Sessions", description = "Scheduled runs of a training course")
public class TrainingSessionController {

    private static final String SOURCE_MODULE = "training";
    private static final String ENTITY_TYPE = "TrainingSession";

    private final TrainingSessionService sessionService;
    private final TrainingCourseService courseService;
    private final TrainingEnrollmentRepository enrollmentRepository;
    private final EvidenceFacade evidenceFacade;
    private final TrainingPdfService pdfService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('TRAINING_READ','TRAINING_MANAGE','TRAINING_ADMIN')")
    public ResponseEntity<ApiResponse<Page<SessionResponse>>> list(
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("training");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                sessionService.list(tenantId, courseId, status, pageable).map(s -> toResponse(tenantId, s))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('TRAINING_READ','TRAINING_MANAGE','TRAINING_ADMIN')")
    public ResponseEntity<ApiResponse<SessionResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("training");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(toResponse(tenantId, sessionService.get(tenantId, id))));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('TRAINING_MANAGE','TRAINING_ADMIN')")
    @Operation(summary = "Schedule a session for a course")
    public ResponseEntity<ApiResponse<SessionResponse>> create(@Valid @RequestBody CreateSessionRequest req) {
        featureGuard.requireModule("training");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        TrainingSession session = sessionService.create(tenantId, req.courseId(), req.startDate(), req.endDate(),
                req.venue(), req.trainerName(), req.capacity(), req.notes());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Session scheduled", toResponse(tenantId, session)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('TRAINING_MANAGE','TRAINING_ADMIN')")
    public ResponseEntity<ApiResponse<SessionResponse>> update(@PathVariable UUID id, @Valid @RequestBody UpdateSessionRequest req) {
        featureGuard.requireModule("training");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        TrainingSession session = sessionService.update(tenantId, id, req.venue(), req.trainerName(), req.capacity(), req.notes());
        return ResponseEntity.ok(ApiResponse.success("Session updated", toResponse(tenantId, session)));
    }

    @PostMapping("/{id}/reschedule")
    @PreAuthorize("hasAnyAuthority('TRAINING_MANAGE','TRAINING_ADMIN')")
    public ResponseEntity<ApiResponse<SessionResponse>> reschedule(@PathVariable UUID id, @Valid @RequestBody RescheduleSessionRequest req) {
        featureGuard.requireModule("training");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        TrainingSession session = sessionService.reschedule(tenantId, id, req.startDate(), req.endDate());
        return ResponseEntity.ok(ApiResponse.success("Session rescheduled", toResponse(tenantId, session)));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyAuthority('TRAINING_MANAGE','TRAINING_ADMIN')")
    public ResponseEntity<ApiResponse<SessionResponse>> start(@PathVariable UUID id) {
        featureGuard.requireModule("training");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(toResponse(tenantId, sessionService.start(tenantId, id))));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyAuthority('TRAINING_MANAGE','TRAINING_ADMIN')")
    public ResponseEntity<ApiResponse<SessionResponse>> complete(@PathVariable UUID id) {
        featureGuard.requireModule("training");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(toResponse(tenantId, sessionService.complete(tenantId, id))));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('TRAINING_MANAGE','TRAINING_ADMIN')")
    public ResponseEntity<ApiResponse<SessionResponse>> cancel(@PathVariable UUID id, @RequestBody(required = false) CancelRequest req) {
        featureGuard.requireModule("training");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        String reason = req != null ? req.reason() : null;
        return ResponseEntity.ok(ApiResponse.success(toResponse(tenantId, sessionService.cancel(tenantId, id, reason))));
    }

    // ── Evidence (attendance registers, materials, external invoices) ──────────

    @PostMapping(value = "/{id}/evidence", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyAuthority('TRAINING_MANAGE','TRAINING_ADMIN')")
    @Operation(summary = "Attach an attendance register, training material, or external invoice to a session")
    public ResponseEntity<ApiResponse<EvidenceResponse>> attachEvidence(
            @PathVariable UUID id, @RequestParam("file") MultipartFile file, @RequestParam String evidenceType) {
        featureGuard.requireModule("training");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        sessionService.get(tenantId, id); // 404s if the session doesn't belong to this tenant
        return ResponseEntity.ok(ApiResponse.success(evidenceFacade.attach(tenantId, file, evidenceType,
                SOURCE_MODULE, ENTITY_TYPE, id, null,
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/{id}/evidence")
    @PreAuthorize("hasAnyAuthority('TRAINING_READ','TRAINING_MANAGE','TRAINING_ADMIN')")
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> listEvidence(@PathVariable UUID id) {
        featureGuard.requireModule("training");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                evidenceFacade.listFor(tenantId, SOURCE_MODULE, ENTITY_TYPE, id)));
    }

    @GetMapping("/{id}/attendance-register/pdf")
    @PreAuthorize("hasAnyAuthority('TRAINING_READ','TRAINING_MANAGE','TRAINING_ADMIN')")
    public ResponseEntity<byte[]> downloadAttendanceRegister(@PathVariable UUID id) {
        featureGuard.requireModule("training");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        TrainingSession session = sessionService.get(tenantId, id);
        TrainingCourse course = courseService.get(tenantId, session.getCourseId());
        byte[] pdf = pdfService.generateAttendanceRegister(session, course,
                enrollmentRepository.findAllForSession(tenantId, id), fetchTenantName(tenantId));
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"attendance-register-" + id + ".pdf\"")
                .body(pdf);
    }

    /** Same jdbc.queryForMap(tenants) fallback pattern HrController's own fetchTenantDetails() uses. */
    private String fetchTenantName(TenantId tenantId) {
        try {
            var row = jdbc.queryForMap("SELECT name FROM tenants WHERE id = ?", tenantId.getValue());
            Object name = row.get("name");
            return name != null ? name.toString() : "HandyFlow Tenant";
        } catch (Exception e) {
            return "HandyFlow Tenant";
        }
    }

    private SessionResponse toResponse(TenantId tenantId, TrainingSession s) {
        TrainingCourse course = courseService.get(tenantId, s.getCourseId());
        long enrolledCount = enrollmentRepository.countLiveBySession(tenantId, s.getId());
        return new SessionResponse(s.getId(), s.getCourseId(), course.getTitle(), s.getStartDate(), s.getEndDate(),
                s.getVenue(), s.getTrainerName(), s.getCapacity(), enrolledCount, s.getStatus(), s.getNotes(),
                s.getCancelReason(), s.getCreatedAt());
    }
}
