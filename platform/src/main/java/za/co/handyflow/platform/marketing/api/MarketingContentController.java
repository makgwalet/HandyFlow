package za.co.handyflow.platform.marketing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.marketing.application.internal.MarketingContentService;
import za.co.handyflow.platform.marketing.dto.StudioDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/marketing")
@RequiredArgsConstructor
@Tag(name = "Marketing Content Studio", description = "Content checker, preview and reusable blocks")
public class MarketingContentController {

    private final MarketingContentService content;

    @PostMapping("/content/lint")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    @Operation(summary = "Static content check (no URLs are fetched)")
    public ResponseEntity<ApiResponse<List<LintIssue>>> lint(@Valid @RequestBody LintRequest req) {
        return ResponseEntity.ok(ApiResponse.success(content.lint(req.subject(), req.htmlBody())));
    }

    @PostMapping("/content/preview")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    @Operation(summary = "Render subject and body exactly as a send would, using a sample contact")
    public ResponseEntity<ApiResponse<ContentPreviewResponse>> preview(@Valid @RequestBody ContentPreviewRequest req) {
        return ResponseEntity.ok(ApiResponse.success(content.preview(TenantContext.getTenantIdAsObject(), req)));
    }

    @GetMapping("/blocks")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    public ResponseEntity<ApiResponse<List<BlockResponse>>> blocks() {
        return ResponseEntity.ok(ApiResponse.success(content.listBlocks(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/blocks")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    public ResponseEntity<ApiResponse<BlockResponse>> createBlock(@Valid @RequestBody BlockRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Block created",
                content.createBlock(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), req)));
    }

    @PutMapping("/blocks/{id}")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    public ResponseEntity<ApiResponse<BlockResponse>> updateBlock(@PathVariable UUID id, @Valid @RequestBody BlockRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Block updated", content.updateBlock(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @DeleteMapping("/blocks/{id}")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deleteBlock(@PathVariable UUID id) {
        content.deleteBlock(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Block deleted", null));
    }
}