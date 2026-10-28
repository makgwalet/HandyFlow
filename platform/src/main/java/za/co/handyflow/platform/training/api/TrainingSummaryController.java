package za.co.handyflow.platform.training.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.training.application.internal.TrainingSummaryService;
import za.co.handyflow.platform.training.domain.model.TrainingAuditRules;
import za.co.handyflow.platform.training.dto.TrainingSummaryDtos.Summary;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/training/summary")
@RequiredArgsConstructor
@Tag(name = "Training", description = "Dashboard figures")
public class TrainingSummaryController {

    private final TrainingSummaryService summaries;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('TRAINING_READ','TRAINING_MANAGE','TRAINING_ADMIN')")
    @Operation(summary = "Counts and short lists for the dashboard")
    public ResponseEntity<ApiResponse<Summary>> summary() {
        featureGuard.requireModule("training");
        return ResponseEntity.ok(ApiResponse.success(summaries.summary(TenantContext.getTenantIdAsObject(), LocalDate.now(TrainingAuditRules.ZONE))));
    }
}
