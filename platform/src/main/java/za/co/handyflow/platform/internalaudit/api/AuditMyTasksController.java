package za.co.handyflow.platform.internalaudit.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.internalaudit.application.internal.AuditMyTasksService;
import za.co.handyflow.platform.internalaudit.application.internal.MyTasks.Result;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/v1/internal-audit/my-tasks")
@RequiredArgsConstructor
@Tag(name = "Internal Audit - My Tasks", description = "The signed-in person's own open audit work")
public class AuditMyTasksController {
    private final AuditMyTasksService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    @Operation(summary = "Actions and findings owned by me, review notes waiting on me, and (for managers) completed actions waiting to be validated, most urgent first")
    public ResponseEntity<ApiResponse<Result>> mine(Authentication auth) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(service.forUser(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(),
                auth != null && auth.getAuthorities().stream().anyMatch(a -> "AUDIT_MANAGE".equals(a.getAuthority())), LocalDate.now(ZoneId.of("Africa/Johannesburg")))));
    }
}
