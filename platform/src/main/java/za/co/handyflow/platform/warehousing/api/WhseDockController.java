package za.co.handyflow.platform.warehousing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.warehousing.application.internal.WhseDockService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehousing/dock-appointments")
@RequiredArgsConstructor
@Tag(name = "Warehousing - Dock appointments", description = "Book carriers into dock doors for deliveries and collections")
public class WhseDockController {

    private static final String READ = "hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')";
    private static final String WRITE = "hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')";

    private final WhseDockService service;
    private final FeatureGuard featureGuard;

    public record BookRequest(@NotNull String direction, @NotNull UUID dockLocationId, @NotNull Instant startsAt, @NotNull Instant endsAt,
                              @Size(max = 120) String carrier, @Size(max = 30) String vehicleRegistration, @Size(max = 120) String driverName,
                              UUID inboundShipmentId, UUID manifestId, @Size(max = 300) String notes) {}
    public record MoveRequest(@NotNull UUID dockLocationId, @NotNull Instant startsAt, @NotNull Instant endsAt) {}
    public record ReasonRequest(@NotNull @Size(min = 5, max = 300) String reason) {}

    @GetMapping
    @PreAuthorize(READ)
    @Operation(summary = "Appointments starting in [from, to), optionally for one door")
    public ResponseEntity<ApiResponse<List<WhseDockService.Appointment>>> list(@RequestParam Instant from, @RequestParam Instant to, @RequestParam(required = false) UUID dockLocationId) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), from, to, dockLocationId)));
    }

    @PostMapping
    @PreAuthorize(WRITE)
    @Operation(summary = "Book a delivery or collection into a dock door. A door cannot be double-booked.")
    public ResponseEntity<ApiResponse<Map<String, UUID>>> book(@Valid @RequestBody BookRequest r) {
        featureGuard.requireModule("warehousing");
        UUID id = service.book(TenantContext.getTenantIdAsObject(), new WhseDockService.Booking(r.direction(), r.dockLocationId(), r.startsAt(), r.endsAt(), r.carrier(),
                r.vehicleRegistration(), r.driverName(), r.inboundShipmentId(), r.manifestId(), r.notes()), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Booked", Map.of("id", id)));
    }

    @PutMapping("/{id}/slot")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<Void>> move(@PathVariable UUID id, @Valid @RequestBody MoveRequest r) {
        featureGuard.requireModule("warehousing");
        service.reschedule(TenantContext.getTenantIdAsObject(), id, r.dockLocationId(), r.startsAt(), r.endsAt(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Moved", null));
    }

    @PostMapping("/{id}/arrived")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<Void>> arrived(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        service.arrived(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Marked arrived", null));
    }

    @PostMapping("/{id}/at-dock")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<Void>> atDock(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        service.atDock(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Marked at dock", null));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<Void>> complete(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        service.complete(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Completed", null));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        featureGuard.requireModule("warehousing");
        service.cancel(TenantContext.getTenantIdAsObject(), id, r.reason(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Cancelled", null));
    }

    @PostMapping("/{id}/no-show")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<Void>> noShow(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        featureGuard.requireModule("warehousing");
        service.noShow(TenantContext.getTenantIdAsObject(), id, r.reason(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Marked no-show", null));
    }
}
