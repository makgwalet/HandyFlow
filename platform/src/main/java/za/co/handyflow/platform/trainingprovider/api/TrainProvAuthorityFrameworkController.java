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
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvAuthorityFrameworkService;
import za.co.handyflow.platform.trainingprovider.dto.AccreditationDtos.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider/accreditation")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Authority framework", description = "Authorities in order, accreditation currency, source and evidence")
public class TrainProvAuthorityFrameworkController {

    private static final String READ = "hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final TrainProvAuthorityFrameworkService service;
    private final FeatureGuard featureGuard;

    private static LocalDate today() {
        return LocalDate.now(SAST);
    }

    @GetMapping("/framework")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<FrameworkAuthority>>> overview() {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.overview(TenantContext.getTenantIdAsObject(), today())));
    }

    @PutMapping("/accreditations/{id}/framework")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<FrameworkRow>> framework(@PathVariable UUID id, @Valid @RequestBody FrameworkRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Dates and source saved",
                service.updateFramework(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/accreditations/{id}/currency")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<FrameworkRow>> currency(@PathVariable UUID id, @Valid @RequestBody CurrencyRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Saved",
                service.changeCurrency(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId(), today())));
    }

    @GetMapping("/accreditations/{id}/evidence")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<EvidenceView>>> evidence(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.evidenceFor(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/accreditations/{id}/evidence")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<EvidenceView>> addEvidence(@PathVariable UUID id, @Valid @RequestBody EvidenceRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Evidence recorded as pending",
                service.addEvidence(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/evidence/{evidenceId}/decide")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<EvidenceView>> decide(@PathVariable UUID evidenceId, @Valid @RequestBody EvidenceDecisionRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Evidence updated",
                service.decideEvidence(TenantContext.getTenantIdAsObject(), evidenceId, r, TenantContext.getCurrentUserId())));
    }
}
