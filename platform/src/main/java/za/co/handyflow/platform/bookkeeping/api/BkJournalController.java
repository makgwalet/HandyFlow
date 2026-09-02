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
import za.co.handyflow.platform.bookkeeping.application.internal.BkJournalService;
import za.co.handyflow.platform.bookkeeping.dto.BkJournalEntryResponse;
import za.co.handyflow.platform.bookkeeping.dto.CreateBkJournalEntryRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookkeeping")
@RequiredArgsConstructor
@Tag(name = "Bookkeeping - Journal", description = "A client's own DRAFT->POSTED journal, period-locked")
public class BkJournalController {

    private final BkJournalService journalService;
    private final FeatureGuard featureGuard;

    @GetMapping("/clients/{clientId}/journal-entries")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_READ','BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<Page<BkJournalEntryResponse>>> getJournalEntries(
            @PathVariable UUID clientId, @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success(
                journalService.getJournalEntries(TenantContext.getTenantIdAsObject(), clientId, pageable)));
    }

    @GetMapping("/journal-entries/{id}")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_READ','BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<BkJournalEntryResponse>> getJournalEntry(@PathVariable UUID id) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success(journalService.getJournalEntry(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/journal-entries")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<BkJournalEntryResponse>> createJournal(@Valid @RequestBody CreateBkJournalEntryRequest request) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Journal entry created",
                journalService.createJournal(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), request)));
    }

    @PostMapping("/journal-entries/{id}/post")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<BkJournalEntryResponse>> postJournalEntry(@PathVariable UUID id) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success("Journal entry posted",
                journalService.postJournalEntry(TenantContext.getTenantIdAsObject(), id)));
    }
}
