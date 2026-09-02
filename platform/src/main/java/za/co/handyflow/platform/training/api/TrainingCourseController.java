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
import za.co.handyflow.platform.training.application.internal.TrainingCourseService;
import za.co.handyflow.platform.training.domain.model.TrainingCourse;
import za.co.handyflow.platform.training.dto.CourseResponse;
import za.co.handyflow.platform.training.dto.UpsertCourseRequest;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training/courses")
@RequiredArgsConstructor
@Tag(name = "Training - Courses", description = "Course catalogue management")
public class TrainingCourseController {

    private final TrainingCourseService courseService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('TRAINING_READ','TRAINING_MANAGE','TRAINING_ADMIN')")
    public ResponseEntity<ApiResponse<Page<CourseResponse>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("training");
        return ResponseEntity.ok(ApiResponse.success(
                courseService.list(TenantContext.getTenantIdAsObject(), status, search, pageable).map(this::toResponse)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('TRAINING_READ','TRAINING_MANAGE','TRAINING_ADMIN')")
    public ResponseEntity<ApiResponse<CourseResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("training");
        return ResponseEntity.ok(ApiResponse.success(
                toResponse(courseService.get(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('TRAINING_MANAGE','TRAINING_ADMIN')")
    @Operation(summary = "Add a course to the catalogue")
    public ResponseEntity<ApiResponse<CourseResponse>> create(@Valid @RequestBody UpsertCourseRequest req) {
        featureGuard.requireModule("training");
        TrainingCourse course = courseService.create(TenantContext.getTenantIdAsObject(), req.title(), req.description(),
                req.category(), req.deliveryMode(), req.durationHours(), req.defaultTrainerName(), req.cost(),
                req.certificationOffered(), req.certificateValidityMonths());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Course created", toResponse(course)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('TRAINING_MANAGE','TRAINING_ADMIN')")
    public ResponseEntity<ApiResponse<CourseResponse>> update(@PathVariable UUID id, @Valid @RequestBody UpsertCourseRequest req) {
        featureGuard.requireModule("training");
        TrainingCourse course = courseService.update(TenantContext.getTenantIdAsObject(), id, req.title(), req.description(),
                req.category(), req.deliveryMode(), req.durationHours(), req.defaultTrainerName(), req.cost(),
                req.certificationOffered(), req.certificateValidityMonths());
        return ResponseEntity.ok(ApiResponse.success("Course updated", toResponse(course)));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyAuthority('TRAINING_MANAGE','TRAINING_ADMIN')")
    public ResponseEntity<ApiResponse<CourseResponse>> archive(@PathVariable UUID id) {
        featureGuard.requireModule("training");
        return ResponseEntity.ok(ApiResponse.success("Course archived",
                toResponse(courseService.archive(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAnyAuthority('TRAINING_MANAGE','TRAINING_ADMIN')")
    public ResponseEntity<ApiResponse<CourseResponse>> reactivate(@PathVariable UUID id) {
        featureGuard.requireModule("training");
        return ResponseEntity.ok(ApiResponse.success("Course reactivated",
                toResponse(courseService.reactivate(TenantContext.getTenantIdAsObject(), id))));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('TRAINING_ADMIN')")
    @Operation(summary = "Soft-delete a course — ADMIN only, matches every other provider/internal module's irreversible-action gating")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        featureGuard.requireModule("training");
        courseService.softDelete(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Course deleted", null));
    }

    private CourseResponse toResponse(TrainingCourse c) {
        return new CourseResponse(c.getId(), c.getCourseCode(), c.getTitle(), c.getDescription(), c.getCategory(),
                c.getDeliveryMode(), c.getDurationHours(), c.getDefaultTrainerName(), c.getCost(),
                c.isCertificationOffered(), c.getCertificateValidityMonths(), c.getStatus(), c.getCreatedAt());
    }
}
