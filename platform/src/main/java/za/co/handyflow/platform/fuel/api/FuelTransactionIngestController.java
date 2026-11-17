package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.fuel.application.internal.FuelPosTransactionService;
import za.co.handyflow.platform.fuel.dto.PosImportDtos.IncomingTransactions;
import za.co.handyflow.platform.fuel.dto.PosImportDtos.TransactionIngestResult;
import za.co.handyflow.platform.shared.ApiResponse;

/**
 * Live pump transactions (sales, refunds, voids) from a pump or POS system. Same rules as the shift import: no login and no TenantContext, the company comes
 * from the key in the {@code X-Fuel-Import-Key} header (unknown, missing or revoked gets 401), the exact path is permitted in SecurityConfig and rate
 * limited in RateLimitFilter. It only stores the transactions against their shift for review.
 */
@RestController
@RequestMapping("/api/v1/fuel/import")
@RequiredArgsConstructor
@Tag(name = "Fuel Import", description = "Inbound live pump transactions, authenticated by API key, held with their shift")
public class FuelTransactionIngestController {

    private final FuelPosTransactionService transactions;

    @PostMapping("/pos-transactions")
    @Operation(summary = "Send sales, refunds and voids for one shift. Header X-Fuel-Import-Key is the company's key. Safe to repeat per externalId. "
            + "Refunds and voids take effect only when a supervisor accepts the shift")
    public ResponseEntity<ApiResponse<TransactionIngestResult>> receive(
            @RequestHeader(value = "X-Fuel-Import-Key", required = false) String apiKey,
            @Valid @RequestBody IncomingTransactions body) {
        return ResponseEntity.ok(ApiResponse.success(transactions.ingest(apiKey, body)));
    }
}
