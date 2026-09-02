package za.co.handyflow.platform.bookkeeping.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.bookkeeping.application.internal.BkTimeEntryService;
import za.co.handyflow.platform.bookkeeping.dto.BkTimeEntryResponse;
import za.co.handyflow.platform.bookkeeping.dto.CreateBkTimeEntryRequest;
import za.co.handyflow.platform.bookkeeping.dto.UpdateBkTimeEntryRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookkeeping")
@RequiredArgsConstructor
@Tag(name = "Bookkeeping - Time Entries", description = "Staff hours logged against a TIME_AND_MATERIALS client")
public class BkTimeEntryController {

    private final BkTimeEntryService timeEntryService;
    private final FeatureGuard featureGuard;

    @GetMapping("/clients/{clientId}/time-entries")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_READ','BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<Page<BkTimeEntryResponse>>> getTimeEntries(
            @PathVariable UUID clientId, @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success(
                timeEntryService.getTimeEntries(TenantContext.getTenantIdAsObject(), clientId, pageable)));
    }

    @GetMapping("/clients/{clientId}/time-entries/unbilled")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_READ','BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<List<BkTimeEntryResponse>>> getUnbilled(@PathVariable UUID clientId) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success(
                timeEntryService.getUnbilledByClient(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @GetMapping("/time-entries/{id}")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_READ','BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<BkTimeEntryResponse>> getTimeEntry(@PathVariable UUID id) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success(timeEntryService.getTimeEntry(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/time-entries")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<BkTimeEntryResponse>> logTime(@Valid @RequestBody CreateBkTimeEntryRequest request) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Time entry logged",
                timeEntryService.logTime(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/time-entries/{id}")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<BkTimeEntryResponse>> update(@PathVariable UUID id, @Valid @RequestBody UpdateBkTimeEntryRequest request) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success("Time entry updated",
                timeEntryService.update(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/time-entries/{id}/write-off")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<BkTimeEntryResponse>> writeOff(@PathVariable UUID id) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success("Time entry written off",
                timeEntryService.writeOff(TenantContext.getTenantIdAsObject(), id)));
    }

    @DeleteMapping("/time-entries/{id}")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        featureGuard.requireModule("bookkeeping");
        timeEntryService.delete(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Time entry deleted", null));
    }
}
