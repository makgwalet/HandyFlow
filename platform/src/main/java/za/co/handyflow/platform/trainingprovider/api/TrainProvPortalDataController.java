package za.co.handyflow.platform.trainingprovider.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvPortalDataService;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvCertificate;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvDelegate;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvEnrollment;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvInvoice;
import za.co.handyflow.platform.trainingprovider.dto.CertificateResponse;
import za.co.handyflow.platform.trainingprovider.dto.DelegateResponse;
import za.co.handyflow.platform.trainingprovider.dto.EnrollmentResponse;
import za.co.handyflow.platform.trainingprovider.dto.InvoiceResponse;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

/**
 * Client-facing reads, gated by the portal JWT (not the staff
 * TRAININGPROVIDER_* authorities) — same {@code PORTAL_USER} authority
 * convention AccountantPortalAuthController's own
 * acceptAdditionalInvite() endpoint uses, and the same
 * "PortalJwtFilter stores the portal user's ID as the Authentication
 * principal" mechanism confirmed there.
 */
@RestController
@RequestMapping("/api/v1/training-provider/portal/me")
@RequiredArgsConstructor
@Tag(name = "Training Provider Client Portal - Data", description = "What a logged-in client contact can see about their own organization")
public class TrainProvPortalDataController {

    private final TrainProvPortalDataService portalDataService;

    @GetMapping("/delegates")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "This client's own delegates")
    public ResponseEntity<ApiResponse<List<DelegateResponse>>> getMyDelegates() {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        List<DelegateResponse> delegates = portalDataService.getMyDelegates(tenantId, getPortalUserId()).stream()
                .map(this::toResponse).toList();
        return ResponseEntity.ok(ApiResponse.success(delegates));
    }

    @GetMapping("/enrollments")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    public ResponseEntity<ApiResponse<Page<EnrollmentResponse>>> getMyEnrollments(
            @RequestParam(required = false) String status, @PageableDefault(size = 50) Pageable pageable) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                portalDataService.getMyEnrollments(tenantId, getPortalUserId(), status, pageable).map(this::toResponse)));
    }

    @GetMapping("/certificates")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    public ResponseEntity<ApiResponse<Page<CertificateResponse>>> getMyCertificates(
            @RequestParam(required = false) String status, @PageableDefault(size = 50) Pageable pageable) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                portalDataService.getMyCertificates(tenantId, getPortalUserId(), status, pageable).map(this::toResponse)));
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    public ResponseEntity<ApiResponse<List<InvoiceResponse>>> getMyInvoices() {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        List<InvoiceResponse> invoices = portalDataService.getMyInvoices(tenantId, getPortalUserId()).stream()
                .map(this::toResponse).toList();
        return ResponseEntity.ok(ApiResponse.success(invoices));
    }

    /** PortalJwtFilter stores the portal user's ID (UUID string) as the Authentication principal — confirmed against AccountantPortalAuthController's own identical helper. */
    private UUID getPortalUserId() {
        try {
            return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
        } catch (Exception e) {
            throw new HandyFlowException("Invalid portal session", HttpStatus.UNAUTHORIZED, "INVALID_PORTAL_SESSION");
        }
    }

    private DelegateResponse toResponse(TrainProvDelegate d) {
        return new DelegateResponse(d.getId(), d.getClientId(), d.getDelegateNumber(), d.getFullName(), d.getIdNumber(),
                d.getEmail(), d.getPhone(), d.getJobTitle(), d.getStatus(), d.getCreatedAt());
    }

    private EnrollmentResponse toResponse(TrainProvEnrollment e) {
        return new EnrollmentResponse(e.getId(), e.getSessionId(), e.getDelegateId(), e.getClientId(),
                e.getDelegateNameSnapshot(), e.getStatus(), e.getEnrolledAt(), e.getCompletedAt(), e.getScore(),
                e.getPassed(), e.getNotes(), e.getCancelReason(), e.isInvoiced());
    }

    private CertificateResponse toResponse(TrainProvCertificate c) {
        return new CertificateResponse(c.getId(), c.getEnrollmentId(), c.getDelegateId(), c.getClientId(),
                c.getDelegateNameSnapshot(), c.getClientNameSnapshot(), c.getCourseTitleSnapshot(),
                c.getUnitStandardSnapshot(), c.getCertificateNumber(), c.getIssueDate(), c.getExpiryDate(),
                c.getStatus(), c.getRevokedReason(), c.getCreatedAt());
    }

    private InvoiceResponse toResponse(TrainProvInvoice i) {
        return new InvoiceResponse(i.getId(), i.getClientId(), i.getInvoiceNumber(), i.getPeriodStart(), i.getPeriodEnd(),
                i.getIssueDate(), i.getDueDate(), i.getDelegateCount(), i.getSubtotal(), i.getVatAmount(), i.getTotal(),
                i.getAmountPaid(), i.balance(), i.getStatus(), i.getCreatedAt());
    }
}
