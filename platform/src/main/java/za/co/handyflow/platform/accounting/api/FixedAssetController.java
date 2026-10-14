package za.co.handyflow.platform.accounting.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.accounting.application.internal.FixedAssetService;
import za.co.handyflow.platform.accounting.dto.FixedAssetDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Fixed-asset register, depreciation and disposals (finance F25). */
@RestController
@RequestMapping("/api/v1/accounting/fixed-assets")
@RequiredArgsConstructor
@Tag(name = "Fixed assets")
public class FixedAssetController {

    private final FixedAssetService service;

    @GetMapping
    @PreAuthorize("hasAuthority('ACCOUNTING_READ')")
    public ResponseEntity<ApiResponse<List<Asset>>> list() {
        return ResponseEntity.ok(ApiResponse.success("Success", service.list(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    @Operation(summary = "Add an asset to the register. This does not post a journal: the cost is already in the ledger")
    public ResponseEntity<ApiResponse<Asset>> create(@RequestBody Save req) {
        return ResponseEntity.ok(ApiResponse.success("Added", service.create(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    public ResponseEntity<ApiResponse<Asset>> update(@PathVariable UUID id, @RequestBody Save req) {
        return ResponseEntity.ok(ApiResponse.success("Saved", service.update(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @GetMapping("/depreciation/preview")
    @PreAuthorize("hasAuthority('ACCOUNTING_READ')")
    @Operation(summary = "What a depreciation run would charge for a month (default: the next month to run)")
    public ResponseEntity<ApiResponse<Preview>> preview(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate period) {
        return ResponseEntity.ok(ApiResponse.success("Success", service.preview(TenantContext.getTenantIdAsObject(), period)));
    }

    @PostMapping("/depreciation/run")
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    @Operation(summary = "Make the month's depreciation as a draft journal for a different person to post")
    public ResponseEntity<ApiResponse<Run>> run(@RequestBody RunRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Drafted", service.run(TenantContext.getTenantIdAsObject(), req.period(), TenantContext.getCurrentUserId())));
    }

    @GetMapping("/depreciation/runs")
    @PreAuthorize("hasAuthority('ACCOUNTING_READ')")
    public ResponseEntity<ApiResponse<List<Run>>> runs() {
        return ResponseEntity.ok(ApiResponse.success("Success", service.runs(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/{id}/dispose")
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    @Operation(summary = "Dispose of an asset: drafts the journal that removes cost and accumulated depreciation and records the gain or loss")
    public ResponseEntity<ApiResponse<Disposal>> dispose(@PathVariable UUID id, @RequestBody DisposeRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Drafted", service.dispose(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId())));
    }

    @GetMapping("/reconciliation")
    @PreAuthorize("hasAuthority('ACCOUNTING_READ')")
    @Operation(summary = "The register against the ledger for each cost and accumulated depreciation account")
    public ResponseEntity<ApiResponse<List<ReconRow>>> reconciliation() {
        return ResponseEntity.ok(ApiResponse.success("Success", service.reconciliation(TenantContext.getTenantIdAsObject())));
    }
}
