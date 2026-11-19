package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.*;
import za.co.handyflow.platform.legalcompliance.domain.model.RiskCategory;
import za.co.handyflow.platform.legalcompliance.domain.model.RiskStatus;
import za.co.handyflow.platform.legalcompliance.dto.GrcDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Risk register: scoring, treatment, acceptance, heat map. */
@RestController
@RequestMapping("/api/v1/legalcompliance/risks")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Risk Register", description = "Inherent and residual risk, appetite, treatment and acceptance")
public class RiskController {

    private static final String CAN_READ = "hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_RISK_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_MANAGE = "hasAnyAuthority('LEGALCOMPLIANCE_RISK_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_ACCEPT = "hasAnyAuthority('LEGALCOMPLIANCE_RISK_ACCEPT','LEGALCOMPLIANCE_ADMIN')";

    private final RiskService risks;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<RiskResponse>>> list(@RequestParam(required = false) RiskStatus status, @RequestParam(required = false) RiskCategory category,
                                                                @RequestParam(defaultValue = "false") boolean includeClosed) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(risks.list(tenant(), status, category, includeClosed)));
    }

    @GetMapping("/summary")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<RiskSummary>> summary() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(risks.summary(tenant())));
    }

    @GetMapping("/heatmap")
    @PreAuthorize(CAN_READ)
    @Operation(summary = "5x5 grid of open, assessed risks by RESIDUAL (default) or INHERENT likelihood and impact")
    public ResponseEntity<ApiResponse<HeatMapResponse>> heatMap(@RequestParam(defaultValue = "RESIDUAL") String basis) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(risks.heatMap(tenant(), "INHERENT".equalsIgnoreCase(basis))));
    }

    @GetMapping("/settings")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<SettingsResponse>> settings() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(risks.getSettings(tenant())));
    }

    @PutMapping("/settings")
    @PreAuthorize(CAN_ACCEPT)
    @Operation(summary = "Set risk appetite and tolerance (scores out of 25). Past assessments keep the thresholds they were made under.")
    public ResponseEntity<ApiResponse<SettingsResponse>> saveSettings(@Valid @RequestBody SettingsRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Settings saved", risks.saveSettings(tenant(), req, uid(), uname())));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<RiskDetailResponse>> detail(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(risks.detail(tenant(), id)));
    }

    @PostMapping
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<RiskResponse>> create(@Valid @RequestBody RiskRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Risk created", risks.create(tenant(), req, uid(), uname())));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<RiskResponse>> update(@PathVariable UUID id, @Valid @RequestBody RiskRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Risk updated", risks.update(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/assess")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Record a new assessment. The previous one is superseded and stays in the history.")
    public ResponseEntity<ApiResponse<RiskResponse>> assess(@PathVariable UUID id, @Valid @RequestBody AssessRiskRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Assessment recorded", risks.assess(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/treatment")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<RiskResponse>> treatment(@PathVariable UUID id, @Valid @RequestBody TreatmentRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Treatment plan saved", risks.startTreatment(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize(CAN_ACCEPT)
    @Operation(summary = "Formally accept a risk until a future date. Refused when residual is above tolerance.")
    public ResponseEntity<ApiResponse<RiskResponse>> accept(@PathVariable UUID id, @Valid @RequestBody AcceptRiskRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Risk accepted", risks.accept(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<RiskResponse>> close(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Risk closed", risks.close(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<RiskResponse>> reopen(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Risk reopened", risks.reopen(tenant(), id, req, uid(), uname())));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
