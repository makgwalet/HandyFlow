package za.co.handyflow.platform.accounting.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.accounting.application.internal.CostCentreService;
import za.co.handyflow.platform.accounting.dto.CostCentreDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Cost centres, departments, branches and projects, and profit and loss by cost centre (finance F24). */
@RestController
@RequestMapping("/api/v1/accounting/cost-centres")
@RequiredArgsConstructor
@Tag(name = "Cost centres")
public class CostCentreController {

    private final CostCentreService service;

    @GetMapping
    @PreAuthorize("hasAuthority('ACCOUNTING_READ')")
    public ResponseEntity<ApiResponse<List<CostCentre>>> list() {
        return ResponseEntity.ok(ApiResponse.success("Success", service.list(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    public ResponseEntity<ApiResponse<CostCentre>> create(@RequestBody Save req) {
        return ResponseEntity.ok(ApiResponse.success("Created", service.create(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    public ResponseEntity<ApiResponse<CostCentre>> update(@PathVariable UUID id, @RequestBody Save req) {
        return ResponseEntity.ok(ApiResponse.success("Saved", service.update(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/{id}/active")
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    @Operation(summary = "Switch a cost centre on or off. Switched-off ones keep their history but cannot be used on new journals")
    public ResponseEntity<ApiResponse<CostCentre>> setActive(@PathVariable UUID id, @RequestBody SetActive req) {
        return ResponseEntity.ok(ApiResponse.success("Saved", service.setActive(TenantContext.getTenantIdAsObject(), id, req.active())));
    }

    @GetMapping("/report")
    @PreAuthorize("hasAuthority('ACCOUNTING_READ')")
    @Operation(summary = "Profit and loss with a column per cost centre")
    public ResponseEntity<ApiResponse<ByCentreReport>> report(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "true") boolean rollUp,
            @RequestParam(required = false) String kind) {
        return ResponseEntity.ok(ApiResponse.success("Success", service.profitAndLoss(TenantContext.getTenantIdAsObject(), from, to, rollUp, kind)));
    }

    @GetMapping("/{id}/lines")
    @PreAuthorize("hasAuthority('ACCOUNTING_READ')")
    @Operation(summary = "The journal lines behind one cost centre and everything beneath it")
    public ResponseEntity<ApiResponse<CentreDetail>> lines(@PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("Success", service.detail(TenantContext.getTenantIdAsObject(), id, from, to)));
    }
}
