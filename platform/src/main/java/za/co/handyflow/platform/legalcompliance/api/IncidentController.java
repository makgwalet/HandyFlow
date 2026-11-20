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
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.*;
import za.co.handyflow.platform.legalcompliance.dto.GovernanceDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Compliance incidents and breaches. Reporting is open to any signed-in user; handling needs LEGALCOMPLIANCE_INCIDENT_MANAGE. */
@RestController("legalComplianceIncidentController")
@RequestMapping("/api/v1/legalcompliance/incidents")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Incidents", description = "Report, investigate, assess notification duties for, and close incidents")
public class IncidentController {

    private static final String CAN_READ = "hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_INCIDENT_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_MANAGE = "hasAnyAuthority('LEGALCOMPLIANCE_INCIDENT_MANAGE','LEGALCOMPLIANCE_ADMIN')";

    private final IncidentService incidents;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<IncidentResponse>>> list(@RequestParam(required = false) IncidentStatus status, @RequestParam(defaultValue = "false") boolean includeClosed) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(incidents.list(tenant(), status, includeClosed)));
    }

    @GetMapping("/summary")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<IncidentSummary>> summary() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(incidents.summary(tenant())));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<IncidentResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(incidents.get(tenant(), id)));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Report an incident. Open to any signed-in user.")
    public ResponseEntity<ApiResponse<IncidentResponse>> report(@Valid @RequestBody IncidentRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Incident reported", incidents.report(tenant(), req, uid(), uname())));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<IncidentResponse>> update(@PathVariable UUID id, @Valid @RequestBody IncidentRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Incident updated", incidents.update(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/investigate")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<IncidentResponse>> investigate(@PathVariable UUID id, @Valid @RequestBody InvestigateRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Investigation started", incidents.investigate(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<IncidentResponse>> resolve(@PathVariable UUID id, @Valid @RequestBody ResolveIncidentRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Incident resolved", incidents.resolve(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/assess-notification")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Record whether the Regulator and data subjects must be notified, and why. The system records the decision; it does not make it.")
    public ResponseEntity<ApiResponse<IncidentResponse>> assess(@PathVariable UUID id, @Valid @RequestBody AssessRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Assessment recorded", incidents.assess(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/regulator-notified")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<IncidentResponse>> regulatorNotified(@PathVariable UUID id, @Valid @RequestBody RegulatorNotifiedRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Regulator notification recorded", incidents.regulatorNotified(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/subjects-notified")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<IncidentResponse>> subjectsNotified(@PathVariable UUID id, @Valid @RequestBody SubjectsNotifiedRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Data subject notification recorded", incidents.subjectsNotified(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/defer-subjects")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<IncidentResponse>> deferSubjects(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Reason recorded", incidents.deferSubjects(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Close a resolved incident. Not by its investigator; needs notification duties recorded and all corrective actions verified.")
    public ResponseEntity<ApiResponse<IncidentResponse>> close(@PathVariable UUID id, @Valid @RequestBody FindingNoteRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Incident closed", incidents.close(tenant(), id, req, uid(), uname())));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
