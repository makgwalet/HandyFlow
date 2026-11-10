package za.co.handyflow.platform.warehousing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.warehousing.application.internal.WhseCycleCountService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehousing/cycle-counts")
@RequiredArgsConstructor
@Tag(name = "Warehousing - Cycle counts", description = "Count stock, approve differences, measure accuracy")
public class WhseCycleCountController {

    private static final String READ = "hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')";
    private static final String WRITE = "hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')";
    private static final String APPROVE = "hasAuthority('WAREHOUSING_COUNT_APPROVE')";

    private final WhseCycleCountService service;
    private final FeatureGuard featureGuard;

    public record CreateRequest(UUID clientId, UUID locationId, Boolean blind, @jakarta.validation.constraints.Size(max = 300) String note) {}
    public record CountRequest(@NotNull @DecimalMin("0") BigDecimal qty) {}
    public record DecisionRequest(@jakarta.validation.constraints.Size(max = 300) String note) {}

    @GetMapping
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<WhseCycleCountService.SessionRow>>> list(@RequestParam(required = false) String status) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), status)));
    }

    @GetMapping("/accuracy")
    @PreAuthorize(READ)
    @Operation(summary = "Stock accuracy: share of lines counted in the period whose first count matched the system")
    public ResponseEntity<ApiResponse<WhseCycleCountService.Accuracy>> accuracy(@RequestParam(defaultValue = "30") int days) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.accuracy(TenantContext.getTenantIdAsObject(), days)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    @Operation(summary = "Count detail. While a blind count is being counted, system quantities and differences are withheld.")
    public ResponseEntity<ApiResponse<WhseCycleCountService.Detail>> detail(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.detail(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize(WRITE)
    @Operation(summary = "Start a count: freezes the system quantity of every position with stock in the chosen client/location")
    public ResponseEntity<ApiResponse<Map<String, UUID>>> create(@Valid @RequestBody CreateRequest req) {
        featureGuard.requireModule("warehousing");
        UUID id = service.create(TenantContext.getTenantIdAsObject(), req.clientId(), req.locationId(),
                req.blind() == null || req.blind(), req.note(), TenantContext.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Count started", Map.of("id", id)));
    }

    @PutMapping("/{id}/lines/{lineId}")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<Void>> count(@PathVariable UUID id, @PathVariable UUID lineId, @Valid @RequestBody CountRequest req) {
        featureGuard.requireModule("warehousing");
        service.recordCount(TenantContext.getTenantIdAsObject(), id, lineId, req.qty(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Count recorded", null));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<WhseCycleCountService.SubmitResult>> submit(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.submit(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/lines/{lineId}/approve")
    @PreAuthorize(APPROVE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> approve(@PathVariable UUID id, @PathVariable UUID lineId,
                                                                    @RequestBody(required = false) DecisionRequest req) {
        featureGuard.requireModule("warehousing");
        String sentBack = service.approve(TenantContext.getTenantIdAsObject(), id, lineId, req == null ? null : req.note(), TenantContext.getCurrentUserId());
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("applied", sentBack == null);
        body.put("message", sentBack);
        return ResponseEntity.ok(ApiResponse.success(sentBack == null ? "Difference approved and posted" : "Sent back for recount", body));
    }

    @PostMapping("/{id}/lines/{lineId}/reject")
    @PreAuthorize(APPROVE)
    public ResponseEntity<ApiResponse<Void>> reject(@PathVariable UUID id, @PathVariable UUID lineId, @Valid @RequestBody DecisionRequest req) {
        featureGuard.requireModule("warehousing");
        service.reject(TenantContext.getTenantIdAsObject(), id, lineId, req.note(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Sent back for recount", null));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<Void>> close(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        service.close(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Count closed", null));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        service.cancel(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Count cancelled", null));
    }
}
