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
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvLearnerMatchService;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvLearnerMergeService;
import za.co.handyflow.platform.trainingprovider.dto.LearnerDtos.ReasonRequest;
import za.co.handyflow.platform.trainingprovider.dto.LearnerMatchDtos.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Learner matching and merging", description = "Duplicate learners, unmatched delegates and controlled merges")
public class TrainProvLearnerMergeController {

    private static final String READ = "hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String ADMIN = "hasAuthority('TRAININGPROVIDER_ADMIN')";

    private final TrainProvLearnerMatchService matching;
    private final TrainProvLearnerMergeService merging;
    private final FeatureGuard featureGuard;

    @PostMapping("/learners/check-duplicates")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<List<MatchView>>> check(@RequestBody MatchRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(matching.check(TenantContext.getTenantIdAsObject(), r)));
    }

    @GetMapping("/learners/{id}/possible-duplicates")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<MatchView>>> possible(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(matching.forLearner(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/learners/duplicate-pairs")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<PairView>>> pairs() {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(matching.pairs(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/learners/unmatched-delegates")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<UnmatchedDelegateView>>> unmatched() {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(matching.unmatchedDelegates(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/learners/{id}/history")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<LearnerEventView>>> history(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(matching.history(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/learner-merges")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<MergeView>>> list(@RequestParam(required = false) String status, @RequestParam(required = false) UUID learnerId) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(learnerId != null
                ? merging.forLearner(TenantContext.getTenantIdAsObject(), learnerId) : merging.list(TenantContext.getTenantIdAsObject(), status)));
    }

    @GetMapping("/learner-merges/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<MergeView>> get(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(merging.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/learner-merges")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<MergeView>> propose(@Valid @RequestBody MergeProposalRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Merge proposed",
                merging.propose(TenantContext.getTenantIdAsObject(), r, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/learner-merges/{id}/approve")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<MergeView>> approve(@PathVariable UUID id, @Valid @RequestBody MergeDecisionRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Learners merged",
                merging.approve(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/learner-merges/{id}/reject")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<MergeView>> reject(@PathVariable UUID id, @Valid @RequestBody MergeDecisionRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Merge rejected",
                merging.reject(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/learner-merges/{id}/cancel")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<MergeView>> cancel(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Merge cancelled",
                merging.cancel(TenantContext.getTenantIdAsObject(), id, r.reason(), TenantContext.getCurrentUserId())));
    }
}
