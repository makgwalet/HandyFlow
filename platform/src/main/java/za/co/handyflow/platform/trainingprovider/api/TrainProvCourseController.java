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
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvCourseService;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvCourse;
import za.co.handyflow.platform.trainingprovider.dto.CourseResponse;
import za.co.handyflow.platform.trainingprovider.dto.UpsertCourseRequest;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider/courses")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Courses", description = "Accredited course catalogue")
public class TrainProvCourseController {

    private final TrainProvCourseService courseService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<Page<CourseResponse>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(
                courseService.list(TenantContext.getTenantIdAsObject(), status, search, pageable).map(this::toResponse)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<CourseResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(toResponse(courseService.get(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<CourseResponse>> create(@Valid @RequestBody UpsertCourseRequest req) {
        featureGuard.requireModule("trainingprovider");
        TrainProvCourse course = courseService.create(TenantContext.getTenantIdAsObject(), req.title(), req.description(),
                req.unitStandardNumber(), req.nqfLevel(), req.credits(), req.durationDays(), req.pricePerDelegate(),
                req.certificationOffered(), req.certificateValidityMonths());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Course created", toResponse(course)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<CourseResponse>> update(@PathVariable UUID id, @Valid @RequestBody UpsertCourseRequest req) {
        featureGuard.requireModule("trainingprovider");
        TrainProvCourse course = courseService.update(TenantContext.getTenantIdAsObject(), id, req.title(), req.description(),
                req.unitStandardNumber(), req.nqfLevel(), req.credits(), req.durationDays(), req.pricePerDelegate(),
                req.certificationOffered(), req.certificateValidityMonths());
        return ResponseEntity.ok(ApiResponse.success("Course updated", toResponse(course)));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<CourseResponse>> archive(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Course archived",
                toResponse(courseService.archive(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<CourseResponse>> reactivate(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Course reactivated",
                toResponse(courseService.reactivate(TenantContext.getTenantIdAsObject(), id))));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('TRAININGPROVIDER_ADMIN')")
    @Operation(summary = "Soft-delete a course — ADMIN only")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        courseService.softDelete(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Course deleted", null));
    }

    private CourseResponse toResponse(TrainProvCourse c) {
        return new CourseResponse(c.getId(), c.getCourseCode(), c.getTitle(), c.getDescription(), c.getUnitStandardNumber(),
                c.getNqfLevel(), c.getCredits(), c.getDurationDays(), c.getPricePerDelegate(), c.isCertificationOffered(),
                c.getCertificateValidityMonths(), c.getStatus(), c.getCreatedAt());
    }
}
