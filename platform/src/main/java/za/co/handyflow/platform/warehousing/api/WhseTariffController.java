package za.co.handyflow.platform.warehousing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.warehousing.application.internal.WhseTariffService;
import za.co.handyflow.platform.warehousing.dto.AddBillingTermsRequest;
import za.co.handyflow.platform.warehousing.dto.AddStorageTariffRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehousing")
@RequiredArgsConstructor
@Tag(name = "Warehousing - Tariffs", description = "Effective-dated storage tariffs and minimum charges")
public class WhseTariffController {

    private final WhseTariffService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/storage-tariffs")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "Tariff versions, newest first. With clientId: that client's versions plus the warehouse default's.")
    public ResponseEntity<ApiResponse<List<WhseTariffService.TariffRow>>> tariffs(@RequestParam(required = false) UUID clientId) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.tariffs(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @PostMapping("/storage-tariffs")
    @PreAuthorize("hasAuthority('WAREHOUSING_ADMIN')")
    @Operation(summary = "Add a tariff version starting today or later. Past days keep the rate they were charged.")
    public ResponseEntity<ApiResponse<Map<String, UUID>>> addTariff(@Valid @RequestBody AddStorageTariffRequest req) {
        featureGuard.requireModule("warehousing");
        UUID id = service.addTariff(TenantContext.getTenantIdAsObject(), req.clientId(), req.itemId(), req.monthlyRate(),
                req.effectiveFrom(), req.note(), TenantContext.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Tariff added", Map.of("id", id)));
    }

    @GetMapping("/billing-terms")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<List<WhseTariffService.TermsRow>>> terms(@RequestParam(required = false) UUID clientId) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.terms(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @PostMapping("/billing-terms")
    @PreAuthorize("hasAuthority('WAREHOUSING_ADMIN')")
    @Operation(summary = "Add a minimum storage charge (per 30 days) starting today or later")
    public ResponseEntity<ApiResponse<Map<String, UUID>>> addTerms(@Valid @RequestBody AddBillingTermsRequest req) {
        featureGuard.requireModule("warehousing");
        UUID id = service.addTerms(TenantContext.getTenantIdAsObject(), req.clientId(), req.minimumStorageCharge(),
                req.effectiveFrom(), req.note(), TenantContext.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Billing terms added", Map.of("id", id)));
    }
}
