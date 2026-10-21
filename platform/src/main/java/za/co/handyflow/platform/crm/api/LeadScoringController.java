package za.co.handyflow.platform.crm.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.crm.application.internal.LeadScoringService;
import za.co.handyflow.platform.crm.dto.LeadScoreResponse;
import za.co.handyflow.platform.crm.dto.ScoringRuleRequest;
import za.co.handyflow.platform.crm.dto.ScoringRuleResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@RestController
@RequestMapping("/api/v1/crm/scoring")
@RequiredArgsConstructor
@Tag(name = "CRM - Lead scoring", description = "Rules that add points to a lead, and each lead's score")
public class LeadScoringController {
    private final LeadScoringService service;

    @GetMapping("/rules")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "The scoring rules")
    public ResponseEntity<ApiResponse<List<ScoringRuleResponse>>> rules() {
        return ResponseEntity.ok(ApiResponse.success(service.rules(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/rules")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Add a scoring rule")
    public ResponseEntity<ApiResponse<ScoringRuleResponse>> create(@Valid @RequestBody ScoringRuleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.create(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/rules/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Change a scoring rule")
    public ResponseEntity<ApiResponse<ScoringRuleResponse>> update(@PathVariable UUID id, @Valid @RequestBody ScoringRuleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @DeleteMapping("/rules/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Delete a scoring rule")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/scores")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "Score, band and reasons for every active lead")
    public ResponseEntity<ApiResponse<List<LeadScoreResponse>>> scores() {
        return ResponseEntity.ok(ApiResponse.success(service.scores(TenantContext.getTenantIdAsObject())));
    }
}
