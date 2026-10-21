package za.co.handyflow.platform.crm.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.crm.application.internal.OpportunityService;
import za.co.handyflow.platform.crm.dto.OpportunityRequest;
import za.co.handyflow.platform.crm.dto.OpportunityResponse;
import za.co.handyflow.platform.crm.dto.OpportunityStageRequest;
import za.co.handyflow.platform.crm.dto.OpportunitySummaryResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@RestController
@RequestMapping("/api/v1/crm/opportunities")
@RequiredArgsConstructor
@Tag(name = "CRM - Opportunities", description = "Deals with a company, each with its own stage")
public class OpportunityController {

    private final OpportunityService service;

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "List opportunities; narrow to one company with customerId, or to open ones with openOnly=true")
    public ResponseEntity<ApiResponse<Page<OpportunityResponse>>> list(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "false") boolean openOnly,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), customerId, openOnly, pageable)));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "Open pipeline, weighted forecast and wins across all opportunities")
    public ResponseEntity<ApiResponse<OpportunitySummaryResponse>> summary() {
        return ResponseEntity.ok(ApiResponse.success(service.summary(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Create an opportunity for a company (starts in Prospecting)")
    public ResponseEntity<ApiResponse<OpportunityResponse>> create(@Valid @RequestBody OpportunityRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.create(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Edit an opportunity's details")
    public ResponseEntity<ApiResponse<OpportunityResponse>> update(@PathVariable UUID id, @Valid @RequestBody OpportunityRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PatchMapping("/{id}/stage")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Move an opportunity to another stage (a lost reason is required to close as lost)")
    public ResponseEntity<ApiResponse<OpportunityResponse>> stage(@PathVariable UUID id, @Valid @RequestBody OpportunityStageRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.changeStage(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Delete an opportunity")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
