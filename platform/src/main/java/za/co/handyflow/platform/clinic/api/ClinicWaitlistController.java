package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicWaitlistService;
import za.co.handyflow.platform.clinic.dto.CreateWaitlistEntryRequest;
import za.co.handyflow.platform.clinic.dto.WaitlistEntryResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * FIX: "no waitlist" gap — cancellations/no-shows previously had no
 * mechanism to backfill from a waiting list.
 */
@RestController
@RequestMapping("/api/v1/clinic/waitlist")
@RequiredArgsConstructor
@Tag(name = "Clinic Waitlist", description = "Backfill list for cancellations/no-shows")
public class ClinicWaitlistController {

    private final ClinicWaitlistService waitlistService;

    @GetMapping
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "List active waitlist entries")
    public ResponseEntity<ApiResponse<List<WaitlistEntryResponse>>> getWaitlist() {
        return ResponseEntity.ok(ApiResponse.success("Success",
                waitlistService.getActiveWaitlist(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CLINIC_WRITE')")
    @Operation(summary = "Add a patient to the waitlist")
    public ResponseEntity<ApiResponse<WaitlistEntryResponse>> addToWaitlist(
            @Valid @RequestBody CreateWaitlistEntryRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Added to waitlist",
                waitlistService.addToWaitlist(TenantContext.getTenantIdAsObject(), req)));
    }

    @PostMapping("/{id}/contacted")
    @PreAuthorize("hasAuthority('CLINIC_WRITE')")
    @Operation(summary = "Mark a waitlist entry as contacted")
    public ResponseEntity<ApiResponse<Void>> markContacted(@PathVariable UUID id) {
        waitlistService.markContacted(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Marked contacted", null));
    }

    @PostMapping("/{id}/scheduled")
    @PreAuthorize("hasAuthority('CLINIC_WRITE')")
    @Operation(summary = "Mark a waitlist entry as scheduled — remove from the active list")
    public ResponseEntity<ApiResponse<Void>> markScheduled(@PathVariable UUID id) {
        waitlistService.markScheduled(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Marked scheduled", null));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CLINIC_WRITE')")
    @Operation(summary = "Remove a patient from the waitlist")
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable UUID id) {
        waitlistService.cancel(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Removed from waitlist", null));
    }
}