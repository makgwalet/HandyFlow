package za.co.handyflow.platform.marketing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.marketing.application.internal.MarketingSegmentService;
import za.co.handyflow.platform.marketing.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/marketing/segments")
@RequiredArgsConstructor
@Tag(name = "Marketing Audiences", description = "Segments, static lists and audience previews")
public class MarketingSegmentController {

    private final MarketingSegmentService segments;

    @GetMapping
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    @Operation(summary = "List segments")
    public ResponseEntity<ApiResponse<List<SegmentResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(segments.list(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    @Operation(summary = "Get a segment")
    public ResponseEntity<ApiResponse<SegmentResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(segments.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    @Operation(summary = "Create a dynamic segment (rules) or a static list")
    public ResponseEntity<ApiResponse<SegmentResponse>> create(@Valid @RequestBody SegmentRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Segment created",
                segments.create(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    @Operation(summary = "Update a segment. Campaigns that have not launched yet pick up the change.")
    public ResponseEntity<ApiResponse<SegmentResponse>> update(@PathVariable UUID id, @Valid @RequestBody SegmentRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Segment updated",
                segments.update(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), id, req)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    @Operation(summary = "Delete a segment (409 while an unfinished campaign uses it)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        segments.delete(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Segment deleted", null));
    }

    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    @Operation(summary = "Preview an unsaved set of rules: matched / not opted in / suppressed / eligible + sample")
    public ResponseEntity<ApiResponse<AudiencePreviewResponse>> previewRules(@Valid @RequestBody PreviewRequest req) {
        return ResponseEntity.ok(ApiResponse.success(
                segments.previewDefinition(TenantContext.getTenantIdAsObject(), req.definition())));
    }

    @PostMapping("/{id}/preview")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    @Operation(summary = "Preview a saved segment as it would resolve right now (also refreshes its stored count)")
    public ResponseEntity<ApiResponse<AudiencePreviewResponse>> previewSaved(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(segments.previewSaved(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/refresh")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    @Operation(summary = "Recompute a segment's eligible count")
    public ResponseEntity<ApiResponse<SegmentResponse>> refresh(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(segments.refreshCount(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    @Operation(summary = "Members of a static list")
    public ResponseEntity<ApiResponse<Page<SegmentMemberResponse>>> members(@PathVariable UUID id, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(segments.listMembers(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    @Operation(summary = "Add existing contacts to a static list by email or contact id")
    public ResponseEntity<ApiResponse<AddMembersResult>> addMembers(@PathVariable UUID id,
                                                                    @RequestBody AddSegmentMembersRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Members processed",
                segments.addMembers(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), id, req)));
    }

    @DeleteMapping("/{id}/members/{preferenceId}")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    @Operation(summary = "Remove a contact from a static list")
    public ResponseEntity<ApiResponse<Void>> removeMember(@PathVariable UUID id, @PathVariable UUID preferenceId) {
        segments.removeMember(TenantContext.getTenantIdAsObject(), id, preferenceId);
        return ResponseEntity.ok(ApiResponse.success("Member removed", null));
    }
}