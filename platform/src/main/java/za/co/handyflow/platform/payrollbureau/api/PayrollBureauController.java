package za.co.handyflow.platform.payrollbureau.api;

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
import za.co.handyflow.platform.payrollbureau.application.internal.PayrollBureauService;
import za.co.handyflow.platform.payrollbureau.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Foundation-layer endpoints only — practice profile and client
 * portfolio. FeatureGuard gated same as every other separately-
 * subscribable module (see billing.FeatureGuard usage elsewhere) —
 * this is an add-on module, not bundled with internal `hr`.
 */
@RestController
@RequestMapping("/api/v1/payroll-bureau")
@RequiredArgsConstructor
@Tag(name = "Payroll Bureau", description = "Multi-client payroll bureau practice management")
public class PayrollBureauController {

    private final PayrollBureauService bureauService;
    private final FeatureGuard featureGuard;

    // ── Practice profile ─────────────────────────────────────────────────────

    @GetMapping("/profile")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_READ','PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    @Operation(summary = "Get the bureau's own practice profile")
    public ResponseEntity<ApiResponse<BureauProfileResponse>> getProfile() {
        featureGuard.requireModule("payrollbureau");
        return ResponseEntity.ok(ApiResponse.success(
                bureauService.getProfile(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/profile")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    @Operation(summary = "Create or update the bureau's practice profile")
    public ResponseEntity<ApiResponse<BureauProfileResponse>> upsertProfile(
            @Valid @RequestBody UpdateBureauProfileRequest req) {
        featureGuard.requireModule("payrollbureau");
        return ResponseEntity.ok(ApiResponse.success("Profile saved",
                bureauService.upsertProfile(TenantContext.getTenantIdAsObject(), req)));
    }

    // ── Client portfolio ──────────────────────────────────────────────────────

    @GetMapping("/clients")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_READ','PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    @Operation(summary = "List active payroll clients")
    public ResponseEntity<ApiResponse<Page<PayClientResponse>>> getClients(
            @org.springframework.data.web.PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("payrollbureau");
        return ResponseEntity.ok(ApiResponse.success(
                bureauService.getClients(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @GetMapping("/clients/{id}")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_READ','PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    public ResponseEntity<ApiResponse<PayClientResponse>> getClient(@PathVariable UUID id) {
        featureGuard.requireModule("payrollbureau");
        return ResponseEntity.ok(ApiResponse.success(
                bureauService.getClient(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/clients")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    @Operation(summary = "Onboard a new payroll client")
    public ResponseEntity<ApiResponse<PayClientResponse>> createClient(
            @Valid @RequestBody CreatePayClientRequest req) {
        featureGuard.requireModule("payrollbureau");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Client onboarded",
                bureauService.createClient(TenantContext.getTenantIdAsObject(), req)));
    }

    @PutMapping("/clients/{id}")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    public ResponseEntity<ApiResponse<PayClientResponse>> updateClient(
            @PathVariable UUID id, @Valid @RequestBody CreatePayClientRequest req) {
        featureGuard.requireModule("payrollbureau");
        return ResponseEntity.ok(ApiResponse.success("Client updated",
                bureauService.updateClient(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/clients/{id}/offboard")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    public ResponseEntity<ApiResponse<PayClientResponse>> offboardClient(@PathVariable UUID id) {
        featureGuard.requireModule("payrollbureau");
        return ResponseEntity.ok(ApiResponse.success("Client offboarded",
                bureauService.offboardClient(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/clients/{id}/reactivate")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    public ResponseEntity<ApiResponse<PayClientResponse>> reactivateClient(@PathVariable UUID id) {
        featureGuard.requireModule("payrollbureau");
        return ResponseEntity.ok(ApiResponse.success("Client reactivated",
                bureauService.reactivateClient(TenantContext.getTenantIdAsObject(), id)));
    }

    @DeleteMapping("/clients/{id}")
    @PreAuthorize("hasAuthority('PAYROLLBUREAU_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteClient(@PathVariable UUID id) {
        featureGuard.requireModule("payrollbureau");
        bureauService.deleteClient(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Client deleted", null));
    }

    @GetMapping("/clients/{clientId}/employees")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_READ','PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    public ResponseEntity<ApiResponse<List<PayEmployeeResponse>>> getEmployees(@PathVariable UUID clientId) {
        featureGuard.requireModule("payrollbureau");
        return ResponseEntity.ok(ApiResponse.success(
                bureauService.getEmployees(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @PostMapping("/clients/{clientId}/employees")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    public ResponseEntity<ApiResponse<PayEmployeeResponse>> createEmployee(
            @PathVariable UUID clientId, @Valid @RequestBody CreatePayEmployeeRequest req) {
        featureGuard.requireModule("payrollbureau");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Employee added",
                bureauService.createEmployee(TenantContext.getTenantIdAsObject(), clientId, req)));
    }

    @GetMapping("/clients/{clientId}/pay-runs")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_READ','PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    public ResponseEntity<ApiResponse<Page<PayRunResponse>>> getPayRuns(
            @PathVariable UUID clientId, @PageableDefault(size = 24) Pageable pageable) {
        featureGuard.requireModule("payrollbureau");
        return ResponseEntity.ok(ApiResponse.success(
                bureauService.getPayRuns(TenantContext.getTenantIdAsObject(), clientId, pageable)));
    }

    @PostMapping("/clients/{clientId}/pay-runs")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    public ResponseEntity<ApiResponse<PayRunResponse>> createPayRun(
            @PathVariable UUID clientId, @Valid @RequestBody CreatePayRunRequest req) {
        featureGuard.requireModule("payrollbureau");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Pay run created",
                bureauService.createPayRun(TenantContext.getTenantIdAsObject(), clientId, req)));
    }

    @PostMapping("/pay-runs/{payRunId}/process")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    @Operation(summary = "Calculate PAYE/UIF/SDL and generate payslips for every employee in this run")
    public ResponseEntity<ApiResponse<PayRunResponse>> processPayRun(@PathVariable UUID payRunId) {
        featureGuard.requireModule("payrollbureau");
        return ResponseEntity.ok(ApiResponse.success("Pay run processed",
                bureauService.processPayRun(TenantContext.getTenantIdAsObject(), payRunId)));
    }

    @GetMapping("/pay-runs/{payRunId}/payslips")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_READ','PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    public ResponseEntity<ApiResponse<List<PayslipResponse>>> getPayslips(@PathVariable UUID payRunId) {
        featureGuard.requireModule("payrollbureau");
        return ResponseEntity.ok(ApiResponse.success(
                bureauService.getPayslips(TenantContext.getTenantIdAsObject(), payRunId)));
    }

    @GetMapping("/pay-runs/{payRunId}/payslips/{payslipId}/pdf")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_READ','PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    @Operation(summary = "Download/print a single payslip as PDF — works regardless of whether the employee has an email on file")
    public ResponseEntity<byte[]> downloadPayslipPdf(@PathVariable UUID payRunId, @PathVariable UUID payslipId) {
        featureGuard.requireModule("payrollbureau");
        byte[] pdf = bureauService.generatePayslipPdf(TenantContext.getTenantIdAsObject(), payRunId, payslipId);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"payslip.pdf\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping("/pay-runs/{payRunId}/payslips/email")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    @Operation(summary = "Email every payslip in this run to employees who have an email on file")
    public ResponseEntity<ApiResponse<PayrollBureauService.PayslipDeliveryResult>> emailPayslips(@PathVariable UUID payRunId) {
        featureGuard.requireModule("payrollbureau");
        return ResponseEntity.ok(ApiResponse.success(
                bureauService.emailPayslips(TenantContext.getTenantIdAsObject(), payRunId)));
    }

    @PutMapping("/clients/{clientId}/employees/{employeeId}")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    public ResponseEntity<ApiResponse<PayEmployeeResponse>> updateEmployee(
            @PathVariable UUID clientId, @PathVariable UUID employeeId,
            @Valid @RequestBody UpdatePayEmployeeRequest req) {
        featureGuard.requireModule("payrollbureau");
        return ResponseEntity.ok(ApiResponse.success("Employee updated",
                bureauService.updateEmployee(TenantContext.getTenantIdAsObject(), clientId, employeeId, req)));
    }

    @PostMapping("/clients/{clientId}/deadlines/generate")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    @Operation(summary = "Generate EMP201/EMP501 deadlines for a client for a given year (idempotent)")
    public ResponseEntity<ApiResponse<List<PayDeadlineResponse>>> generateDeadlines(
            @PathVariable UUID clientId, @RequestParam int year) {
        featureGuard.requireModule("payrollbureau");
        return ResponseEntity.ok(ApiResponse.success("Deadlines generated",
                bureauService.generateDeadlines(TenantContext.getTenantIdAsObject(), clientId, year)));
    }

    @GetMapping("/clients/{clientId}/deadlines")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_READ','PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    public ResponseEntity<ApiResponse<List<PayDeadlineResponse>>> getDeadlines(@PathVariable UUID clientId) {
        featureGuard.requireModule("payrollbureau");
        return ResponseEntity.ok(ApiResponse.success(
                bureauService.getDeadlines(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @PostMapping("/deadlines/{deadlineId}/mark-filed")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    public ResponseEntity<ApiResponse<PayDeadlineResponse>> markDeadlineFiled(@PathVariable UUID deadlineId) {
        featureGuard.requireModule("payrollbureau");
        return ResponseEntity.ok(ApiResponse.success("Marked filed",
                bureauService.markDeadlineFiled(TenantContext.getTenantIdAsObject(), deadlineId)));
    }

    @PostMapping(value = "/employees/{employeeId}/documents", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    public ResponseEntity<ApiResponse<PayEmployeeDocumentResponse>> uploadDocument(
            @PathVariable UUID employeeId,
            @RequestParam String docType,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        featureGuard.requireModule("payrollbureau");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Document uploaded",
                bureauService.uploadDocument(TenantContext.getTenantIdAsObject(), employeeId, docType, file)));
    }

    @GetMapping("/employees/{employeeId}/documents")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_READ','PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    public ResponseEntity<ApiResponse<List<PayEmployeeDocumentResponse>>> getDocuments(@PathVariable UUID employeeId) {
        featureGuard.requireModule("payrollbureau");
        return ResponseEntity.ok(ApiResponse.success(
                bureauService.getDocuments(TenantContext.getTenantIdAsObject(), employeeId)));
    }

    @GetMapping("/documents/{documentId}/download")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_READ','PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable UUID documentId) {
        featureGuard.requireModule("payrollbureau");
        var doc = bureauService.downloadDocument(TenantContext.getTenantIdAsObject(), documentId);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + doc.fileName() + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType(
                        doc.contentType() != null ? doc.contentType() : "application/octet-stream"))
                .body(doc.content());
    }

    @PostMapping("/clients/{clientId}/fee-notes")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    @Operation(summary = "Generate an invoice from a processed pay run")
    public ResponseEntity<ApiResponse<PayFeeNoteResponse>> generateFeeNote(
            @PathVariable UUID clientId, @Valid @RequestBody CreatePayFeeNoteRequest req) {
        featureGuard.requireModule("payrollbureau");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Fee note generated",
                bureauService.generateFeeNote(TenantContext.getTenantIdAsObject(), clientId, req)));
    }

    @GetMapping("/clients/{clientId}/fee-notes")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_READ','PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    public ResponseEntity<ApiResponse<Page<PayFeeNoteResponse>>> getFeeNotes(
            @PathVariable UUID clientId, @PageableDefault(size = 24) Pageable pageable) {
        featureGuard.requireModule("payrollbureau");
        return ResponseEntity.ok(ApiResponse.success(
                bureauService.getFeeNotes(TenantContext.getTenantIdAsObject(), clientId, pageable)));
    }

    @PostMapping("/fee-notes/{id}/send")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    public ResponseEntity<ApiResponse<PayFeeNoteResponse>> sendFeeNote(@PathVariable UUID id) {
        featureGuard.requireModule("payrollbureau");
        return ResponseEntity.ok(ApiResponse.success("Fee note sent",
                bureauService.sendFeeNote(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/fee-notes/{id}/payments")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    public ResponseEntity<ApiResponse<PayFeeNoteResponse>> recordPayment(
            @PathVariable UUID id, @Valid @RequestBody RecordPayFeeNotePaymentRequest req) {
        featureGuard.requireModule("payrollbureau");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Payment recorded",
                bureauService.recordPayment(TenantContext.getTenantIdAsObject(), id, req,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/clients/{id}/portal-invites")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    @io.swagger.v3.oas.annotations.tags.Tag(name = "Payroll Bureau Client Portal")
    @Operation(summary = "Invite a client contact to the payroll bureau portal")
    public ResponseEntity<ApiResponse<PortalAccessGrantResponse>> invitePortalUser(
            @PathVariable UUID id, @Valid @RequestBody InvitePortalUserRequest req) {
        featureGuard.requireModule("payrollbureau");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Invite sent",
                bureauService.invitePortalUser(TenantContext.getTenantIdAsObject(), id,
                        req.email(), TenantContext.getCurrentUserId())));
    }

    @GetMapping("/clients/{id}/portal-invites")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_READ','PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    @io.swagger.v3.oas.annotations.tags.Tag(name = "Payroll Bureau Client Portal")
    @Operation(summary = "List portal access grants for a client")
    public ResponseEntity<ApiResponse<List<PortalAccessGrantResponse>>> getPortalAccessGrants(@PathVariable UUID id) {
        featureGuard.requireModule("payrollbureau");
        return ResponseEntity.ok(ApiResponse.success(
                bureauService.getPortalAccessGrants(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/clients/{clientId}/portal-invites/{grantId}/revoke")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    @io.swagger.v3.oas.annotations.tags.Tag(name = "Payroll Bureau Client Portal")
    @Operation(summary = "Revoke a client's portal access")
    public ResponseEntity<ApiResponse<PortalAccessGrantResponse>> revokePortalAccess(
            @PathVariable UUID clientId, @PathVariable UUID grantId) {
        featureGuard.requireModule("payrollbureau");
        return ResponseEntity.ok(ApiResponse.success("Portal access revoked",
                bureauService.revokePortalAccess(TenantContext.getTenantIdAsObject(), clientId, grantId,
                        TenantContext.getCurrentUserId())));
    }

    @PostMapping(value = "/clients/{clientId}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    public ResponseEntity<ApiResponse<PayClientResponse>> attachLogo(
            @PathVariable UUID clientId, @RequestParam("file") MultipartFile file) {
        featureGuard.requireModule("payrollbureau");
        return ResponseEntity.ok(ApiResponse.success("Logo uploaded",
                bureauService.attachLogo(TenantContext.getTenantIdAsObject(), clientId, file,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/clients/{clientId}/logo")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_READ','PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    public ResponseEntity<byte[]> downloadLogo(@PathVariable UUID clientId) {
        featureGuard.requireModule("payrollbureau");
        var logo = bureauService.downloadLogo(TenantContext.getTenantIdAsObject(), clientId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(logo.contentType())).body(logo.content());
    }

    @PostMapping(value = "/profile/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    public ResponseEntity<ApiResponse<BureauProfileResponse>> attachProfileLogo(@RequestParam("file") MultipartFile file) {
        featureGuard.requireModule("payrollbureau");
        return ResponseEntity.ok(ApiResponse.success("Logo uploaded",
                bureauService.attachProfileLogo(TenantContext.getTenantIdAsObject(), file,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/profile/logo")
    @PreAuthorize("hasAnyAuthority('PAYROLLBUREAU_READ','PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')")
    public ResponseEntity<byte[]> downloadProfileLogo() {
        featureGuard.requireModule("payrollbureau");
        var logo = bureauService.downloadProfileLogo(TenantContext.getTenantIdAsObject());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(logo.contentType())).body(logo.content());
    }

}