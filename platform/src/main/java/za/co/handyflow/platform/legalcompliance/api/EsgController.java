package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.EsgService;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.ReasonRequest;
import za.co.handyflow.platform.legalcompliance.dto.EsgDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** ESG metrics and readings */
@RestController
@RequestMapping("/api/v1/legalcompliance/esg")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - ESG", description = "ESG metrics, readings and corrections")
public class EsgController {

    private static final String CAN_READ = "hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ESG_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_MANAGE = "hasAnyAuthority('LEGALCOMPLIANCE_ESG_MANAGE','LEGALCOMPLIANCE_ADMIN')";

    private final EsgService esg;
    private final FeatureGuard featureGuard;

    @GetMapping("/summary")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<EsgSummary>> summary() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(esg.summary(tenant())));
    }

    @GetMapping("/metrics")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<MetricResponse>>> metrics(@RequestParam(defaultValue = "false") boolean includeRetired) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(esg.list(tenant(), includeRetired)));
    }

    @GetMapping("/metrics/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<MetricDetail>> metric(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(esg.get(tenant(), id)));
    }

    @PostMapping("/metrics")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<MetricDetail>> create(@Valid @RequestBody MetricRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Metric created", esg.create(tenant(), req, uid(), uname())));
    }

    @PutMapping("/metrics/{id}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<MetricDetail>> update(@PathVariable UUID id, @Valid @RequestBody MetricRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Metric updated", esg.update(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/metrics/{id}/retire")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<MetricDetail>> retire(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Metric retired", esg.retire(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/metrics/{id}/readings")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<MetricDetail>> record(@PathVariable UUID id, @Valid @RequestBody ReadingRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Reading recorded", esg.record(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/readings/{readingId}/correct")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<MetricDetail>> correct(@PathVariable UUID readingId, @Valid @RequestBody CorrectionRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Reading corrected. The earlier value is kept", esg.correct(tenant(), readingId, req, uid(), uname())));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
