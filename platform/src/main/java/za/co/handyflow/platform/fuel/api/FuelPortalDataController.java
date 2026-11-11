package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.fuel.application.internal.FuelPortalDataService;
import za.co.handyflow.platform.fuel.dto.PortalDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;

import java.util.List;
import java.util.UUID;

/** Customer-facing reads and the order request. The path contains /portal/, which is what routes it to the portal token filter. */
@RestController
@RequestMapping("/api/v1/fuel/portal")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PORTAL_USER')")
@Tag(name = "Fuel Customer Portal", description = "Customer-facing data")
public class FuelPortalDataController {

    private final FuelPortalDataService data;

    @GetMapping("/accounts")
    public ResponseEntity<ApiResponse<List<AccountSummary>>> accounts() {
        return ResponseEntity.ok(ApiResponse.success(data.myAccounts(userId())));
    }

    @GetMapping("/accounts/{accountId}/sites")
    public ResponseEntity<ApiResponse<List<SiteSummary>>> sites(@PathVariable UUID accountId) {
        return ResponseEntity.ok(ApiResponse.success(data.mySites(userId(), accountId)));
    }

    @GetMapping("/accounts/{accountId}/orders")
    public ResponseEntity<ApiResponse<List<OrderView>>> orders(@PathVariable UUID accountId) {
        return ResponseEntity.ok(ApiResponse.success(data.myOrders(userId(), accountId)));
    }

    @GetMapping("/accounts/{accountId}/deliveries")
    public ResponseEntity<ApiResponse<List<DeliveryView>>> deliveries(@PathVariable UUID accountId) {
        return ResponseEntity.ok(ApiResponse.success(data.myDeliveries(userId(), accountId)));
    }

    @PostMapping("/accounts/{accountId}/order-requests")
    public ResponseEntity<ApiResponse<OrderView>> requestOrder(@PathVariable UUID accountId, @Valid @RequestBody OrderRequestBody req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Request sent", data.requestOrder(userId(), accountId, req)));
    }

    private UUID userId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
    }
}
