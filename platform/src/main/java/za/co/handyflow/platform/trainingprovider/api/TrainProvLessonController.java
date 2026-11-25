package za.co.handyflow.platform.trainingprovider.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvLessonService;
import za.co.handyflow.platform.trainingprovider.dto.LessonDtos.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Learning content", description = "Lessons and learner progress")
public class TrainProvLessonController {

    private static final String READ = "hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final TrainProvLessonService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/lessons")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<LessonView>>> list(@RequestParam(required = false) UUID programmeVersionId, @RequestParam(required = false) UUID moduleId,
                                                              @RequestParam(required = false) String status) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), programmeVersionId, moduleId, status)));
    }

    @GetMapping("/lessons/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<LessonView>> get(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/lessons")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<LessonView>> create(@Valid @RequestBody LessonRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Lesson created", service.create(TenantContext.getTenantIdAsObject(), r, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/lessons/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<LessonView>> update(@PathVariable UUID id, @Valid @RequestBody LessonUpdate r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Lesson updated", service.update(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/lessons/{id}/approve")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<LessonView>> approve(@PathVariable UUID id, @RequestBody(required = false) ApproveRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Lesson approved",
                service.approve(TenantContext.getTenantIdAsObject(), id, r == null ? null : r.overrideReason(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/lessons/{id}/retire")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<LessonView>> retire(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Lesson retired", service.retire(TenantContext.getTenantIdAsObject(), id, r.reason(), TenantContext.getCurrentUserId())));
    }

    @GetMapping("/learner-enrolments/{enrolmentId}/lesson-progress")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<ProgressView>> progress(@PathVariable UUID enrolmentId) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.progress(TenantContext.getTenantIdAsObject(), enrolmentId)));
    }

    @PostMapping("/lesson-progress")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ProgressRecord>> finish(@Valid @RequestBody FinishRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Marked as finished",
                service.finish(TenantContext.getTenantIdAsObject(), r, TenantContext.getCurrentUserId(), LocalDate.now(SAST))));
    }

    @PostMapping("/lesson-progress/{id}/reverse")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ProgressRecord>> reverse(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Record reversed", service.reverse(TenantContext.getTenantIdAsObject(), id, r.reason(), TenantContext.getCurrentUserId())));
    }
}
