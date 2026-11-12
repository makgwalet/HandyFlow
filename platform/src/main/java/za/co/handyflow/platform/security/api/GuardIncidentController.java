// security/api/GuardIncidentController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.security.application.internal.IncidentCaseService;
import za.co.handyflow.platform.security.application.internal.GuardOfflineWriteService;
import za.co.handyflow.platform.security.dto.CreateIncidentRequest;
import za.co.handyflow.platform.security.dto.IncidentResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/**
 * GAP-02 fix: independent of GAP-01's routing problem, {@code
 * IncidentController.createIncident()} requires {@code SECURITY_MANAGE} —
 * confirmed directly that {@code GuardAuthService.buildLoginResponse()}
 * issues guard tokens with exactly {@code ["SECURITY_GUARD",
 * "SECURITY_SCAN"]}, never {@code SECURITY_MANAGE}. Even after moving to
 * the correct JWT surface, a guard's own token still could not pass that
 * authority check — a guard genuinely could not report an incident
 * through their own session, for a second, independent reason on top of
 * the routing one.
 * <p>
 * This endpoint requires only {@code SECURITY_GUARD} — reporting an
 * incident is exactly the kind of self-service action a guard's own
 * authority tier should cover; supervisor-only actions
 * (acknowledge/resolve/list-all) deliberately stay on the original
 * {@link IncidentController}, still gated by {@code SECURITY_MANAGE}/
 * {@code SECURITY_READ} — this does not touch that controller at all.
 * <p>
 * Requires {@code CreateIncidentRequest.siteId} same as the original —
 * see the new {@code siteId} field added to {@code DeviceSessionResponse}
 * in this same change (GAP-03) for how the guard app now has a real value
 * to supply here.
 */
@RestController
@RequestMapping("/api/v1/guard/incidents")
@RequiredArgsConstructor
@Tag(name = "Guard - Incidents", description = "Guard-facing incident reporting (mobile)")
public class GuardIncidentController {

    private final GuardOfflineWriteService offlineWrites;
    private final IncidentCaseService caseService;

    @PostMapping
    @PreAuthorize("hasAuthority('SECURITY_GUARD')")
    @Operation(
            summary = "Report an incident — guard-facing",
            description = "Same validation as the supervisor-facing endpoint this mirrors " +
                    "(siteId required, guardId/shiftId optional). Requires only SECURITY_GUARD, " +
                    "which every guard token carries — acknowledge/resolve/list-all remain " +
                    "supervisor-only on the original IncidentController.")
    public ResponseEntity<ApiResponse<IncidentResponse>> createIncident(
            @Valid @RequestBody CreateIncidentRequest req) {
        // The reporting guard is the token's guard. A body naming another guard, or none, is overridden.
        UUID me = TenantContext.getCurrentUserId();
        CreateIncidentRequest own = new CreateIncidentRequest(req.siteId(), req.shiftId(), me, req.title(),
                req.description(), req.severity(), req.type(), req.latitude(), req.longitude(),
                req.clientRef(), req.recordedAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                offlineWrites.incident(TenantContext.getTenantIdAsObject(), me, TenantContext.getCurrentUserName(), own)));
    }

    @PostMapping(value = "/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('SECURITY_GUARD')")
    @Operation(summary = "Attach a photo or file to an incident you reported — guard-facing",
            description = "Only the guard who reported the incident can attach. Another guard's incident returns 404. " +
                    "Up to 20 files per incident from the field; type and size limits are the evidence store's.")
    public ResponseEntity<ApiResponse<EvidenceResponse>> attach(@PathVariable UUID id,
                                                                @RequestParam("file") MultipartFile file,
                                                                @RequestParam(value = "label", required = false) String label) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Evidence attached",
                caseService.attachByGuard(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId(),
                        file, label, TenantContext.getCurrentUserName())));
    }
}
