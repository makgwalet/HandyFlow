package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilities.domain.model.FacilityVendor;
import za.co.handyflow.platform.facilities.domain.repository.FacilityVendorRepository;
import za.co.handyflow.platform.facilities.dto.UpsertVendorRequest;
import za.co.handyflow.platform.facilities.dto.VendorResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FacilityVendorService {

    private final FacilityVendorRepository vendorRepository;

    @Transactional(readOnly = true)
    public Page<VendorResponse> getVendors(TenantId tenantId, Pageable pageable) {
        return vendorRepository.findAllActive(tenantId, pageable).map(this::toResponse);
    }

    @Transactional
    public VendorResponse createVendor(TenantId tenantId, UpsertVendorRequest req) {
        FacilityVendor v = FacilityVendor.create(tenantId, req.companyName(), req.serviceType(),
                req.contactName(), req.contactPhone(), req.contactEmail(), req.notes());
        vendorRepository.save(v);
        return toResponse(v);
    }

    @Transactional
    public VendorResponse updateVendor(TenantId tenantId, UUID id, UpsertVendorRequest req) {
        FacilityVendor v = findActive(tenantId, id);
        v.update(req.companyName(), req.serviceType(), req.contactName(), req.contactPhone(),
                req.contactEmail(), req.notes());
        vendorRepository.save(v);
        return toResponse(v);
    }

    @Transactional
    public VendorResponse deactivate(TenantId tenantId, UUID id) {
        FacilityVendor v = findActive(tenantId, id);
        v.deactivate();
        vendorRepository.save(v);
        return toResponse(v);
    }

    @Transactional
    public VendorResponse reactivate(TenantId tenantId, UUID id) {
        FacilityVendor v = findActive(tenantId, id);
        v.reactivate();
        vendorRepository.save(v);
        return toResponse(v);
    }

    @Transactional
    public void deleteVendor(TenantId tenantId, UUID id) {
        FacilityVendor v = findActive(tenantId, id);
        v.softDelete();
        vendorRepository.save(v);
    }

    private FacilityVendor findActive(TenantId tenantId, UUID id) {
        return vendorRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FacilityVendor", id.toString()));
    }

    private VendorResponse toResponse(FacilityVendor v) {
        return new VendorResponse(v.getId(), v.getCompanyName(), v.getServiceType(), v.getContactName(),
                v.getContactPhone(), v.getContactEmail(), v.getNotes(), v.isActive(), v.getCreatedAt());
    }
}
