package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.MonitoringService;
import za.co.handyflow.platform.legalcompliance.domain.model.WatchItemStatus;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.ReasonRequest;
import za.co.handyflow.platform.legalcompliance.dto.MonitoringDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Regulatory monitoring: watched feeds, the triage inbox, and advisory impact suggestions */
@RestController
@RequestMapping("/api/v1/legalcompliance/monitoring")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Regulatory monitoring", description = "Watched feeds, triage inbox and advisory impact suggestions")
public class MonitoringController {

    private static final String CAN_READ = "hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_MONITOR_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_MANAGE = "hasAnyAuthority('LEGALCOMPLIANCE_MONITOR_MANAGE','LEGALCOMPLIANCE_ADMIN')";

    private final MonitoringService monitoring;
    private final FeatureGuard featureGuard;

    @GetMapping("/summary")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<MonitoringSummary>> summary() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(monitoring.summary(tenant())));
    }

    @GetMapping("/sources")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<SourceResponse>>> sources() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(monitoring.listSources(tenant())));
    }

    @PostMapping("/sources")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<SourceResponse>> addSource(@Valid @RequestBody SourceRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Source added", monitoring.addSource(tenant(), req, uid(), uname())));
    }

    @PutMapping("/sources/{id}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<SourceResponse>> updateSource(@PathVariable UUID id, @Valid @RequestBody SourceUpdateRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Source updated", monitoring.updateSource(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/sources/{id}/fetch")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<FetchOutcome>> fetch(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(monitoring.fetchNow(tenant(), id, uid(), uname())));
    }

    @GetMapping("/items")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<ItemResponse>>> items(@RequestParam(required = false) WatchItemStatus status, @RequestParam(required = false) UUID sourceId,
                                                                 @RequestParam(defaultValue = "100") int limit) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(monitoring.listItems(tenant(), status, sourceId, limit)));
    }

    @PostMapping("/items/{id}/decision")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ItemResponse>> decide(@PathVariable UUID id, @Valid @RequestBody DecisionRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Decision recorded", monitoring.decide(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/items/{id}/reopen")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ItemResponse>> reopen(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Item reopened", monitoring.reopen(tenant(), id, req, uid(), uname())));
    }

    /** Advisory: which obligations, controls and policies this item seems to touch. Computed on request, never stored. */
    @GetMapping("/items/{id}/suggestions")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<SuggestionResponse>>> suggestions(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(monitoring.suggestionsFor(tenant(), id)));
    }

    /** Same matching on pasted text, for a proposed amendment that did not come from a feed. */
    @PostMapping("/assist")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<SuggestionResponse>>> assist(@Valid @RequestBody AssistRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(monitoring.assist(tenant(), req.text())));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
