package za.co.handyflow.platform.facilitiesmanagement.api;

import io.swagger.v3.oas.annotations.Operation;
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
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmQuoteService;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmQuoteRequest;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmQuoteResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmReasonRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/** Staff side of client quotes. Read: READ/MANAGE/ADMIN. Prepare, send, withdraw: MANAGE/ADMIN. Limited to the caller's client scope. */
@RestController
@RequestMapping("/api/v1/facilitiesmanagement/quotes")
@RequiredArgsConstructor
@Tag(name = "Facilities Management - Quotes", description = "Quotes sent to clients for approval in the portal")
public class FmQuoteController {

    private static final String CAN_READ = "hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')";
    private static final String CAN_WRITE = "hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')";

    private final FmQuoteService quoteService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<Page<FmQuoteResponse>>> list(@RequestParam(required = false) String status,
            @RequestParam(required = false) UUID clientId, @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(quoteService.list(TenantContext.getTenantIdAsObject(), status, clientId, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<FmQuoteResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(quoteService.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Prepare a draft quote (not visible to the client until sent)")
    public ResponseEntity<ApiResponse<FmQuoteResponse>> create(@Valid @RequestBody FmQuoteRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(quoteService.create(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Edit a draft quote (a sent quote is frozen — withdraw it and prepare a new one)")
    public ResponseEntity<ApiResponse<FmQuoteResponse>> update(@PathVariable UUID id, @Valid @RequestBody FmQuoteRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(quoteService.update(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/send")
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Send the quote to the client: it becomes visible in their portal and is frozen")
    public ResponseEntity<ApiResponse<FmQuoteResponse>> send(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(quoteService.send(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Withdraw a sent quote that has not been answered")
    public ResponseEntity<ApiResponse<FmQuoteResponse>> withdraw(@PathVariable UUID id, @Valid @RequestBody FmReasonRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(quoteService.withdraw(TenantContext.getTenantIdAsObject(), id, request.reason())));
    }
}
