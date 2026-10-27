package za.co.handyflow.platform.hr.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.hr.application.internal.PayRunPaymentService;
import za.co.handyflow.platform.hr.application.internal.PayrollAccess;
import za.co.handyflow.platform.hr.dto.PayRunPaymentDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.UUID;

/** The bank payment file for a finalised pay run. Holds everyone's bank details, so only people who may finalise a run can use it. */
@RestController
@RequestMapping("/api/v1/hr")
@RequiredArgsConstructor
@Tag(name = "Pay run payments", description = "Bank file and paid state for a finalised pay run")
public class PayRunPaymentController {

    private final PayRunPaymentService service;

    @GetMapping("/pay-runs/{id}/payments")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "The payments of a pay run and their state. Account numbers are masked")
    public ResponseEntity<ApiResponse<Summary>> summary(@PathVariable UUID id, Authentication a) {
        if (!PayrollAccess.canReview(a)) throw new AccessDeniedException("You may not see pay run payments.");
        return ResponseEntity.ok(ApiResponse.success(service.summary(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/pay-runs/{id}/payments/make-up")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Make up the payments from a finalised run's payslips. Lists anyone left out and why")
    public ResponseEntity<ApiResponse<Summary>> makeUp(@PathVariable UUID id, Authentication a, Principal p) {
        requireFinalise(a);
        return ResponseEntity.ok(ApiResponse.success("Payments made up", service.makeUp(TenantContext.getTenantIdAsObject(), id, user(p))));
    }

    @GetMapping(value = "/pay-runs/{id}/payments/file", produces = "text/csv")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Download the bank payment file (CSV) for the pending payments")
    public ResponseEntity<byte[]> file(@PathVariable UUID id, Authentication a, Principal p) {
        requireFinalise(a);
        FileResult f = service.file(TenantContext.getTenantIdAsObject(), id, user(p));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + f.filename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(f.csv().getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/pay-runs/{id}/payments/confirm-paid")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Confirm that the bank accepted the file: pending payments become paid")
    public ResponseEntity<ApiResponse<Summary>> confirmPaid(@PathVariable UUID id, @RequestBody(required = false) ActionRequest req, Authentication a, Principal p) {
        requireFinalise(a);
        return ResponseEntity.ok(ApiResponse.success("Payments confirmed",
                service.confirmPaid(TenantContext.getTenantIdAsObject(), id, user(p), req == null ? null : req.note())));
    }

    @PostMapping("/pay-run-payments/{id}/return")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Record that the bank returned one payment, with the reason")
    public ResponseEntity<ApiResponse<Summary>> markReturned(@PathVariable UUID id, @RequestBody ReturnRequest req, Authentication a, Principal p) {
        requireFinalise(a);
        return ResponseEntity.ok(ApiResponse.success("Payment marked as returned",
                service.markReturned(TenantContext.getTenantIdAsObject(), id, req == null ? null : req.reason(), user(p))));
    }

    private static void requireFinalise(Authentication a) {
        if (!PayrollAccess.canFinalise(a)) throw new AccessDeniedException("Only people who may finalise a pay run can make up or pay its bank file.");
    }

    private static UUID user(Principal principal) {
        if (principal == null) return null;
        try { return UUID.fromString(principal.getName()); } catch (Exception e) { return null; }
    }
}
