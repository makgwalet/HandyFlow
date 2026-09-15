// security/api/GuardIncidentController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.security.application.internal.IncidentService;
import za.co.handyflow.platform.security.dto.CreateIncidentRequest;
import za.co.handyflow.platform.security.dto.IncidentResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

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

    private final IncidentService incidentService;

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
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                incidentService.createIncident(TenantContext.getTenantIdAsObject(), req)));
    }
}
