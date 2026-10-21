package za.co.handyflow.platform.crm.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.crm.application.internal.SegmentService;
import za.co.handyflow.platform.crm.dto.SegmentMembersResponse;
import za.co.handyflow.platform.crm.dto.SegmentRequest;
import za.co.handyflow.platform.crm.dto.SegmentResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/crm/segments")
@RequiredArgsConstructor
@Tag(name = "CRM - Segments", description = "Saved groups of customers defined by conditions")
public class SegmentController {

    private final SegmentService service;

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "The account's segments, each with how many customers are in it now")
    public ResponseEntity<ApiResponse<List<SegmentResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Save a segment")
    public ResponseEntity<ApiResponse<SegmentResponse>> create(@RequestBody SegmentRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.create(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Rename a segment or change its conditions")
    public ResponseEntity<ApiResponse<SegmentResponse>> update(@PathVariable UUID id, @RequestBody SegmentRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Delete a segment (the customers in it are not touched)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Segment deleted", null));
    }

    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "Who would match these conditions, without saving them")
    public ResponseEntity<ApiResponse<SegmentMembersResponse>> preview(@RequestBody SegmentRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.preview(TenantContext.getTenantIdAsObject(), request)));
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "The customers in a segment (first 200, plus the total)")
    public ResponseEntity<ApiResponse<SegmentMembersResponse>> members(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.members(TenantContext.getTenantIdAsObject(), id)));
    }
}
