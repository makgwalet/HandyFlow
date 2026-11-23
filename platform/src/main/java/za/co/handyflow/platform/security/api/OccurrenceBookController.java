// security/api/OccurrenceBookController.java
package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.DeviceSessionService;
import za.co.handyflow.platform.security.application.internal.OccurrenceBookService;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Security - Occurrence Book")
public class OccurrenceBookController {

    public record EntryRequest(@NotBlank String kind, @NotBlank String text, Instant recordedAt, String clientRef) {}

    public record SignRequest(String note) {}

    private final OccurrenceBookService book;
    private final DeviceSessionService sessions;
    private final FeatureGuard featureGuard;

    /** Optional so a hand-built controller still works; in the application it is always there. Limits a user to the sites in their scope. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private za.co.handyflow.platform.security.application.internal.SiteScopeService scope;

    private void site(UUID siteId) {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.check(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), siteId);
    }


    @GetMapping("/api/v1/security/sites/{siteId}/occurrence-book")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "The Occurrence Book of a site for one day (South African time), oldest first",
            description = "Duty changes, patrols, gate movements (company and vehicle only, no names or ID numbers), incidents, alarms and hand-written entries. Default is today.")
    public ResponseEntity<ApiResponse<OccurrenceBookService.Day>> day(
            @PathVariable UUID siteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        featureGuard.requireModule("security");
        site(siteId);
        return ResponseEntity.ok(ApiResponse.success("OK", book.day(TenantContext.getTenantIdAsObject(), siteId, date, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/api/v1/security/sites/{siteId}/occurrence-book/entries")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "A supervisor adds an entry (NOTE, HANDOVER or CORRECTION). Entries cannot be edited or deleted.")
    public ResponseEntity<ApiResponse<Void>> supervisorEntry(@PathVariable UUID siteId, @Valid @RequestBody EntryRequest r) {
        featureGuard.requireModule("security");
        site(siteId);
        book.addEntry(TenantContext.getTenantIdAsObject(), siteId, null, TenantContext.getCurrentUserId(),
                r.kind(), true, r.text(), r.recordedAt(), r.clientRef());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/api/v1/security/sites/{siteId}/occurrence-book/sign")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "A supervisor signs the day. A signed day accepts only corrections and cannot be unsigned.",
            description = "Signing twice is harmless: the first signature stays. A future day is refused.")
    public ResponseEntity<ApiResponse<OccurrenceBookService.Signoff>> sign(
            @PathVariable UUID siteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody(required = false) SignRequest r) {
        featureGuard.requireModule("security");
        site(siteId);
        return ResponseEntity.ok(ApiResponse.success("OK", book.sign(TenantContext.getTenantIdAsObject(), siteId, date,
                TenantContext.getCurrentUserId(), r == null ? null : r.note())));
    }

    @PostMapping("/api/v1/guard/ob/entries")
    @PreAuthorize("hasAuthority('SECURITY_GUARD')")
    @Operation(summary = "A guard adds a NOTE or HANDOVER to the Occurrence Book of the site they are on duty at",
            description = "The site comes from the guard's open session, never from the request. Safe to retry with the same clientRef.")
    public ResponseEntity<ApiResponse<Void>> guardEntry(@Valid @RequestBody EntryRequest r) {
        featureGuard.requireModule("security");
        UUID guard = TenantContext.getCurrentUserId();
        UUID site = sessions.resolveCurrentSiteForGuard(guard);
        book.addEntry(TenantContext.getTenantIdAsObject(), site, guard, guard, r.kind(), false, r.text(), r.recordedAt(), r.clientRef());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
