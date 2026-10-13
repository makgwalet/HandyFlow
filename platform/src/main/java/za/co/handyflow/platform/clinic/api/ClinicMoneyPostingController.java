package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicMoneyPostingService;
import za.co.handyflow.platform.clinic.dto.billing.MoneyPostingDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clinic/billing/money-postings")
@RequiredArgsConstructor
@Tag(name = "Clinic money postings", description = "Scheme payments and write-offs on their way to the general ledger")
public class ClinicMoneyPostingController {

    private final ClinicMoneyPostingService postings;

    @GetMapping
    @PreAuthorize("hasAuthority('CLINIC_CLAIM_READ')")
    @Operation(summary = "Scheme payments waiting for a bank account, and postings that failed")
    public ResponseEntity<ApiResponse<List<PostingView>>> waiting() {
        return ResponseEntity.ok(ApiResponse.success("Success", postings.waiting(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/banks")
    @PreAuthorize("hasAuthority('CLINIC_PAYMENT_ALLOCATE')")
    @Operation(summary = "Bank accounts a scheme payment can be posted to")
    public ResponseEntity<ApiResponse<List<BankChoice>>> banks() {
        return ResponseEntity.ok(ApiResponse.success("Success", postings.banks(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/post")
    @PreAuthorize("hasAuthority('CLINIC_PAYMENT_ALLOCATE')")
    @Operation(summary = "Post waiting scheme payments (into the chosen bank account) and failed write-offs to the general ledger")
    public ResponseEntity<ApiResponse<PostResult>> post(@RequestBody PostRequest body) {
        UUID me;
        try { me = UserContext.getCurrentUserId(); } catch (RuntimeException e) { me = null; }
        return ResponseEntity.ok(ApiResponse.success("Done",
                postings.postWaiting(TenantContext.getTenantIdAsObject(), body.bankAccountId(), body.postingIds(), me)));
    }
}
