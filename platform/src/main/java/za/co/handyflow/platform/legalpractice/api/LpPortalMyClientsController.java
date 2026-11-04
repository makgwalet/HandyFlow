package za.co.handyflow.platform.legalpractice.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.legalpractice.application.internal.LpPortalDataService;
import za.co.handyflow.platform.legalpractice.dto.LpClientResponse;
import za.co.handyflow.platform.shared.ApiResponse;

import java.util.List;
import java.util.UUID;

/**
 * Lists the client records the signed-in portal user has active access to. The portal's other reads all need a
 * {@code clientId}, and the login response does not carry one, so without this the portal could not find its way in.
 * Same security model as {@link LpPortalDataController}: portal JWT only, access decided by live grants.
 */
@RestController
@RequestMapping("/api/v1/legal-practice/portal/data/clients")
@RequiredArgsConstructor
@Tag(name = "Legal Practice Client Portal - Data", description = "Client-facing self-service reads")
public class LpPortalMyClientsController {

    private final LpPortalDataService portalDataService;

    @GetMapping
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "The client records this portal user can see")
    public ResponseEntity<ApiResponse<List<LpClientResponse>>> myClients() {
        UUID portalUserId = UUID.fromString((String) SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        return ResponseEntity.ok(ApiResponse.success(portalDataService.listMyClients(portalUserId)));
    }
}
