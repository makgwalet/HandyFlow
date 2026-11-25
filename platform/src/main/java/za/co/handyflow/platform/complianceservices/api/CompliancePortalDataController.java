package za.co.handyflow.platform.complianceservices.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.complianceservices.application.internal.CompliancePortalDataService;
import za.co.handyflow.platform.complianceservices.dto.PortalDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Client-facing and read only, gated by the portal token ({@code PORTAL_USER}). A portal token is not tenant-scoped, so
 * nothing here reads TenantContext: the service works out the tenant and client from the caller's active grants. A person
 * with access to several clients says which with {@code ?clientId=}.
 */
@RestController
@RequestMapping("/api/v1/compliance-services/portal/me")
@RequiredArgsConstructor
@Tag(name = "Compliance Services Client Portal - Data", description = "What a signed-in client contact can see for their own organisation")
public class CompliancePortalDataController {

    private static final String PORTAL = "hasAuthority('PORTAL_USER')";

    private final CompliancePortalDataService data;

    @GetMapping("/clients")
    @PreAuthorize(PORTAL)
    public ResponseEntity<ApiResponse<List<ClientRef>>> clients() {
        return ResponseEntity.ok(ApiResponse.success(data.myClients(userId())));
    }

    @GetMapping("/overview")
    @PreAuthorize(PORTAL)
    public ResponseEntity<ApiResponse<Overview>> overview(@RequestParam(required = false) UUID clientId) {
        return ResponseEntity.ok(ApiResponse.success(data.overview(userId(), clientId, LocalDate.now())));
    }

    @GetMapping("/quotes")
    @PreAuthorize(PORTAL)
    public ResponseEntity<ApiResponse<List<Quote>>> quotes(@RequestParam(required = false) UUID clientId) {
        return ResponseEntity.ok(ApiResponse.success(data.quotes(userId(), clientId, LocalDate.now())));
    }

    @GetMapping("/agreements")
    @PreAuthorize(PORTAL)
    public ResponseEntity<ApiResponse<List<Agreement>>> agreements(@RequestParam(required = false) UUID clientId) {
        return ResponseEntity.ok(ApiResponse.success(data.agreements(userId(), clientId, LocalDate.now())));
    }

    @GetMapping("/invoices")
    @PreAuthorize(PORTAL)
    public ResponseEntity<ApiResponse<List<Invoice>>> invoices(@RequestParam(required = false) UUID clientId) {
        return ResponseEntity.ok(ApiResponse.success(data.invoices(userId(), clientId, LocalDate.now())));
    }

    private UUID userId() {
        try {
            return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
        } catch (Exception e) {
            throw new HandyFlowException("Invalid portal session", HttpStatus.UNAUTHORIZED, "INVALID_PORTAL_SESSION");
        }
    }
}
