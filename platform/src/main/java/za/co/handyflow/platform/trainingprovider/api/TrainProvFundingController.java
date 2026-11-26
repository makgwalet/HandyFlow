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
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvFundingService;
import za.co.handyflow.platform.trainingprovider.dto.FundingDtos.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Funding", description = "Funders and the enrolments they pay for")
public class TrainProvFundingController {

    private static final String READ = "hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final TrainProvFundingService service;
    private final FeatureGuard featureGuard;

    private static LocalDate today() {
        return LocalDate.now(SAST);
    }

    @GetMapping("/funders")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<FunderView>>> listFunders(@RequestParam(required = false) String type, @RequestParam(required = false) String status) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.listFunders(TenantContext.getTenantIdAsObject(), type, status)));
    }

    @GetMapping("/funders/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<FunderView>> getFunder(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.getFunder(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/funders")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<FunderView>> createFunder(@Valid @RequestBody FunderRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Funder added", service.createFunder(TenantContext.getTenantIdAsObject(), r, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/funders/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<FunderView>> updateFunder(@PathVariable UUID id, @Valid @RequestBody FunderRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Funder updated", service.updateFunder(TenantContext.getTenantIdAsObject(), id, r)));
    }

    @PostMapping("/funders/{id}/retire")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<FunderView>> retireFunder(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Funder retired", service.retireFunder(TenantContext.getTenantIdAsObject(), id, r.reason(), TenantContext.getCurrentUserId())));
    }

    @GetMapping("/enrolment-funding")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<LinkView>>> listLinks(@RequestParam(required = false) UUID enrolmentId, @RequestParam(required = false) UUID funderId,
                                                                 @RequestParam(required = false) String status) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.listLinks(TenantContext.getTenantIdAsObject(), enrolmentId, funderId, status)));
    }

    @GetMapping("/learner-enrolments/{id}/funding")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<EnrolmentFunding>> forEnrolment(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.forEnrolment(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/enrolment-funding")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<LinkView>> link(@Valid @RequestBody LinkRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Funder linked", service.link(TenantContext.getTenantIdAsObject(), r, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/enrolment-funding/{id}/end")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<LinkView>> end(@PathVariable UUID id, @Valid @RequestBody EndRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Funding link ended", service.end(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId(), today())));
    }
}
