// security/api/GuardDuressController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.security.application.internal.ControlRoomService;
import za.co.handyflow.platform.security.domain.model.AlarmEvent;
import za.co.handyflow.platform.security.dto.TriggerDuressRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

/**
 * SAFETY-CRITICAL FIX: {@code ControlRoomController.triggerDuress()}'s own
 * doc comment says it "deliberately has no authority gate beyond standard
 * authentication: any guard in distress must be able to trigger this
 * without needing elevated permissions" — but that endpoint lives under
 * {@code /api/v1/security/**}, which {@link
 * za.co.handyflow.platform.security.infrastructure.GuardJwtFilter} does not
 * cover (confirmed directly: {@code GUARD_PATH_PREFIX =
 * "/api/v1/guard/"}). A guard's own JWT — issued by GuardAuthService in a
 * completely different shape from a tenant-user token — could not even
 * authenticate to reach an endpoint specifically designed to need no
 * permissions at all. A guard's own panic button was unusable by guards.
 * <p>
 * Same correction {@link GuardGateAccessController} already applied to
 * gate access: a guard-facing endpoint under {@code /api/v1/guard/**},
 * secured by {@code GuardJwtFilter}. {@code @PreAuthorize("hasAuthority
 * ('SECURITY_GUARD')")} here does not narrow anything beyond "must be an
 * authenticated guard" — GuardAuthService issues SECURITY_GUARD to every
 * guard token unconditionally, so this preserves the original endpoint's
 * "no elevated permission required" intent exactly, it just requires
 * being a guard at all, which is the whole point.
 * <p>
 * Deliberately does NOT remove or modify {@code ControlRoomController
 * .triggerDuress()} — that tenant-JWT-surfaced endpoint has no caller in
 * the web frontend today (confirmed directly), but may still be a
 * legitimate path for other trusted callers (a future hardware panic-
 * button integration, or a supervisor manually triggering duress on a
 * guard's behalf after a radio call) — this adds the missing guard
 * capability without removing a working one on the strength of "nothing
 * calls it today."
 */
@RestController
@RequestMapping("/api/v1/guard")
@RequiredArgsConstructor
@Tag(name = "Guard - Duress", description = "Guard-facing panic/duress trigger (mobile)")
public class GuardDuressController {

    private final ControlRoomService controlRoomService;

    @PostMapping("/duress")
    @PreAuthorize("hasAuthority('SECURITY_GUARD')")
    @Operation(
            summary = "Trigger a duress event (panic) — guard-facing",
            description = """
            The highest-priority alarm source — severity is hard-set to
            CRITICAL regardless of any input, no triage step required
            before dispatch. Requires only SECURITY_GUARD, which every
            guard token always carries — this is intentionally as close
            to "no permission check" as an authenticated endpoint can be,
            matching the original endpoint's own design intent exactly.
            Optionally links to a protection detail if the duress
            occurred during a CP engagement.
            """)
    public ResponseEntity<ApiResponse<AlarmEvent>> triggerDuress(
            @Valid @RequestBody TriggerDuressRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        controlRoomService.triggerDuress(TenantContext.getTenantIdAsObject(), req)));
    }
}
