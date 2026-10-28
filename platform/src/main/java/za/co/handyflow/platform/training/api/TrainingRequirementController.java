package za.co.handyflow.platform.training.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.training.application.internal.TrainingRequirementService;
import za.co.handyflow.platform.training.dto.TrainingRequirementDtos.Matrix;
import za.co.handyflow.platform.training.dto.TrainingRequirementDtos.Requirement;
import za.co.handyflow.platform.training.dto.TrainingRequirementDtos.RequirementRequest;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/** Required training: which certificates each role must hold, and who holds them. */
@RestController
@RequestMapping("/api/v1/training")
@RequiredArgsConstructor
@Tag(name = "Training - Required training", description = "Required-training matrix")
public class TrainingRequirementController {

    private final TrainingRequirementService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/requirements")
    @PreAuthorize("hasAnyAuthority('TRAINING_READ','TRAINING_MANAGE','TRAINING_ADMIN')")
    public ResponseEntity<ApiResponse<List<Requirement>>> list() {
        featureGuard.requireModule("training");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/requirements")
    @PreAuthorize("hasAnyAuthority('TRAINING_MANAGE','TRAINING_ADMIN')")
    public ResponseEntity<ApiResponse<Requirement>> add(@Valid @RequestBody RequirementRequest req, Principal principal) {
        featureGuard.requireModule("training");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Requirement added",
                service.add(TenantContext.getTenantIdAsObject(), req.courseId(), req.jobTitle(), req.department(), user(principal))));
    }

    @DeleteMapping("/requirements/{id}")
    @PreAuthorize("hasAnyAuthority('TRAINING_MANAGE','TRAINING_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable UUID id) {
        featureGuard.requireModule("training");
        service.remove(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Requirement removed", null));
    }

    @GetMapping("/matrix")
    @PreAuthorize("hasAnyAuthority('TRAINING_READ','TRAINING_MANAGE','TRAINING_ADMIN')")
    public ResponseEntity<ApiResponse<Matrix>> matrix() {
        featureGuard.requireModule("training");
        return ResponseEntity.ok(ApiResponse.success(service.matrix(TenantContext.getTenantIdAsObject())));
    }

    private static UUID user(Principal principal) {
        try { return UUID.fromString(principal.getName()); } catch (RuntimeException e) { return null; }
    }
}
