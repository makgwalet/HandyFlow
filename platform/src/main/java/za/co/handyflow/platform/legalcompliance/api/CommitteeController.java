package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.CommitteeService;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.ReasonRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Governance committees: members, meetings, minutes, decisions and actions */
@RestController
@RequestMapping("/api/v1/legalcompliance/committees")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Governance committees", description = "Governance committees: members, meetings, minutes, decisions and actions")
public class CommitteeController {

    private static final String CAN_READ = "hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_GOVERNANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_MANAGE = "hasAnyAuthority('LEGALCOMPLIANCE_GOVERNANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')";

    private final CommitteeService committees;
    private final FeatureGuard featureGuard;

    @GetMapping("")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<CommitteeResponse>>> list(@RequestParam(required = false) CommitteeStatus status) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(committees.list(tenant(), status)));
    }

    @GetMapping("/summary")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<GovernanceSummary>> summary() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(committees.summary(tenant())));
    }

    @GetMapping("/actions")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<ResolutionResponse>>> actions(@RequestParam(defaultValue = "true") boolean open) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(committees.actions(tenant(), open)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<CommitteeDetail>> get(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(committees.get(tenant(), id)));
    }

    @GetMapping("/{id}/meetings/{mid}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<MeetingDetail>> meeting(@PathVariable UUID id, @PathVariable UUID mid) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(committees.getMeeting(tenant(), id, mid)));
    }

    @PostMapping("")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<CommitteeDetail>> create(@Valid @RequestBody CommitteeRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Committee created", committees.create(tenant(), req, uid(), uname())));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<CommitteeDetail>> update(@PathVariable UUID id, @Valid @RequestBody CommitteeRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Committee updated", committees.update(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/charter-review")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<CommitteeDetail>> reviewCharter(@PathVariable UUID id, @Valid @RequestBody CharterReviewRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Charter review recorded", committees.reviewCharter(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/dissolve")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<CommitteeDetail>> dissolve(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Committee dissolved", committees.dissolve(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<CommitteeDetail>> addMember(@PathVariable UUID id, @Valid @RequestBody MemberRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Member added", committees.addMember(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/members/{memberId}/end")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<CommitteeDetail>> endMember(@PathVariable UUID id, @PathVariable UUID memberId, @Valid @RequestBody EndMemberRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Membership ended", committees.endMember(tenant(), id, memberId, req, uid(), uname())));
    }

    @PostMapping("/{id}/meetings")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<MeetingDetail>> schedule(@PathVariable UUID id, @Valid @RequestBody MeetingRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Meeting scheduled", committees.scheduleMeeting(tenant(), id, req, uid(), uname())));
    }

    @PutMapping("/{id}/meetings/{mid}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<MeetingDetail>> updateMeeting(@PathVariable UUID id, @PathVariable UUID mid, @Valid @RequestBody MeetingRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Meeting updated", committees.updateMeeting(tenant(), id, mid, req, uid(), uname())));
    }

    @PostMapping("/{id}/meetings/{mid}/cancel")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<MeetingDetail>> cancel(@PathVariable UUID id, @PathVariable UUID mid, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Meeting cancelled", committees.cancelMeeting(tenant(), id, mid, req, uid(), uname())));
    }

    @PostMapping("/{id}/meetings/{mid}/hold")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<MeetingDetail>> hold(@PathVariable UUID id, @PathVariable UUID mid, @Valid @RequestBody HoldRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Meeting recorded", committees.holdMeeting(tenant(), id, mid, req, uid(), uname())));
    }

    @PostMapping("/{id}/meetings/{mid}/minutes")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<MeetingDetail>> minutes(@PathVariable UUID id, @PathVariable UUID mid, @Valid @RequestBody MinutesRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Minutes saved", committees.recordMinutes(tenant(), id, mid, req, uid(), uname())));
    }

    @PostMapping("/{id}/meetings/{mid}/minutes/approve")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<MeetingDetail>> approveMinutes(@PathVariable UUID id, @PathVariable UUID mid) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Minutes approved", committees.approveMinutes(tenant(), id, mid, uid(), uname())));
    }

    @PostMapping("/{id}/meetings/{mid}/resolutions")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<MeetingDetail>> addResolution(@PathVariable UUID id, @PathVariable UUID mid, @Valid @RequestBody ResolutionRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Recorded", committees.addResolution(tenant(), id, mid, req, uid(), uname())));
    }

    @PutMapping("/{id}/meetings/{mid}/resolutions/{rid}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<MeetingDetail>> updateResolution(@PathVariable UUID id, @PathVariable UUID mid, @PathVariable UUID rid, @Valid @RequestBody ResolutionRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Updated", committees.updateResolution(tenant(), id, mid, rid, req, uid(), uname())));
    }

    @DeleteMapping("/{id}/meetings/{mid}/resolutions/{rid}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<MeetingDetail>> removeResolution(@PathVariable UUID id, @PathVariable UUID mid, @PathVariable UUID rid) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Removed", committees.removeResolution(tenant(), id, mid, rid, uid(), uname())));
    }

    @PostMapping("/{id}/meetings/{mid}/resolutions/{rid}/complete")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<MeetingDetail>> complete(@PathVariable UUID id, @PathVariable UUID mid, @PathVariable UUID rid, @Valid @RequestBody CompleteRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Action completed", committees.completeAction(tenant(), id, mid, rid, req, uid(), uname())));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
