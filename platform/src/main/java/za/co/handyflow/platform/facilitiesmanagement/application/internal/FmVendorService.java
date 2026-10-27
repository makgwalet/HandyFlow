package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmVendor;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmVendorDocument;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmVendorDocumentRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmVendorRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmWorkOrderRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Subcontractor records. They are company-wide, not owned by a client, so a caller restricted to certain clients
 * sees only the directory (name and service type, no contact details or notes) and cannot change anything.
 * Every change is audited, and a vendor with unfinished work cannot be switched off or deleted.
 * A new vendor is PENDING until someone other than its creator approves it (an admin may approve their own, audited);
 * approval needs every required compliance document in force (see {@link FmVendorCompliance}).
 */
@Service
@RequiredArgsConstructor
public class FmVendorService {

    private final FmVendorRepository vendorRepository;
    private final FmWorkOrderRepository workOrderRepository;
    private final FmAccessPolicy accessPolicy;
    private final FmAuditService auditService;
    private final FmVendorDocumentRepository documentRepository;

    @Transactional(readOnly = true)
    public Page<FmVendorResponse> getVendors(TenantId tenantId, Pageable pageable) {
        boolean full = accessPolicy.scope(tenantId).unrestricted();
        return vendorRepository.findAllActive(tenantId, pageable).map(v -> toResponse(v, full));
    }

    @Transactional
    public FmVendorResponse createVendor(TenantId tenantId, UpsertFmVendorRequest req) {
        requireCompanyWide(tenantId);
        FmVendor v = FmVendor.create(tenantId, req.companyName(), req.serviceType(), req.contactName(),
                req.contactPhone(), req.contactEmail(), req.notes());
        v.recordCreator(TenantContext.getCurrentUserId());
        vendorRepository.save(v);
        auditService.record(tenantId, "VENDOR_CREATED", "VENDOR", v.getId(), v.getCompanyName() + " (" + v.getServiceType() + ")");
        return toResponse(v, true);
    }

    @Transactional
    public FmVendorResponse updateVendor(TenantId tenantId, UUID id, UpsertFmVendorRequest req) {
        requireCompanyWide(tenantId);
        FmVendor v = findActive(tenantId, id);
        String before = v.getCompanyName() + " / " + v.getServiceType();
        v.update(req.companyName(), req.serviceType(), req.contactName(), req.contactPhone(), req.contactEmail(), req.notes());
        vendorRepository.save(v);
        // Details only: contact data is personal information and stays out of the audit text.
        auditService.record(tenantId, "VENDOR_UPDATED", "VENDOR", id, before + " -> " + v.getCompanyName() + " / " + v.getServiceType());
        return toResponse(v, true);
    }

    @Transactional
    public FmVendorResponse deactivate(TenantId tenantId, UUID id) {
        requireCompanyWide(tenantId);
        FmVendor v = findActive(tenantId, id);
        requireNoOpenWork(tenantId, v);
        v.deactivate();
        vendorRepository.save(v);
        auditService.record(tenantId, "VENDOR_DEACTIVATED", "VENDOR", id, v.getCompanyName());
        return toResponse(v, true);
    }

    @Transactional
    public FmVendorResponse reactivate(TenantId tenantId, UUID id) {
        requireCompanyWide(tenantId);
        FmVendor v = findActive(tenantId, id);
        v.reactivate();
        vendorRepository.save(v);
        auditService.record(tenantId, "VENDOR_REACTIVATED", "VENDOR", id, v.getCompanyName());
        return toResponse(v, true);
    }

    @Transactional
    public void deleteVendor(TenantId tenantId, UUID id) {
        requireCompanyWide(tenantId);
        FmVendor v = findActive(tenantId, id);
        requireNoOpenWork(tenantId, v);
        v.softDelete();
        vendorRepository.save(v);
        auditService.record(tenantId, "VENDOR_DELETED", "VENDOR", id, v.getCompanyName());
    }

    // ── Onboarding ───────────────────────────────────────────────────────────

    @Transactional
    public FmVendorResponse approve(TenantId tenantId, UUID id) {
        requireCompanyWide(tenantId);
        FmVendor v = findActive(tenantId, id);
        UUID approver = TenantContext.getCurrentUserId();
        boolean adminOverride = false;
        if (approver != null && approver.equals(v.getCreatedByUserId())) {
            if (!accessPolicy.hasAuthority("FACILITIESMANAGEMENT_ADMIN")) {
                throw new HandyFlowException("You added this vendor, so someone else must approve it",
                        HttpStatus.FORBIDDEN, "VENDOR_SELF_APPROVAL");
            }
            adminOverride = true;
        }
        FmVendorCompliance.Result compliance = FmVendorCompliance.evaluate(documentRepository.findForVendor(tenantId, id), FmTime.today());
        if (compliance.isNonCompliant()) {
            throw new HandyFlowException("Cannot approve " + v.getCompanyName() + ": " + String.join("; ", compliance.problems()),
                    HttpStatus.CONFLICT, "VENDOR_NOT_COMPLIANT");
        }
        v.approve(approver);
        vendorRepository.save(v);
        auditService.record(tenantId, adminOverride ? "VENDOR_SELF_APPROVED" : "VENDOR_APPROVED", "VENDOR", id,
                v.getCompanyName() + " compliance=" + compliance.status());
        return toResponse(v, true);
    }

    @Transactional
    public FmVendorResponse suspend(TenantId tenantId, UUID id, String reason) {
        requireCompanyWide(tenantId);
        FmVendor v = findActive(tenantId, id);
        v.suspend(reason);
        vendorRepository.save(v);
        auditService.record(tenantId, "VENDOR_SUSPENDED", "VENDOR", id, v.getCompanyName() + " reason=" + v.getSuspendedReason());
        return toResponse(v, true);
    }

    @Transactional(readOnly = true)
    public FmVendorComplianceResponse getCompliance(TenantId tenantId, UUID id) {
        requireCompanyWide(tenantId);
        FmVendor v = findActive(tenantId, id);
        LocalDate today = FmTime.today();
        List<FmVendorDocument> docs = documentRepository.findForVendor(tenantId, id);
        FmVendorCompliance.Result c = FmVendorCompliance.evaluate(docs, today);
        List<String> problems = new ArrayList<>();
        if (!v.isApproved()) problems.add("vendor is " + v.getOnboardingStatus());
        problems.addAll(c.problems());
        return new FmVendorComplianceResponse(id, v.getOnboardingStatus(), c.status(), problems.isEmpty(), problems,
                c.missing(), c.lapsed(),
                c.expiring().stream().map(e -> new FmVendorComplianceResponse.Expiring(e.docType(), e.expiryDate(), e.daysLeft())).toList(),
                docs.stream().map(d -> toResponse(d, today)).toList());
    }

    // ── Documents ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FmVendorDocumentResponse> getDocuments(TenantId tenantId, UUID vendorId) {
        requireCompanyWide(tenantId);
        findActive(tenantId, vendorId);
        LocalDate today = FmTime.today();
        return documentRepository.findForVendor(tenantId, vendorId).stream().map(d -> toResponse(d, today)).toList();
    }

    @Transactional
    public FmVendorDocumentResponse addDocument(TenantId tenantId, UUID vendorId, FmVendorDocumentRequest req) {
        requireCompanyWide(tenantId);
        FmVendor v = findActive(tenantId, vendorId);
        FmVendorDocument d = FmVendorDocument.create(tenantId, vendorId, req.docType(), req.reference(), req.issuedBy(),
                req.issueDate(), req.expiryDate(), req.coverAmount(), req.documentRef());
        documentRepository.save(d);
        auditService.record(tenantId, "VENDOR_DOCUMENT_ADDED", "VENDOR", vendorId,
                "vendor=" + v.getCompanyName() + " type=" + d.getDocType() + " ref=" + d.getReference() + " expires=" + d.getExpiryDate());
        return toResponse(d, FmTime.today());
    }

    @Transactional
    public FmVendorDocumentResponse revokeDocument(TenantId tenantId, UUID vendorId, UUID documentId, String reason) {
        requireCompanyWide(tenantId);
        findActive(tenantId, vendorId);
        FmVendorDocument d = documentRepository.findByIdForTenant(tenantId, documentId)
                .filter(x -> x.getVendorId().equals(vendorId))
                .orElseThrow(() -> new ResourceNotFoundException("FmVendorDocument", documentId.toString()));
        d.revoke(reason);
        documentRepository.save(d);
        auditService.record(tenantId, "VENDOR_DOCUMENT_REVOKED", "VENDOR", vendorId,
                "type=" + d.getDocType() + " ref=" + d.getReference() + " reason=" + d.getRevokedReason());
        return toResponse(d, FmTime.today());
    }

    private FmVendorDocumentResponse toResponse(FmVendorDocument d, LocalDate today) {
        return new FmVendorDocumentResponse(d.getId(), d.getVendorId(), d.getDocType(), d.getReference(), d.getIssuedBy(),
                d.getIssueDate(), d.getExpiryDate(), d.getCoverAmount(), d.getDocumentRef(), d.getStatus(), d.isInForce(today),
                d.getRevokedReason(), d.getCreatedAt());
    }

    FmVendor findActive(TenantId tenantId, UUID id) {
        return vendorRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FmVendor", id.toString()));
    }

    private void requireCompanyWide(TenantId tenantId) {
        if (!accessPolicy.scope(tenantId).unrestricted())
            throw new HandyFlowException("Your access is limited to certain clients, so you cannot change the subcontractor list",
                    HttpStatus.FORBIDDEN, "SCOPE_RESTRICTED");
    }

    private void requireNoOpenWork(TenantId tenantId, FmVendor v) {
        long open = workOrderRepository.countOpenForVendor(tenantId, v.getId());
        if (open > 0)
            throw new HandyFlowException(v.getCompanyName() + " still has " + open + " unfinished work order" + (open == 1 ? "" : "s")
                    + "; reassign or finish them first", HttpStatus.CONFLICT, "VENDOR_HAS_OPEN_WORK");
    }

    private FmVendorResponse toResponse(FmVendor v, boolean full) {
        return new FmVendorResponse(v.getId(), v.getCompanyName(), v.getServiceType(),
                full ? v.getContactName() : null, full ? v.getContactPhone() : null, full ? v.getContactEmail() : null,
                full ? v.getNotes() : null, v.isActive(), v.getCreatedAt(), v.getOnboardingStatus());
    }
}
