package za.co.handyflow.platform.earthmoving.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.earthmoving.application.internal.EarthHireService;
import za.co.handyflow.platform.earthmoving.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/earthmoving/hire-charges")
@RequiredArgsConstructor
@Tag(name = "Earthmoving hire charges")
public class EarthHireController {

    private final EarthHireService hire;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    @Operation(summary = "Hire charges, newest first; optionally only PENDING, INVOICED or VOIDED")
    public ResponseEntity<ApiResponse<List<HireChargeResponse>>> list(@RequestParam(required = false) String status) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Hire charges", hire.list(TenantContext.getTenantIdAsObject(), status)));
    }

    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('EARTHMOVING_ADMIN')")
    @Operation(summary = "What a hire charge would be, with the same checks as raising it. Saves nothing")
    public ResponseEntity<ApiResponse<HireChargeResponse>> preview(@Valid @RequestBody HireChargeRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Preview", hire.preview(TenantContext.getTenantIdAsObject(), request)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EARTHMOVING_ADMIN')")
    @Operation(summary = "Raise a hire charge for one period of a deployment")
    public ResponseEntity<ApiResponse<HireChargeResponse>> create(@Valid @RequestBody HireChargeRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Hire charge raised",
                hire.create(TenantContext.getTenantIdAsObject(), request, UserContext.getCurrentUserId())));
    }

    @PatchMapping("/{id}/void")
    @PreAuthorize("hasAuthority('EARTHMOVING_ADMIN')")
    @Operation(summary = "Void a charge that is not yet on an invoice (reason required)")
    public ResponseEntity<ApiResponse<HireChargeResponse>> voidCharge(@PathVariable UUID id, @Valid @RequestBody HireReasonRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Hire charge voided",
                hire.voidCharge(TenantContext.getTenantIdAsObject(), id, request.reason(), UserContext.getCurrentUserId())));
    }

    @PatchMapping("/{id}/reopen")
    @PreAuthorize("hasAuthority('EARTHMOVING_ADMIN')")
    @Operation(summary = "Reopen an invoiced charge whose draft invoice no longer exists (reason required)")
    public ResponseEntity<ApiResponse<HireChargeResponse>> reopen(@PathVariable UUID id, @Valid @RequestBody HireReasonRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Hire charge reopened",
                hire.reopen(TenantContext.getTenantIdAsObject(), id, request.reason(), UserContext.getCurrentUserId())));
    }

    @PostMapping("/draft-invoice")
    @PreAuthorize("hasAuthority('EARTHMOVING_ADMIN')")
    @Operation(summary = "Put pending charges for one customer on a DRAFT invoice. The invoice is not issued")
    public ResponseEntity<ApiResponse<DraftInvoiceResponse>> draftInvoice(@Valid @RequestBody DraftFromChargesRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Draft invoice created",
                hire.draftInvoice(TenantContext.getTenantIdAsObject(), request, UserContext.getCurrentUserId())));
    }
}
