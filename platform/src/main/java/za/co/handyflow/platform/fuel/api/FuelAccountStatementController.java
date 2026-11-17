package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelAccountStatementService;
import za.co.handyflow.platform.fuel.dto.AccountStatementDtos.Statement;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.UUID;

/**
 * What Fuel charged to a customer account in a period. Both paths serve the same statement. The first is not under /truck-stop, so a company without
 * the Truck Stop division can read its delivery statement; the old path stays for existing callers and still requires the Truck Stop division.
 */
@RestController
@RequestMapping({"/api/v1/fuel/account-statements", "/api/v1/fuel/truck-stop/account-statements"})
@RequiredArgsConstructor
@Tag(name = "Fuel Account Statements", description = "What Fuel charged to a customer account: deliveries, stays and tabs, and where the invoices stand")
public class FuelAccountStatementController {

    private final FuelAccountStatementService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/{accountId}")
    @PreAuthorize("hasAnyAuthority('FUEL_READ','FUEL_MANAGE','FUEL_TRUCKSTOP_MANAGE')")
    public ResponseEntity<ApiResponse<Statement>> statement(@PathVariable UUID accountId,
                                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                            @RequestParam(required = false) String division) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(service.statement(TenantContext.getTenantIdAsObject(), accountId, from, to, division)));
    }
}
