package za.co.handyflow.platform.trainingprovider.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvResourceBookingService;
import za.co.handyflow.platform.trainingprovider.dto.ResourceBookingDtos.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider/resource-bookings")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Resource bookings", description = "Booking site resources into timetable slots")
public class TrainProvResourceBookingController {

    private static final String READ = "hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String ADMIN_AUTHORITY = "TRAININGPROVIDER_ADMIN";

    private final TrainProvResourceBookingService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/slots/{slotId}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<BookingView>>> forSlot(@PathVariable UUID slotId) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.forSlot(TenantContext.getTenantIdAsObject(), slotId)));
    }

    @PostMapping("/slots/{slotId}/check")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CheckResult>> check(@PathVariable UUID slotId, @Valid @RequestBody BookRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.check(TenantContext.getTenantIdAsObject(), slotId, r)));
    }

    @PostMapping("/slots/{slotId}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<BookingView>> book(@PathVariable UUID slotId, @Valid @RequestBody BookRequest r, Authentication auth) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Resource booked", service.book(TenantContext.getTenantIdAsObject(), slotId, r, TenantContext.getCurrentUserId(), isAdmin(auth))));
    }

    @PostMapping("/{id}/release")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<BookingView>> release(@PathVariable UUID id, @Valid @RequestBody ReleaseRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Booking released", service.release(TenantContext.getTenantIdAsObject(), id, r.reason(), TenantContext.getCurrentUserId())));
    }

    @GetMapping("/resources/{resourceId}/schedule")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<ResourceSchedule>> schedule(@PathVariable UUID resourceId) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.schedule(TenantContext.getTenantIdAsObject(), resourceId, Instant.now())));
    }

    private static boolean isAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> ADMIN_AUTHORITY.equals(a.getAuthority()));
    }
}
