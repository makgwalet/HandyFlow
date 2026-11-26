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
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvLearnershipService;
import za.co.handyflow.platform.trainingprovider.dto.LearnershipDtos.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider/learnership-agreements")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Learnerships", description = "Records of learnership agreements")
public class TrainProvLearnershipController {

    private static final String READ = "hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final TrainProvLearnershipService service;
    private final FeatureGuard featureGuard;

    private static LocalDate today() {
        return LocalDate.now(SAST);
    }

    @GetMapping
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<AgreementView>>> list(@RequestParam(required = false) UUID enrolmentId, @RequestParam(required = false) String status) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), enrolmentId, status)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<AgreementView>> get(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AgreementView>> create(@Valid @RequestBody AgreementRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Agreement added", service.create(TenantContext.getTenantIdAsObject(), r, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AgreementView>> update(@PathVariable UUID id, @Valid @RequestBody AgreementUpdate r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Agreement updated", service.update(TenantContext.getTenantIdAsObject(), id, r)));
    }

    @PostMapping("/{id}/sign")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AgreementView>> sign(@PathVariable UUID id, @Valid @RequestBody SignRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Signing recorded", service.sign(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/{id}/registration")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AgreementView>> registration(@PathVariable UUID id, @Valid @RequestBody RegistrationRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Reference recorded", service.recordRegistration(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/{id}/end")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AgreementView>> end(@PathVariable UUID id, @Valid @RequestBody EndRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Agreement ended", service.end(TenantContext.getTenantIdAsObject(), id, r, today())));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AgreementView>> cancel(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Agreement cancelled", service.cancel(TenantContext.getTenantIdAsObject(), id, r.reason())));
    }
}
