package za.co.handyflow.platform.legalpractice.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.legalpractice.application.internal.LpPortalDataService;
import za.co.handyflow.platform.legalpractice.dto.LpClientResponse;
import za.co.handyflow.platform.legalpractice.dto.LpInvoiceResponse;
import za.co.handyflow.platform.legalpractice.dto.LpMatterResponse;
import za.co.handyflow.platform.shared.ApiResponse;

import java.util.UUID;

/**
 * Client-facing reads — a portal user's own matter status and invoices.
 * Every endpoint is scoped by an explicit {@code clientId} path segment
 * and authorized against a live {@code LpPortalAccessGrant} inside
 * {@code LpPortalDataService}, never against a bare portal JWT alone (the
 * token only proves identity, not what that identity may see — see
 * {@code PortalJwtService}'s own Javadoc). No {@code FeatureGuard} call
 * here for the same reason as {@code LpPortalAuthController}: no
 * {@code TenantContext} exists on a portal request.
 */
@RestController
@RequestMapping("/api/v1/legal-practice/portal/data/clients/{clientId}")
@RequiredArgsConstructor
@Tag(name = "Legal Practice Client Portal - Data", description = "Client-facing self-service reads")
public class LpPortalDataController {

    private final LpPortalDataService portalDataService;

    @GetMapping
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "This client's own record")
    public ResponseEntity<ApiResponse<LpClientResponse>> getMyClient(@PathVariable UUID clientId) {
        return ResponseEntity.ok(ApiResponse.success(
                portalDataService.getMyClient(currentPortalUserId(), clientId)));
    }

    @GetMapping("/matters")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "This client's own matters")
    public ResponseEntity<ApiResponse<Page<LpMatterResponse>>> getMyMatters(
            @PathVariable UUID clientId, @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                portalDataService.listMyMatters(currentPortalUserId(), clientId, pageable)));
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "This client's own invoices")
    public ResponseEntity<ApiResponse<Page<LpInvoiceResponse>>> getMyInvoices(
            @PathVariable UUID clientId, @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                portalDataService.listMyInvoices(currentPortalUserId(), clientId, pageable)));
    }

    /** {@code PortalJwtFilter} stores the portal user's ID (UUID string) as the Authentication principal. */
    private UUID currentPortalUserId() {
        return UUID.fromString((String) SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }
}
