package za.co.handyflow.platform.contracting.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.contracting.application.internal.ContractingService;
import za.co.handyflow.platform.contracting.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
@Tag(name = "Contracting", description = "Contract lifecycle, templates and OTP signing")
public class ContractingController {

    private final ContractingService contractingService;

    // ── Templates ─────────────────────────────────────────────────────────────

    @GetMapping("/templates")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List contract templates (seeds 5 SA system templates on first call)")
    public ResponseEntity<ApiResponse<List<TemplateResponse>>> getTemplates() {
        return ResponseEntity.ok(ApiResponse.success("Success",
                contractingService.getTemplates(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/templates")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Create a custom contract template")
    public ResponseEntity<ApiResponse<TemplateResponse>> createTemplate(
            @Valid @RequestBody CreateTemplateRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Template created",
                contractingService.createTemplate(TenantContext.getTenantIdAsObject(), req)));
    }

    // ── Contracts ─────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List contracts with optional status and type filters")
    public ResponseEntity<ApiResponse<Page<ContractResponse>>> getContracts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                contractingService.getContracts(TenantContext.getTenantIdAsObject(),
                        status, type, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Get contract detail including parties and signing status")
    public ResponseEntity<ApiResponse<ContractResponse>> getContract(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                contractingService.getContract(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Create a new contract (from template or blank)")
    public ResponseEntity<ApiResponse<ContractResponse>> createContract(
            @Valid @RequestBody CreateContractRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Contract created",
                contractingService.createContract(TenantContext.getTenantIdAsObject(), req, null)));
    }

    @PostMapping("/{id}/submit-for-review")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Submit contract for internal review (DRAFT → UNDER_REVIEW)")
    public ResponseEntity<ApiResponse<ContractResponse>> submitForReview(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Submitted for review",
                contractingService.submitForReview(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/send-for-signing")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Send contract to all parties for signing")
    public ResponseEntity<ApiResponse<ContractResponse>> sendForSigning(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Sent for signing",
                contractingService.sendForSigning(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/terminate")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Terminate a signed contract")
    public ResponseEntity<ApiResponse<ContractResponse>> terminate(
            @PathVariable UUID id,
            @RequestBody TerminateContractRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Contract terminated",
                contractingService.terminate(TenantContext.getTenantIdAsObject(), id, req.reason())));
    }

    // ── Parties ───────────────────────────────────────────────────────────────

    @PostMapping("/{id}/parties")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Add a party to a contract")
    public ResponseEntity<ApiResponse<PartyResponse>> addParty(
            @PathVariable UUID id,
            @Valid @RequestBody AddPartyRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Party added",
                contractingService.addParty(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/{id}/parties/{partyId}/request-otp")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Send OTP to party's phone number for signing")
    public ResponseEntity<ApiResponse<PartyResponse>> requestOtp(
            @PathVariable UUID id, @PathVariable UUID partyId) {
        return ResponseEntity.ok(ApiResponse.success("OTP sent",
                contractingService.requestOtp(TenantContext.getTenantIdAsObject(), id, partyId)));
    }

    @PostMapping("/{id}/parties/{partyId}/sign")
    @Operation(summary = "Sign contract with OTP (public — no auth required for counterparties)")
    public ResponseEntity<ApiResponse<ContractResponse>> sign(
            @PathVariable UUID id,
            @PathVariable UUID partyId,
            @Valid @RequestBody SignContractRequest req,
            HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        String ua = httpRequest.getHeader("User-Agent");
        return ResponseEntity.ok(ApiResponse.success("Contract signed",
                contractingService.signContract(TenantContext.getTenantIdAsObject(),
                        id, partyId, req, ip, ua)));
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Download signed contract as PDF")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID id) {
        byte[] pdf = contractingService.generatePdf(
                TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition",
                        "attachment; filename=\"" + id + ".pdf\"")
                .body(pdf);
    }
}
