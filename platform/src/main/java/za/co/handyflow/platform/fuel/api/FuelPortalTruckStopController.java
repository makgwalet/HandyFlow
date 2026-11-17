package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.fuel.application.internal.FuelStayRequestService;
import za.co.handyflow.platform.fuel.dto.StayRequestDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Driver self-service at the truck stop, for a customer's portal user. The path contains /portal/, which routes it to the portal token filter; the grant on the account decides access. */
@RestController
@RequestMapping("/api/v1/fuel/portal/accounts/{accountId}/truck-stop")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PORTAL_USER')")
@Tag(name = "Fuel Customer Portal: Truck Stop", description = "Free bays, stays on the account, and overnight stay requests")
public class FuelPortalTruckStopController {

    private final FuelStayRequestService service;

    @GetMapping("/availability")
    public ResponseEntity<ApiResponse<PortalAvailability>> availability(@PathVariable UUID accountId,
                                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                        @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(ApiResponse.success(service.availability(userId(), accountId, from, days)));
    }

    @GetMapping("/stays")
    public ResponseEntity<ApiResponse<List<PortalStayView>>> stays(@PathVariable UUID accountId) {
        return ResponseEntity.ok(ApiResponse.success(service.myStays(userId(), accountId)));
    }

    @GetMapping("/stay-requests")
    public ResponseEntity<ApiResponse<List<PortalStayRequestView>>> requests(@PathVariable UUID accountId) {
        return ResponseEntity.ok(ApiResponse.success(service.myRequests(userId(), accountId)));
    }

    @PostMapping("/stay-requests")
    public ResponseEntity<ApiResponse<PortalStayRequestView>> request(@PathVariable UUID accountId, @Valid @RequestBody StayRequestBody body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Request sent", service.request(userId(), accountId, body)));
    }

    @PostMapping("/stay-requests/{requestId}/cancel")
    public ResponseEntity<ApiResponse<PortalStayRequestView>> cancel(@PathVariable UUID accountId, @PathVariable UUID requestId) {
        return ResponseEntity.ok(ApiResponse.success("Request cancelled", service.cancel(userId(), accountId, requestId)));
    }

    private UUID userId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
    }
}
