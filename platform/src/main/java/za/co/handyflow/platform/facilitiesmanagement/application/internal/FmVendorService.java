package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmVendor;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmVendorRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmWorkOrderRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmVendorResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.UpsertFmVendorRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Subcontractor records. They are company-wide, not owned by a client, so a caller restricted to certain clients
 * sees only the directory (name and service type, no contact details or notes) and cannot change anything.
 * Every change is audited, and a vendor with unfinished work cannot be switched off or deleted.
 */
@Service
@RequiredArgsConstructor
public class FmVendorService {

    private final FmVendorRepository vendorRepository;
    private final FmWorkOrderRepository workOrderRepository;
    private final FmAccessPolicy accessPolicy;
    private final FmAuditService auditService;

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
                full ? v.getNotes() : null, v.isActive(), v.getCreatedAt());
    }
}
