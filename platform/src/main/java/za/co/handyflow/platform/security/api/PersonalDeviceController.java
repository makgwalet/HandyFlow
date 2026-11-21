package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.security.application.internal.DeviceManagementService;
import za.co.handyflow.platform.security.application.internal.DeviceStateRules.Action;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@Tag(name = "Security - Guard devices")
@RestController
@RequestMapping("/api/v1/security/devices/personal")
@RequiredArgsConstructor
public class PersonalDeviceController {

    private final DeviceManagementService service;

    @PostMapping("/{id}/lost")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Report a guard's phone lost; ends sessions on it and stops it logging in")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> lost(@PathVariable UUID id) { return run(id, Action.LOST, "Device marked lost"); }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Revoke a guard's phone; ends sessions on it and stops it logging in")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> revoke(@PathVariable UUID id) { return run(id, Action.REVOKE, "Device revoked"); }

    @PostMapping("/{id}/reinstate")
    @PreAuthorize("hasAuthority('SECURITY_ADMIN')")
    @Operation(summary = "Reinstate a phone that was lost or revoked (only while it is still the guard's current device)")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> reinstate(@PathVariable UUID id) { return run(id, Action.REINSTATE, "Device reinstated"); }

    private ResponseEntity<ApiResponse<Map<String, Integer>>> run(UUID id, Action action, String msg) {
        int ended = service.apply(TenantContext.getTenantIdAsObject(), id, action, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(msg, Map.of("sessionsEnded", ended)));
    }
}
