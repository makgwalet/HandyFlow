package za.co.handyflow.platform.controls.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.controls.application.ControlExceptionFacade;
import za.co.handyflow.platform.controls.dto.ControlExceptionResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * The shared "needs attention" board itself — one screen, every open
 * exception, from every module that's adopted ControlExceptionFacade.
 * No FeatureGuard.requireModule() check, same reasoning as
 * EvidenceController: this is foundational shared infrastructure any
 * tenant can see, not a subscribable module of its own.
 */
@RestController
@RequestMapping("/api/v1/control-exceptions")
@RequiredArgsConstructor
@Tag(name = "Control Exceptions", description = "Shared cross-module \"needs attention\" board")
public class ControlExceptionController {

    private final ControlExceptionFacade controlExceptionFacade;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List every open exception across every module")
    public ResponseEntity<ApiResponse<List<ControlExceptionResponse>>> listOpen() {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(controlExceptionFacade.listOpen(tenantId)));
    }

    public record ResolveExceptionRequest(String resolutionNotes) {}

    @PostMapping("/{id}/resolve")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mark an exception as resolved")
    public ResponseEntity<ApiResponse<ControlExceptionResponse>> resolve(@PathVariable UUID id,
                                                                         @RequestBody(required = false) ResolveExceptionRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        String notes = req != null ? req.resolutionNotes() : null;
        return ResponseEntity.ok(ApiResponse.success(controlExceptionFacade.resolve(tenantId, id,
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName(), notes)));
    }
}