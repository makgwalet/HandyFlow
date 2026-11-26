package za.co.handyflow.platform.trainingprovider.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvEnrolmentService;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvLearnerService;
import za.co.handyflow.platform.trainingprovider.dto.LearnerDtos.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Learners", description = "Learners, applications and learner enrolments")
public class TrainProvLearnerController {

    private static final String READ = "hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String ADMIN = "hasAuthority('TRAININGPROVIDER_ADMIN')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final TrainProvLearnerService learners;
    private final TrainProvEnrolmentService enrolments;
    private final FeatureGuard featureGuard;

    private static LocalDate today() {
        return LocalDate.now(SAST);
    }

    private static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> "TRAININGPROVIDER_ADMIN".equals(a.getAuthority()));
    }

    // learners

    @GetMapping("/learners")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<LearnerView>>> list(@RequestParam(required = false) String status, @RequestParam(required = false) UUID clientId,
                                                               @RequestParam(required = false) String q) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(learners.list(TenantContext.getTenantIdAsObject(), status, clientId, q)));
    }

    @GetMapping("/learners/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<LearnerDetail>> detail(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(learners.detail(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/learners")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<LearnerView>> create(@Valid @RequestBody LearnerRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Learner added",
                learners.create(TenantContext.getTenantIdAsObject(), r, TenantContext.getCurrentUserId(), today())));
    }

    @PutMapping("/learners/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<LearnerView>> update(@PathVariable UUID id, @Valid @RequestBody LearnerRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Learner updated", learners.update(TenantContext.getTenantIdAsObject(), id, r, today(), TenantContext.getCurrentUserId())));
    }

    @PutMapping("/learners/{id}/delegate")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<LearnerView>> linkDelegate(@PathVariable UUID id, @RequestBody DelegateLinkRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(r.delegateId() == null ? "Delegate unlinked" : "Delegate linked",
                learners.linkDelegate(TenantContext.getTenantIdAsObject(), id, r.delegateId(), r.reason(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/learners/{id}/archive")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<LearnerView>> archive(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Learner archived",
                learners.archive(TenantContext.getTenantIdAsObject(), id, r.reason(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/learners/{id}/restore")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<LearnerView>> restore(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Learner restored", learners.restore(TenantContext.getTenantIdAsObject(), id)));
    }

    // applications

    @GetMapping("/applications")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<ApplicationView>>> applications(@RequestParam(required = false) String status,
                                                                          @RequestParam(required = false) UUID learnerId,
                                                                          @RequestParam(required = false) UUID programmeId) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(enrolments.applications(TenantContext.getTenantIdAsObject(), status, learnerId, programmeId)));
    }

    @GetMapping("/applications/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<ApplicationDetail>> application(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(enrolments.application(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/applications")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ApplicationView>> apply(@Valid @RequestBody ApplicationRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Application recorded",
                enrolments.apply(TenantContext.getTenantIdAsObject(), r, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/applications/{id}/transition")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ApplicationView>> transitionApplication(@PathVariable UUID id, @Valid @RequestBody ApplicationTransitionRequest r,
                                                                             Authentication auth) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Application updated",
                enrolments.transitionApplication(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId(), isAdmin(auth))));
    }

    // learner enrolments

    @GetMapping("/learner-enrolments")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<EnrolmentView>>> enrolments(@RequestParam(required = false) String status,
                                                                      @RequestParam(required = false) UUID learnerId,
                                                                      @RequestParam(required = false) UUID programmeId) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(enrolments.enrolments(TenantContext.getTenantIdAsObject(), status, learnerId, programmeId)));
    }

    @GetMapping("/learner-enrolments/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<EnrolmentDetail>> enrolment(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(enrolments.enrolment(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/learner-enrolments")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<EnrolmentView>> enrol(@Valid @RequestBody EnrolRequest r, Authentication auth) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Learner enrolled",
                enrolments.enrol(TenantContext.getTenantIdAsObject(), r, TenantContext.getCurrentUserId(), isAdmin(auth), today())));
    }

    @PostMapping("/learner-enrolments/{id}/transition")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<EnrolmentView>> transitionEnrolment(@PathVariable UUID id, @Valid @RequestBody EnrolmentTransitionRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Enrolment updated",
                enrolments.transitionEnrolment(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId())));
    }
}
