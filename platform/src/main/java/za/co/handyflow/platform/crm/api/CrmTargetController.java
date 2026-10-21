package za.co.handyflow.platform.crm.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.crm.application.internal.SalesTargetService;
import za.co.handyflow.platform.crm.dto.SetTargetRequest;
import za.co.handyflow.platform.crm.dto.TargetProgressRow;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/crm/targets")
@RequiredArgsConstructor
@Tag(name = "CRM - Sales targets", description = "Monthly targets per salesperson and progress against them")
public class CrmTargetController {

    private final SalesTargetService targetService;

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "Each salesperson's target and wins for a month (period=YYYY-MM)")
    public ResponseEntity<ApiResponse<List<TargetProgressRow>>> progress(@RequestParam String period) {
        return ResponseEntity.ok(ApiResponse.success(targetService.progress(TenantContext.getTenantIdAsObject(), period)));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Set or replace one salesperson's target for a month")
    public ResponseEntity<ApiResponse<Void>> set(@Valid @RequestBody SetTargetRequest request) {
        targetService.set(TenantContext.getTenantIdAsObject(), request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Remove one salesperson's target for a month")
    public ResponseEntity<ApiResponse<Void>> clear(@RequestParam UUID ownerId, @RequestParam String period) {
        targetService.clear(TenantContext.getTenantIdAsObject(), ownerId, period);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
