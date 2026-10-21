package za.co.handyflow.platform.crm.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.crm.application.internal.CustomerMergeService;
import za.co.handyflow.platform.crm.dto.MergePreviewResponse;
import za.co.handyflow.platform.crm.dto.MergeRequest;
import za.co.handyflow.platform.crm.dto.MergeResultResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@RestController
@RequestMapping("/api/v1/crm/customer-merge")
@RequiredArgsConstructor
@Tag(name = "CRM - Merge", description = "Fold one duplicate customer record into another")
public class CustomerMergeController {
    private final CustomerMergeService service;

    @GetMapping("/preview")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "What merging would do: the fields that differ, what moves, and anything that blocks it")
    public ResponseEntity<ApiResponse<MergePreviewResponse>> preview(@RequestParam UUID keepId, @RequestParam UUID mergeId) {
        return ResponseEntity.ok(ApiResponse.success(service.preview(TenantContext.getTenantIdAsObject(), keepId, mergeId)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Merge the second customer into the first and remove it")
    public ResponseEntity<ApiResponse<MergeResultResponse>> merge(@RequestBody MergeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.merge(TenantContext.getTenantIdAsObject(), request)));
    }
}
