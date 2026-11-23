package za.co.handyflow.platform.admin.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.RlsStatus;

/** Read-only: where row-level security stands. Switching strict on or off is done deliberately in the database, not from a screen. */
@RestController
@RequestMapping("/api/v1/admin/row-level-security")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
@Tag(name = "Admin Portal - Row-level security")
public class RlsStatusController {

    private final JdbcTemplate jdbc;

    @Value("${handyflow.rls.session-tenant:false}")
    private boolean sessionTenantEnabled;

    @GetMapping
    @Operation(summary = "Row-level security status: is each connection told its company, is strict on, which tables are covered, and which code took a connection with no company")
    public ResponseEntity<ApiResponse<RlsStatus>> status() {
        return ResponseEntity.ok(ApiResponse.success(RlsStatus.read(jdbc, sessionTenantEnabled)));
    }
}
