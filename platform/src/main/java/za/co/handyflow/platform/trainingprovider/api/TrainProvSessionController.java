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
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvClientService;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvCourseService;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvPdfService;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvSessionService;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvClient;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvCourse;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvSession;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvEnrollmentRepository;
import za.co.handyflow.platform.trainingprovider.dto.CancelRequest;
import za.co.handyflow.platform.trainingprovider.dto.CreateSessionRequest;
import za.co.handyflow.platform.trainingprovider.dto.RescheduleSessionRequest;
import za.co.handyflow.platform.trainingprovider.dto.SessionResponse;
import za.co.handyflow.platform.trainingprovider.dto.UpdateSessionRequest;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider/sessions")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Sessions", description = "Scheduled public or in-house course runs")
public class TrainProvSessionController {

    private static final String SOURCE_MODULE = "trainingprovider";
    private static final String ENTITY_TYPE = "TrainProvSession";

    private final TrainProvSessionService sessionService;
    private final TrainProvCourseService courseService;
    private final TrainProvClientService clientService;
    private final TrainProvEnrollmentRepository enrollmentRepository;
    private final EvidenceFacade evidenceFacade;
    private final TrainProvPdfService pdfService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<Page<SessionResponse>>> list(
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("trainingprovider");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                sessionService.list(tenantId, courseId, clientId, status, pageable).map(s -> toResponse(tenantId, s))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<SessionResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(toResponse(tenantId, sessionService.get(tenantId, id))));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    @Operation(summary = "Schedule a public or in-house (client-specific) session")
    public ResponseEntity<ApiResponse<SessionResponse>> create(@Valid @RequestBody CreateSessionRequest req) {
        featureGuard.requireModule("trainingprovider");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        TrainProvSession session = sessionService.create(tenantId, req.courseId(), req.sessionType(), req.clientId(),
                req.startDate(), req.endDate(), req.venue(), req.trainerName(), req.capacity(), req.notes());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Session scheduled", toResponse(tenantId, session)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<SessionResponse>> update(@PathVariable UUID id, @Valid @RequestBody UpdateSessionRequest req) {
        featureGuard.requireModule("trainingprovider");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        TrainProvSession session = sessionService.update(tenantId, id, req.venue(), req.trainerName(), req.capacity(), req.notes());
        return ResponseEntity.ok(ApiResponse.success("Session updated", toResponse(tenantId, session)));
    }

    @PostMapping("/{id}/reschedule")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<SessionResponse>> reschedule(@PathVariable UUID id, @Valid @RequestBody RescheduleSessionRequest req) {
        featureGuard.requireModule("trainingprovider");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        TrainProvSession session = sessionService.reschedule(tenantId, id, req.startDate(), req.endDate());
        return ResponseEntity.ok(ApiResponse.success("Session rescheduled", toResponse(tenantId, session)));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<SessionResponse>> start(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(toResponse(tenantId, sessionService.start(tenantId, id))));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<SessionResponse>> complete(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(toResponse(tenantId, sessionService.complete(tenantId, id))));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<SessionResponse>> cancel(@PathVariable UUID id, @RequestBody(required = false) CancelRequest req) {
        featureGuard.requireModule("trainingprovider");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        String reason = req != null ? req.reason() : null;
        return ResponseEntity.ok(ApiResponse.success(toResponse(tenantId, sessionService.cancel(tenantId, id, reason))));
    }

    // ── Evidence (accreditation packs, sign-in sheets, materials) ───────────

    @PostMapping(value = "/{id}/evidence", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<EvidenceResponse>> attachEvidence(
            @PathVariable UUID id, @RequestParam("file") MultipartFile file, @RequestParam String evidenceType) {
        featureGuard.requireModule("trainingprovider");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        sessionService.get(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success(evidenceFacade.attach(tenantId, file, evidenceType,
                SOURCE_MODULE, ENTITY_TYPE, id, null,
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/{id}/evidence")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> listEvidence(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(
                evidenceFacade.listFor(TenantContext.getTenantIdAsObject(), SOURCE_MODULE, ENTITY_TYPE, id)));
    }

    @GetMapping("/{id}/attendance-register/pdf")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<byte[]> downloadAttendanceRegister(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        TrainProvSession session = sessionService.get(tenantId, id);
        TrainProvCourse course = courseService.get(tenantId, session.getCourseId());
        byte[] pdf = pdfService.generateAttendanceRegister(session, course,
                enrollmentRepository.findAllForSession(tenantId, id), fetchProviderName(tenantId));
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"attendance-register-" + id + ".pdf\"")
                .body(pdf);
    }

    private String fetchProviderName(TenantId tenantId) {
        try {
            var row = jdbc.queryForMap("SELECT name FROM tenants WHERE id = ?", tenantId.getValue());
            Object name = row.get("name");
            return name != null ? name.toString() : "HandyFlow Training Provider";
        } catch (Exception e) {
            return "HandyFlow Training Provider";
        }
    }

    private SessionResponse toResponse(TenantId tenantId, TrainProvSession s) {
        TrainProvCourse course = courseService.get(tenantId, s.getCourseId());
        String clientName = null;
        if (s.getClientId() != null) {
            TrainProvClient client = clientService.get(tenantId, s.getClientId());
            clientName = client.getTradingName();
        }
        long enrolledCount = enrollmentRepository.countLiveBySession(tenantId, s.getId());
        return new SessionResponse(s.getId(), s.getCourseId(), course.getTitle(), s.getSessionType(), s.getClientId(),
                clientName, s.getStartDate(), s.getEndDate(), s.getVenue(), s.getTrainerName(), s.getCapacity(),
                enrolledCount, s.getStatus(), s.getNotes(), s.getCancelReason(), s.getCreatedAt());
    }
}
