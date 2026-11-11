package za.co.handyflow.platform.events.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.events.application.internal.GuestImportService;
import za.co.handyflow.platform.events.application.internal.GuestOperationsService;
import za.co.handyflow.platform.events.application.internal.WaitlistService;
import za.co.handyflow.platform.events.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantContext;

import java.io.IOException;
import java.util.UUID;

/**
 * Registration extras: group registration, walk-ins, ticket transfers, bulk
 * CSV import and the waitlist. Kept separate from EventsController so that
 * file stays focused on the event lifecycle; both share the same base path.
 */
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Tag(name = "Event registration", description = "Group registration, walk-ins, transfers, CSV import and waitlist")
public class EventRegistrationController {

    private static final long MAX_IMPORT_BYTES = 2L * 1024 * 1024;   // 2 MB — 500 rows is nowhere near this

    private final GuestOperationsService guestOps;
    private final GuestImportService     importService;
    private final WaitlistService        waitlistService;

    // ── Group / walk-in / transfer ────────────────────────────────────────────

    @PostMapping("/{id}/guests/group")
    @PreAuthorize(EventsPermissions.REGISTRATION)
    @Operation(summary = "Register a group (up to 50) — all or nothing; every member gets a ticket")
    public ResponseEntity<ApiResponse<GroupRegistrationResponse>> registerGroup(
            @PathVariable UUID id,
            @Valid @RequestBody GroupRegistrationRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Group registered",
                guestOps.registerGroup(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/{id}/guests/walk-in")
    @PreAuthorize(EventsPermissions.WALK_IN)
    @Operation(summary = "Register and check in a walk-in at the door — event must be LIVE")
    public ResponseEntity<ApiResponse<CheckInResponse>> walkIn(
            @PathVariable UUID id,
            @Valid @RequestBody WalkInRequest req) {
        UUID scannedBy = TenantContext.getCurrentUserId();
        return ResponseEntity.status(201).body(ApiResponse.success("Walk-in checked in",
                guestOps.walkIn(TenantContext.getTenantIdAsObject(), id, req, scannedBy)));
    }

    @PostMapping("/{id}/guests/{guestId}/transfer")
    @PreAuthorize(EventsPermissions.REGISTRATION)
    @Operation(summary = "Transfer a ticket to another person — issues a new ticket and QR; the old QR stops working")
    public ResponseEntity<ApiResponse<GuestResponse>> transfer(
            @PathVariable UUID id, @PathVariable UUID guestId,
            @Valid @RequestBody TransferGuestRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Ticket transferred",
                guestOps.transferGuest(TenantContext.getTenantIdAsObject(), id, guestId, req)));
    }

    // ── CSV import ────────────────────────────────────────────────────────────

    @PostMapping(value = "/{id}/guests/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(EventsPermissions.REGISTRATION)
    @Operation(summary = "Bulk-import guests from CSV (max 500 rows). dryRun defaults to TRUE — "
            + "preview first, then repeat with dryRun=false and consentConfirmed=true")
    public ResponseEntity<ApiResponse<ImportReport>> importGuests(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) UUID tierId,
            @RequestParam(defaultValue = "false") boolean sendTickets,
            @RequestParam(defaultValue = "true") boolean dryRun,
            @RequestParam(defaultValue = "false") boolean consentConfirmed) {

        if (file == null || file.isEmpty()) {
            throw new HandyFlowException("A CSV file is required", HttpStatus.BAD_REQUEST, "FILE_REQUIRED");
        }
        if (file.getSize() > MAX_IMPORT_BYTES) {
            throw new HandyFlowException("The file is too large — the maximum is 2MB",
                    HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new HandyFlowException("Failed to read the uploaded file",
                    HttpStatus.INTERNAL_SERVER_ERROR, "READ_FAILED");
        }

        ImportReport report = importService.importGuests(TenantContext.getTenantIdAsObject(), id,
                content, tierId, sendTickets, dryRun, consentConfirmed);
        return ResponseEntity.ok(ApiResponse.success(
                dryRun ? "Dry run complete — nothing was saved" : "Import complete", report));
    }

    // ── Waitlist ──────────────────────────────────────────────────────────────

    @GetMapping("/{id}/waitlist")
    @PreAuthorize(EventsPermissions.READ)
    @Operation(summary = "List the waitlist, oldest first (status defaults to WAITING)")
    public ResponseEntity<ApiResponse<Page<WaitlistEntryResponse>>> getWaitlist(
            @PathVariable UUID id,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                waitlistService.list(TenantContext.getTenantIdAsObject(), id, status, pageable)));
    }

    @PostMapping("/{id}/waitlist")
    @PreAuthorize(EventsPermissions.REGISTRATION)
    @Operation(summary = "Join the waitlist — only when the event or tier is genuinely full")
    public ResponseEntity<ApiResponse<WaitlistEntryResponse>> joinWaitlist(
            @PathVariable UUID id,
            @Valid @RequestBody JoinWaitlistRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Added to the waitlist",
                waitlistService.join(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/{id}/waitlist/{entryId}/promote")
    @PreAuthorize(EventsPermissions.REGISTRATION)
    @Operation(summary = "Promote a waiting entry now — needs a free place; overrides deadline and sale window")
    public ResponseEntity<ApiResponse<GuestResponse>> promote(
            @PathVariable UUID id, @PathVariable UUID entryId) {
        return ResponseEntity.ok(ApiResponse.success("Promoted from the waitlist",
                waitlistService.promote(TenantContext.getTenantIdAsObject(), id, entryId)));
    }

    @DeleteMapping("/{id}/waitlist/{entryId}")
    @PreAuthorize(EventsPermissions.REGISTRATION)
    @Operation(summary = "Remove an entry from the waitlist")
    public ResponseEntity<ApiResponse<Void>> removeFromWaitlist(
            @PathVariable UUID id, @PathVariable UUID entryId) {
        waitlistService.remove(TenantContext.getTenantIdAsObject(), id, entryId);
        return ResponseEntity.ok(ApiResponse.success("Removed from the waitlist", null));
    }
}