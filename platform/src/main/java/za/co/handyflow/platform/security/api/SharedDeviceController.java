package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.security.application.internal.SharedDeviceService;
import za.co.handyflow.platform.security.dto.RegisterSharedDeviceRequest;
import za.co.handyflow.platform.security.dto.SharedDeviceResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@Tag(name = "Security - Shared devices")
@RestController
@RequestMapping("/api/v1/security/devices/shared")
@RequiredArgsConstructor
public class SharedDeviceController {

    private final SharedDeviceService service;

    @PostMapping
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Register a shared site device",
            description = "A phone/tablet kept at a post and used by whichever guard is on duty. Guards sign in on it with employee code and PIN; each sign-in ends the previous guard's session on that device.")
    public ResponseEntity<ApiResponse<SharedDeviceResponse>> register(@Valid @RequestBody RegisterSharedDeviceRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Shared device registered",
                service.register(TenantContext.getTenantIdAsObject(), req)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SECURITY_READ') or hasAuthority('SECURITY_MANAGE')")
    public ResponseEntity<ApiResponse<List<SharedDeviceResponse>>> list(@RequestParam(required = false) UUID siteId) {
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), siteId)));
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Take a shared device out of service (lost/stolen) and end sessions open on it")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> revoke(@PathVariable UUID id) {
        int ended = service.revoke(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Shared device revoked", Map.of("sessionsEnded", ended)));
    }
}
