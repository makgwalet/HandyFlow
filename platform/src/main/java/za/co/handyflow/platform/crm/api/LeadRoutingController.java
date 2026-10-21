package za.co.handyflow.platform.crm.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.crm.application.internal.LeadRoutingService;
import za.co.handyflow.platform.crm.dto.RouteRunResponse;
import za.co.handyflow.platform.crm.dto.RoutingOrderRequest;
import za.co.handyflow.platform.crm.dto.RoutingRuleRequest;
import za.co.handyflow.platform.crm.dto.RoutingRuleResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@RestController
@RequestMapping("/api/v1/crm/routing")
@RequiredArgsConstructor
@Tag(name = "CRM - Lead routing", description = "Rules that hand new leads to people in turn")
public class LeadRoutingController {
    private final LeadRoutingService service;

    @GetMapping("/rules")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "The routing rules, in the order they are tried")
    public ResponseEntity<ApiResponse<List<RoutingRuleResponse>>> rules() {
        return ResponseEntity.ok(ApiResponse.success(service.rules(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/rules")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Add a routing rule (goes last)")
    public ResponseEntity<ApiResponse<RoutingRuleResponse>> create(@RequestBody RoutingRuleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.create(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/rules/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Change a routing rule")
    public ResponseEntity<ApiResponse<RoutingRuleResponse>> update(@PathVariable UUID id, @RequestBody RoutingRuleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @DeleteMapping("/rules/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Delete a routing rule")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/order")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Set the order the rules are tried in")
    public ResponseEntity<ApiResponse<List<RoutingRuleResponse>>> order(@RequestBody RoutingOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.reorder(TenantContext.getTenantIdAsObject(), request.ids())));
    }

    @PostMapping("/run")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Give every open lead without an owner to the first rule that fits")
    public ResponseEntity<ApiResponse<RouteRunResponse>> run() {
        return ResponseEntity.ok(ApiResponse.success(service.routeUnassigned(TenantContext.getTenantIdAsObject())));
    }
}
