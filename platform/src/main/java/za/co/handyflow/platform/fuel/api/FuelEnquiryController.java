package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelEnquiryService;
import za.co.handyflow.platform.fuel.dto.EnquiryDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** B06: customer enquiries, turned into quotations or closed with a reason. Same divisions as orders (Wholesale or Distribution). */
@RestController
@RequestMapping("/api/v1/fuel/enquiries")
@RequiredArgsConstructor
@Tag(name = "Fuel Enquiries", description = "Customer enquiries and quotation requests")
public class FuelEnquiryController {

    private final FuelEnquiryService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<List<EnquiryResponse>>> list(@RequestParam(required = false) String status, @RequestParam(defaultValue = "100") int limit) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), status, limit)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_ORDERS_MANAGE')")
    public ResponseEntity<ApiResponse<EnquiryResponse>> create(@Valid @RequestBody EnquiryRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Enquiry recorded",
                service.create(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_ORDERS_MANAGE')")
    public ResponseEntity<ApiResponse<EnquiryResponse>> update(@PathVariable UUID id, @Valid @RequestBody EnquiryRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Enquiry updated", service.update(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/{id}/quote")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_ORDERS_MANAGE')")
    public ResponseEntity<ApiResponse<EnquiryResponse>> quote(@PathVariable UUID id, @Valid @RequestBody(required = false) QuoteRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Quotation raised",
                service.quote(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/lost")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_ORDERS_MANAGE')")
    public ResponseEntity<ApiResponse<EnquiryResponse>> lost(@PathVariable UUID id, @Valid @RequestBody CloseRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Marked as lost", service.lost(TenantContext.getTenantIdAsObject(), id, req.reason())));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_ORDERS_MANAGE')")
    public ResponseEntity<ApiResponse<EnquiryResponse>> cancel(@PathVariable UUID id, @Valid @RequestBody CloseRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Enquiry cancelled", service.cancel(TenantContext.getTenantIdAsObject(), id, req.reason())));
    }
}
