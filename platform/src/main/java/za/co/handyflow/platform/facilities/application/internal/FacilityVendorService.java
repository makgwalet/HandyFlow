package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilities.domain.model.FacilityVendor;
import za.co.handyflow.platform.facilities.domain.model.FacilityVendorDocument;
import za.co.handyflow.platform.facilities.domain.repository.FacilityVendorDocumentRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityVendorRepository;
import za.co.handyflow.platform.facilities.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Vendors and their onboarding. A new vendor is PENDING until someone other than its creator approves it (an
 * admin may approve their own, which is audited); approval needs every required compliance document in force
 * (see {@link FacilityVendorCompliance}). Every change is audited.
 */
@Service
@RequiredArgsConstructor
public class FacilityVendorService {

    private final FacilityVendorRepository vendorRepository;
    private final FacilityVendorDocumentRepository documentRepository;
    private final FacilityAccessPolicy accessPolicy;
    private final FacilityAuditService auditService;

    @Transactional(readOnly = true)
    public Page<VendorResponse> getVendors(TenantId tenantId, Pageable pageable) {
        return vendorRepository.findAllActive(tenantId, pageable).map(this::toResponse);
    }

    @Transactional
    public VendorResponse createVendor(TenantId tenantId, UpsertVendorRequest req) {
        FacilityVendor v = FacilityVendor.create(tenantId, req.companyName(), req.serviceType(),
                req.contactName(), req.contactPhone(), req.contactEmail(), req.notes());
        v.recordCreator(TenantContext.getCurrentUserId());
        vendorRepository.save(v);
        auditService.record(tenantId, "VENDOR_CREATED", "VENDOR", v.getId(), "name=" + v.getCompanyName() + " type=" + v.getServiceType());
        return toResponse(v);
    }

    @Transactional
    public VendorResponse updateVendor(TenantId tenantId, UUID id, UpsertVendorRequest req) {
        FacilityVendor v = findActive(tenantId, id);
        v.update(req.companyName(), req.serviceType(), req.contactName(), req.contactPhone(),
                req.contactEmail(), req.notes());
        vendorRepository.save(v);
        auditService.record(tenantId, "VENDOR_UPDATED", "VENDOR", id, "name=" + v.getCompanyName() + " type=" + v.getServiceType());
        return toResponse(v);
    }

    @Transactional
    public VendorResponse deactivate(TenantId tenantId, UUID id) {
        FacilityVendor v = findActive(tenantId, id);
        v.deactivate();
        vendorRepository.save(v);
        auditService.record(tenantId, "VENDOR_DEACTIVATED", "VENDOR", id, "name=" + v.getCompanyName());
        return toResponse(v);
    }

    @Transactional
    public VendorResponse reactivate(TenantId tenantId, UUID id) {
        FacilityVendor v = findActive(tenantId, id);
        v.reactivate();
        vendorRepository.save(v);
        auditService.record(tenantId, "VENDOR_REACTIVATED", "VENDOR", id, "name=" + v.getCompanyName());
        return toResponse(v);
    }

    @Transactional
    public void deleteVendor(TenantId tenantId, UUID id) {
        FacilityVendor v = findActive(tenantId, id);
        v.softDelete();
        vendorRepository.save(v);
        auditService.record(tenantId, "VENDOR_DELETED", "VENDOR", id, "name=" + v.getCompanyName());
    }

    // ── Onboarding ───────────────────────────────────────────────────────────

    @Transactional
    public VendorResponse approve(TenantId tenantId, UUID id) {
        FacilityVendor v = findActive(tenantId, id);
        UUID approver = TenantContext.getCurrentUserId();
        boolean adminOverride = false;
        if (approver != null && approver.equals(v.getCreatedByUserId())) {
            if (!accessPolicy.hasAuthority("FACILITIES_ADMIN")) {
                throw new HandyFlowException("You added this vendor, so someone else must approve it",
                        HttpStatus.FORBIDDEN, "VENDOR_SELF_APPROVAL");
            }
            adminOverride = true;
        }
        FacilityVendorCompliance.Result compliance = compliance(tenantId, v);
        if (compliance.isNonCompliant()) {
            throw new HandyFlowException("Cannot approve " + v.getCompanyName() + ": " + String.join("; ", compliance.problems()),
                    HttpStatus.CONFLICT, "VENDOR_NOT_COMPLIANT");
        }
        v.approve(approver);
        vendorRepository.save(v);
        auditService.record(tenantId, adminOverride ? "VENDOR_SELF_APPROVED" : "VENDOR_APPROVED", "VENDOR", id,
                "name=" + v.getCompanyName() + " compliance=" + compliance.status());
        return toResponse(v);
    }

    @Transactional
    public VendorResponse suspend(TenantId tenantId, UUID id, String reason) {
        FacilityVendor v = findActive(tenantId, id);
        v.suspend(reason);
        vendorRepository.save(v);
        auditService.record(tenantId, "VENDOR_SUSPENDED", "VENDOR", id, "name=" + v.getCompanyName() + " reason=" + v.getSuspendedReason());
        return toResponse(v);
    }

    @Transactional(readOnly = true)
    public VendorComplianceResponse getCompliance(TenantId tenantId, UUID id) {
        FacilityVendor v = findActive(tenantId, id);
        LocalDate today = FacilityTime.today();
        List<FacilityVendorDocument> docs = documentRepository.findForVendor(tenantId, id);
        FacilityVendorCompliance.Result c = FacilityVendorCompliance.evaluate(docs, today);
        List<String> problems = new java.util.ArrayList<>();
        if (!v.isApproved()) problems.add("vendor is " + v.getOnboardingStatus());
        problems.addAll(c.problems());
        return new VendorComplianceResponse(id, v.getOnboardingStatus(), c.status(), problems.isEmpty(), problems,
                c.missing(), c.lapsed(),
                c.expiring().stream().map(e -> new VendorComplianceResponse.Expiring(e.docType(), e.expiryDate(), e.daysLeft())).toList(),
                docs.stream().map(d -> toResponse(d, today)).toList());
    }

    // ── Documents ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<VendorDocumentResponse> getDocuments(TenantId tenantId, UUID vendorId) {
        findActive(tenantId, vendorId);
        LocalDate today = FacilityTime.today();
        return documentRepository.findForVendor(tenantId, vendorId).stream().map(d -> toResponse(d, today)).toList();
    }

    @Transactional
    public VendorDocumentResponse addDocument(TenantId tenantId, UUID vendorId, VendorDocumentRequest req) {
        FacilityVendor v = findActive(tenantId, vendorId);
        FacilityVendorDocument d = FacilityVendorDocument.create(tenantId, vendorId, req.docType(), req.reference(), req.issuedBy(),
                req.issueDate(), req.expiryDate(), req.coverAmount(), req.documentRef());
        documentRepository.save(d);
        auditService.record(tenantId, "VENDOR_DOCUMENT_ADDED", "VENDOR", vendorId,
                "vendor=" + v.getCompanyName() + " type=" + d.getDocType() + " ref=" + d.getReference() + " expires=" + d.getExpiryDate());
        return toResponse(d, FacilityTime.today());
    }

    @Transactional
    public VendorDocumentResponse revokeDocument(TenantId tenantId, UUID vendorId, UUID documentId, String reason) {
        findActive(tenantId, vendorId);
        FacilityVendorDocument d = documentRepository.findByIdForTenant(tenantId, documentId)
                .filter(x -> x.getVendorId().equals(vendorId))
                .orElseThrow(() -> new ResourceNotFoundException("FacilityVendorDocument", documentId.toString()));
        d.revoke(reason);
        documentRepository.save(d);
        auditService.record(tenantId, "VENDOR_DOCUMENT_REVOKED", "VENDOR", vendorId,
                "type=" + d.getDocType() + " ref=" + d.getReference() + " reason=" + d.getRevokedReason());
        return toResponse(d, FacilityTime.today());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private FacilityVendorCompliance.Result compliance(TenantId tenantId, FacilityVendor v) {
        return FacilityVendorCompliance.evaluate(documentRepository.findForVendor(tenantId, v.getId()), FacilityTime.today());
    }

    private FacilityVendor findActive(TenantId tenantId, UUID id) {
        return vendorRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FacilityVendor", id.toString()));
    }

    private VendorResponse toResponse(FacilityVendor v) {
        return new VendorResponse(v.getId(), v.getCompanyName(), v.getServiceType(), v.getContactName(),
                v.getContactPhone(), v.getContactEmail(), v.getNotes(), v.isActive(), v.getCreatedAt(), v.getOnboardingStatus());
    }

    private VendorDocumentResponse toResponse(FacilityVendorDocument d, LocalDate today) {
        return new VendorDocumentResponse(d.getId(), d.getVendorId(), d.getDocType(), d.getReference(), d.getIssuedBy(),
                d.getIssueDate(), d.getExpiryDate(), d.getCoverAmount(), d.getDocumentRef(), d.getStatus(), d.isInForce(today),
                d.getRevokedReason(), d.getCreatedAt());
    }
}
