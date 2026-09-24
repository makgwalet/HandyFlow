package za.co.handyflow.platform.admin.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.admin.application.internal.SupportActionService;
import za.co.handyflow.platform.admin.dto.ExecuteSupportActionRequest;
import za.co.handyflow.platform.admin.dto.SupportActionDescriptor;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.SupportActionResult;

import java.util.List;
import java.util.UUID;

/**
 * Separate from AdminController — a distinct enough capability (see
 * SupportActionService's own Javadoc for the full framework) to warrant
 * its own controller rather than growing AdminController further, at the
 * small, low-risk cost of its own copies of the three stateless
 * admin-identity-extraction helpers AdminController already has.
 */
@RestController
@RequestMapping("/api/v1/admin/support-actions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
@Tag(name = "Admin Portal - Support Actions", description = "The closed, audited list of actions support staff can trigger on a tenant's behalf")
public class SupportActionController {

    private final SupportActionService supportActionService;

    @GetMapping
    @Operation(summary = "Every registered support action — whatever modules have actually implemented, nothing more")
    public ResponseEntity<ApiResponse<List<SupportActionDescriptor>>> listActions() {
        return ResponseEntity.ok(ApiResponse.success(supportActionService.listActions()));
    }

    @PostMapping("/{actionKey}")
    @Operation(summary = "Execute a support action — always audited, both on success and on failure")
    public ResponseEntity<ApiResponse<SupportActionResult>> execute(
            @PathVariable String actionKey, @Valid @RequestBody ExecuteSupportActionRequest request,
            HttpServletRequest http) {
        SupportActionResult result = supportActionService.execute(actionKey, request.tenantId(), request.targetId(),
                request.params(), getAdminId(), getAdminEmail(), getIp(http));
        return ResponseEntity.ok(ApiResponse.success(
                result.success() ? "Action completed" : "Action did not succeed — see message", result));
    }

    private UUID getAdminId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() != null)
            return UUID.fromString(auth.getPrincipal().toString());
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }

    @SuppressWarnings("unchecked")
    private String getAdminEmail() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof java.util.Map) {
            var d = (java.util.Map<String, String>) auth.getDetails();
            String e = d.get("email");
            if (e != null && !e.isBlank()) return e;
        }
        return "unknown-admin";
    }

    private String getIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        return forwarded != null ? forwarded.split(",")[0].trim() : req.getRemoteAddr();
    }
}
