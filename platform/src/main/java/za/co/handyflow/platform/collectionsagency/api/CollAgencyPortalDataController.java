package za.co.handyflow.platform.collectionsagency.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.collectionsagency.application.internal.CollAgencyPortalDataService;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyDebtorAccount;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyTrustTransaction;
import za.co.handyflow.platform.collectionsagency.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;

import java.util.List;
import java.util.UUID;

/**
 * Client-portal-facing reads — a creditor client checking their own
 * placed portfolio and trust/remittance statement. Direct mirror of
 * PayrollBureauPortalDataController/RecruitmentAgencyPortalDataController.
 */
@RestController
@RequestMapping("/api/v1/collections-agency/portal")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PORTAL_USER')")
@Tag(name = "Collections Agency Client Portal", description = "Client-facing data access")
public class CollAgencyPortalDataController {

    private final CollAgencyPortalDataService portalDataService;

    @GetMapping("/clients")
    @Operation(summary = "List every client this portal user has access to")
    public ResponseEntity<ApiResponse<List<PortalClientSummaryResponse>>> getMyClients() {
        return ResponseEntity.ok(ApiResponse.success(portalDataService.getMyClients(getPortalUserId())));
    }

    @GetMapping("/clients/{clientId}/debtor-accounts")
    @Operation(summary = "List placed debtor accounts and recovery status for a client this portal user has access to")
    public ResponseEntity<ApiResponse<List<DebtorAccountResponse>>> getMyDebtorAccounts(@PathVariable UUID clientId) {
        return ResponseEntity.ok(ApiResponse.success(
                portalDataService.getMyDebtorAccounts(getPortalUserId(), clientId).stream()
                        .map(CollAgencyDebtorAccountController::toResponseStatic).toList()));
    }

    @GetMapping("/clients/{clientId}/trust-statement")
    @Operation(summary = "Trust/remittance transaction history for a client this portal user has access to")
    public ResponseEntity<ApiResponse<List<TrustTransactionResponse>>> getMyTrustStatement(@PathVariable UUID clientId) {
        return ResponseEntity.ok(ApiResponse.success(
                portalDataService.getMyTrustStatement(getPortalUserId(), clientId).stream()
                        .map(this::toTrustResponse).toList()));
    }

    private TrustTransactionResponse toTrustResponse(CollAgencyTrustTransaction t) {
        return new TrustTransactionResponse(t.getId(), t.getClientId(), t.getDebtorAccountId(),
                t.getTransactionType(), t.getAmount(), t.getTransactionDate(), t.getReference(), t.getNotes(),
                t.getRecordedByUserId(), t.getCreatedAt());
    }

    private UUID getPortalUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
    }
}
