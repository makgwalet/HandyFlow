// security/api/CheckpointAdminController.java
package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.CheckpointAdminService;
import za.co.handyflow.platform.security.dto.CheckpointAdminDtos.Row;
import za.co.handyflow.platform.security.dto.CheckpointAdminDtos.UpdateRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/security/checkpoints")
@RequiredArgsConstructor
@Tag(name = "Security - Checkpoints")
public class CheckpointAdminController {

    private final CheckpointAdminService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Every checkpoint with 30-day scan count, last scan and active-route use; optional site, inactive on request")
    public ResponseEntity<ApiResponse<List<Row>>> list(@RequestParam(required = false) UUID siteId,
                                                       @RequestParam(defaultValue = "false") boolean includeInactive) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), siteId, includeInactive)));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Edit a checkpoint's name, description, NFC tag, Bluetooth beacon or active flag (a deactivated checkpoint cannot be scanned)")
    public ResponseEntity<ApiResponse<Row>> update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest req) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(service.update(TenantContext.getTenantIdAsObject(), id, req)));
    }
}
