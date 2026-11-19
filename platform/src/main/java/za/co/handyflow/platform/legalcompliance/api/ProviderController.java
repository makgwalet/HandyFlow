package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.ProviderService;
import za.co.handyflow.platform.legalcompliance.dto.ProviderDtos.*;
import za.co.handyflow.platform.legalcompliance.dto.ReportDtos.ReportInfo;
import za.co.handyflow.platform.legalcompliance.dto.ReportDtos.ReportResult;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Provider (consultancy) workspace. Managers (LEGALCOMPLIANCE_PROVIDER_MANAGE / ADMIN) see and run the whole portfolio; consultants (LEGALCOMPLIANCE_PROVIDER_CONSULT)
 * see only the clients they are actively assigned to. Reading a client's records is read-only and is logged to the client's own audit trail.
 */
@RestController
@RequestMapping("/api/v1/legalcompliance/provider")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Provider", description = "Client organisations, consultant assignments, reviews and delegated read-only access")
public class ProviderController {

    private static final String CAN_MANAGE = "hasAnyAuthority('LEGALCOMPLIANCE_PROVIDER_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_USE = "hasAnyAuthority('LEGALCOMPLIANCE_PROVIDER_MANAGE','LEGALCOMPLIANCE_PROVIDER_CONSULT','LEGALCOMPLIANCE_ADMIN')";

    private final ProviderService provider;
    private final FeatureGuard featureGuard;

    @GetMapping("/summary")
    @PreAuthorize(CAN_USE)
    public ResponseEntity<ApiResponse<ProviderSummary>> summary() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(provider.summary(tenant(), uid(), manager())));
    }

    @GetMapping("/clients")
    @PreAuthorize(CAN_USE)
    public ResponseEntity<ApiResponse<List<ClientResponse>>> clients() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(provider.list(tenant(), uid(), manager())));
    }

    @GetMapping("/workload")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<List<WorkloadRow>>> workload() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(provider.workload(tenant())));
    }

    @PostMapping("/clients")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<InviteResult>> invite(@Valid @RequestBody InviteRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Invitation created. Give the client the code now; it is not shown again", provider.invite(tenant(), req, uid(), uname())));
    }

    @GetMapping("/clients/{id}")
    @PreAuthorize(CAN_USE)
    public ResponseEntity<ApiResponse<ClientDetail>> get(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(provider.get(tenant(), id, uid(), manager())));
    }

    @PutMapping("/clients/{id}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ClientDetail>> update(@PathVariable UUID id, @Valid @RequestBody ClientUpdateRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Client updated", provider.update(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/clients/{id}/reissue")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<InviteResult>> reissue(@PathVariable UUID id, @Valid @RequestBody ReissueRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("New invitation created. The previous code no longer works", provider.reissue(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/clients/{id}/suspend")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ClientDetail>> suspend(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Access suspended", provider.suspend(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/clients/{id}/resume")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ClientDetail>> resume(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Access resumed", provider.resume(tenant(), id, uid(), uname())));
    }

    @PostMapping("/clients/{id}/end")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ClientDetail>> end(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Relationship ended", provider.end(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/clients/{id}/assignments")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ClientDetail>> assign(@PathVariable UUID id, @Valid @RequestBody AssignRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Consultant assigned", provider.assign(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/clients/{id}/assignments/{assignmentId}/end")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<ClientDetail>> endAssignment(@PathVariable UUID id, @PathVariable UUID assignmentId) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Assignment ended", provider.endAssignment(tenant(), id, assignmentId, uid(), uname())));
    }

    @PostMapping("/clients/{id}/reviews")
    @PreAuthorize(CAN_USE)
    public ResponseEntity<ApiResponse<ClientDetail>> review(@PathVariable UUID id, @Valid @RequestBody ReviewRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Review recorded", provider.recordReview(tenant(), id, req, uid(), uname(), manager())));
    }

    @GetMapping("/clients/{id}/posture")
    @PreAuthorize(CAN_USE)
    public ResponseEntity<ApiResponse<ClientPosture>> posture(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(provider.posture(tenant(), id, uid(), uname(), manager())));
    }

    @GetMapping("/clients/{id}/reports")
    @PreAuthorize(CAN_USE)
    public ResponseEntity<ApiResponse<List<ReportInfo>>> reports(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(provider.reportCatalogue(tenant(), id, uid(), manager())));
    }

    @GetMapping("/clients/{id}/reports/{key}")
    @PreAuthorize(CAN_USE)
    public ResponseEntity<ApiResponse<ReportResult>> run(@PathVariable UUID id, @PathVariable String key, @RequestParam(required = false) String asOf) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(provider.runReport(tenant(), id, key, asOf, uid(), uname(), manager())));
    }

    private static boolean manager() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("LEGALCOMPLIANCE_PROVIDER_MANAGE") || a.getAuthority().equals("LEGALCOMPLIANCE_ADMIN"));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
