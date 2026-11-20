package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.ResolutionVotingService;
import za.co.handyflow.platform.legalcompliance.dto.VotingDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Votes on committee decisions. Reading follows the committee screens; recording and closing need governance management. */
@RestController
@RequestMapping("/api/v1/legalcompliance/committees/{id}/meetings/{mid}")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Committee voting", description = "Record votes on a decision and close voting")
public class ResolutionVotingController {

    private static final String CAN_READ = "hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_GOVERNANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_MANAGE = "hasAnyAuthority('LEGALCOMPLIANCE_GOVERNANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')";

    private final ResolutionVotingService voting;
    private final FeatureGuard featureGuard;

    @GetMapping("/ballots")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<BallotSummary>>> ballots(@PathVariable UUID id, @PathVariable UUID mid) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(voting.forMeeting(tenant(), id, mid)));
    }

    @GetMapping("/resolutions/{rid}/votes")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<VotingView>> view(@PathVariable UUID id, @PathVariable UUID mid, @PathVariable UUID rid) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(voting.view(tenant(), id, mid, rid)));
    }

    @PutMapping("/resolutions/{rid}/votes")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<VotingView>> cast(@PathVariable UUID id, @PathVariable UUID mid, @PathVariable UUID rid, @Valid @RequestBody CastRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Votes recorded", voting.cast(tenant(), id, mid, rid, req, uid(), uname())));
    }

    @PutMapping("/resolutions/{rid}/votes/type")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<VotingView>> type(@PathVariable UUID id, @PathVariable UUID mid, @PathVariable UUID rid, @Valid @RequestBody TypeRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Type set", voting.setType(tenant(), id, mid, rid, req.type(), uid(), uname())));
    }

    @PostMapping("/resolutions/{rid}/votes/close")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<VotingView>> close(@PathVariable UUID id, @PathVariable UUID mid, @PathVariable UUID rid) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Voting closed", voting.close(tenant(), id, mid, rid, uid(), uname())));
    }

    @PostMapping("/resolutions/{rid}/votes/close-with-casting-vote")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<VotingView>> closeWithCastingVote(@PathVariable UUID id, @PathVariable UUID mid, @PathVariable UUID rid, @Valid @RequestBody CastingVoteRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Voting closed on the chair's casting vote", voting.closeWithCastingVote(tenant(), id, mid, rid, req, uid(), uname())));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
