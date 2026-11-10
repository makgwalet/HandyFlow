package za.co.handyflow.platform.warehousing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.warehousing.application.internal.WhsePutawayRules;
import za.co.handyflow.platform.warehousing.application.internal.WhseTaskService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehousing/tasks")
@RequiredArgsConstructor
@Tag(name = "Warehousing - Tasks", description = "Pick and put-away tasks")
public class WhseTaskController {

    private static final String READ = "hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')";

    private final WhseTaskService service;
    private final FeatureGuard featureGuard;

    public record CompleteRequest(@NotNull BigDecimal qty, @Size(max = 300) String reason, UUID toLocationId) {}
    public record ReasonRequest(@NotNull @Size(min = 5, max = 300) String reason) {}

    @GetMapping
    @PreAuthorize(READ)
    @Operation(summary = "The worklist, in work order (priority, then the walking order). type PICK or PUTAWAY; status is a comma list; mine=true shows tasks you hold; orderId narrows to an order")
    public ResponseEntity<ApiResponse<List<WhseTaskService.TaskRow>>> list(@RequestParam(required = false) String type, @RequestParam(required = false) String status,
                                                                            @RequestParam(defaultValue = "false") boolean mine, @RequestParam(required = false) UUID orderId,
                                                                            @RequestParam(defaultValue = "200") int limit) {
        featureGuard.requireModule("warehousing");
        List<String> statuses = status == null || status.isBlank() ? List.of("OPEN", "IN_PROGRESS", "SHORT") : List.of(status.split(","));
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), type, statuses,
                mine ? TenantContext.getCurrentUserId() : null, orderId, limit)));
    }

    @GetMapping("/summary")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<WhseTaskService.Summary>> summary() {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.summary(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/putaway/sync")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Creates put-away tasks for available stock sitting in dock and staging locations that has no open task")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> sync() {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(Map.of("created", service.syncPutaway(TenantContext.getTenantIdAsObject()))));
    }

    @GetMapping("/{id}/suggestions")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<WhsePutawayRules.Suggestion>>> suggestions(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.suggestions(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/claim")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<WhseTaskService.TaskRow>> claim(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success("Task claimed", service.claim(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/release")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<WhseTaskService.TaskRow>> release(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        boolean manager = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().anyMatch(a -> "WAREHOUSING_ADMIN".equals(a.getAuthority()));
        return ResponseEntity.ok(ApiResponse.success("Task handed back", service.release(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId(), manager)));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Completes a pick (quantity found; less than asked needs a reason) or a put-away (quantity and destination; the suggestion by default)")
    public ResponseEntity<ApiResponse<WhseTaskService.TaskRow>> complete(@PathVariable UUID id, @Valid @RequestBody CompleteRequest req) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success("Task completed", service.complete(TenantContext.getTenantIdAsObject(), id, req.qty(), req.reason(),
                req.toLocationId(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<WhseTaskService.TaskRow>> cancel(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success("Task cancelled", service.cancel(TenantContext.getTenantIdAsObject(), id, req.reason(), TenantContext.getCurrentUserId())));
    }
}
