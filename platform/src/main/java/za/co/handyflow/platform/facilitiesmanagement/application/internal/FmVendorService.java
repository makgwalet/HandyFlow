package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmVendor;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmVendorRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmVendorResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.UpsertFmVendorRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FmVendorService {

    private final FmVendorRepository vendorRepository;

    @Transactional(readOnly = true)
    public Page<FmVendorResponse> getVendors(TenantId tenantId, Pageable pageable) {
        return vendorRepository.findAllActive(tenantId, pageable).map(this::toResponse);
    }

    @Transactional
    public FmVendorResponse createVendor(TenantId tenantId, UpsertFmVendorRequest req) {
        FmVendor v = FmVendor.create(tenantId, req.companyName(), req.serviceType(), req.contactName(),
                req.contactPhone(), req.contactEmail(), req.notes());
        vendorRepository.save(v);
        return toResponse(v);
    }

    @Transactional
    public FmVendorResponse updateVendor(TenantId tenantId, UUID id, UpsertFmVendorRequest req) {
        FmVendor v = findActive(tenantId, id);
        v.update(req.companyName(), req.serviceType(), req.contactName(), req.contactPhone(), req.contactEmail(), req.notes());
        vendorRepository.save(v);
        return toResponse(v);
    }

    @Transactional
    public FmVendorResponse deactivate(TenantId tenantId, UUID id) {
        FmVendor v = findActive(tenantId, id);
        v.deactivate();
        vendorRepository.save(v);
        return toResponse(v);
    }

    @Transactional
    public FmVendorResponse reactivate(TenantId tenantId, UUID id) {
        FmVendor v = findActive(tenantId, id);
        v.reactivate();
        vendorRepository.save(v);
        return toResponse(v);
    }

    @Transactional
    public void deleteVendor(TenantId tenantId, UUID id) {
        FmVendor v = findActive(tenantId, id);
        v.softDelete();
        vendorRepository.save(v);
    }

    FmVendor findActive(TenantId tenantId, UUID id) {
        return vendorRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FmVendor", id.toString()));
    }

    private FmVendorResponse toResponse(FmVendor v) {
        return new FmVendorResponse(v.getId(), v.getCompanyName(), v.getServiceType(), v.getContactName(),
                v.getContactPhone(), v.getContactEmail(), v.getNotes(), v.isActive(), v.getCreatedAt());
    }
}
