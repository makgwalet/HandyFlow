package za.co.handyflow.platform.admin.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.admin.application.internal.AdminInvoiceService;
import za.co.handyflow.platform.shared.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
@Tag(name = "Admin Invoicing", description = "Generate, send and manage tenant invoices")
public class AdminInvoiceController {

    private final AdminInvoiceService invoiceService;

    // ── Invoice list ──────────────────────────────────────────────────────────

    @GetMapping("/invoices")
    @Operation(summary = "List all tenant invoices — filter by tenant slug or status")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getInvoices(
            @RequestParam(required = false) String tenantSlug,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                invoiceService.getInvoices(tenantSlug, status, page, size)));
    }

    @GetMapping("/invoices/{id}")
    @Operation(summary = "Get invoice detail")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getInvoice(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(invoiceService.getInvoice(id)));
    }

    @GetMapping("/invoices/{id}/pdf")
    @Operation(summary = "Download invoice PDF")
    public ResponseEntity<byte[]> getInvoicePdf(@PathVariable UUID id) {
        byte[] pdf = invoiceService.getInvoicePdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // ── Tenant-scoped invoice list ─────────────────────────────────────────────

    @GetMapping("/tenants/{slug}/invoices")
    @Operation(summary = "List invoices for a specific tenant")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTenantInvoices(
            @PathVariable String slug,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                invoiceService.getInvoices(slug, status, page, size)));
    }

    // ── Invoice actions ────────────────────────────────────────────────────────

    @PostMapping("/tenants/{slug}/invoices")
    @Operation(summary = "Generate a tax invoice for a tenant for the given period")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateInvoice(
            @PathVariable String slug,
            @RequestParam int year,
            @RequestParam int month,
            HttpServletRequest http) {
        Map<String, Object> inv = invoiceService.generateInvoice(
                slug, year, month, getAdminId(), getAdminEmail());
        return ResponseEntity.status(201)
                .body(ApiResponse.success("Invoice generated", inv));
    }

    @PostMapping("/invoices/{id}/send")
    @Operation(summary = "Send invoice to tenant by email — moves status from DRAFT to SENT")
    public ResponseEntity<ApiResponse<Void>> sendInvoice(
            @PathVariable UUID id,
            HttpServletRequest http) {
        invoiceService.sendInvoice(id, getAdminId(), getAdminEmail());
        return ResponseEntity.ok(ApiResponse.success("Invoice sent", null));
    }

    @PostMapping("/invoices/{id}/mark-paid")
    @Operation(summary = "Mark invoice as paid — restores PAST_DUE subscriptions to ACTIVE")
    public ResponseEntity<ApiResponse<Void>> markPaid(
            @PathVariable UUID id,
            HttpServletRequest http) {
        invoiceService.markPaid(id, getAdminId(), getAdminEmail());
        return ResponseEntity.ok(ApiResponse.success("Invoice marked as paid", null));
    }

    @PostMapping("/invoices/{id}/void")
    @Operation(summary = "Void an invoice — cannot be undone, cannot void a PAID invoice")
    public ResponseEntity<ApiResponse<Void>> voidInvoice(
            @PathVariable UUID id,
            HttpServletRequest http) {
        invoiceService.voidInvoice(id, getAdminId(), getAdminEmail());
        return ResponseEntity.ok(ApiResponse.success("Invoice voided", null));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private UUID getAdminId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        return UUID.fromString(auth.getPrincipal().toString());
    }

    @SuppressWarnings("unchecked")
    private String getAdminEmail() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof java.util.Map) {
            var details = (java.util.Map<String, String>) auth.getDetails();
            String email = details.get("email");
            if (email != null && !email.isBlank()) return email;
        }
        return "unknown-admin";
    }
}
