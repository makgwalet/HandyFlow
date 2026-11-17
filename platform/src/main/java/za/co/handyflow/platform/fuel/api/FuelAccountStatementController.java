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

/** What the truck stop charged to a customer account in a period. Under /truck-stop, so the Truck Stop division is required on the server. */
@RestController
@RequestMapping("/api/v1/fuel/truck-stop/account-statements")
@RequiredArgsConstructor
@Tag(name = "Fuel Truck Stop Account Statements", description = "Stays and tabs charged to a customer account")
public class FuelAccountStatementController {

    private final FuelAccountStatementService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/{accountId}")
    @PreAuthorize("hasAnyAuthority('FUEL_READ','FUEL_MANAGE','FUEL_TRUCKSTOP_MANAGE')")
    public ResponseEntity<ApiResponse<Statement>> statement(@PathVariable UUID accountId,
                                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(service.statement(TenantContext.getTenantIdAsObject(), accountId, from, to)));
    }
}
