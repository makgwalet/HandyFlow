package za.co.handyflow.platform.identity.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.identity.TenantDetails;
import za.co.handyflow.platform.identity.domain.model.Tenant;
import za.co.handyflow.platform.identity.domain.repository.TenantRepository;
import za.co.handyflow.platform.identity.dto.UpdateTenantProfileRequest;
import za.co.handyflow.platform.identity.dto.UpdateBillingContactRequest;
import za.co.handyflow.platform.identity.dto.UploadLogoRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public TenantDetails getTenantDetails(TenantId tenantId) {
        return tenantRepository.findById(tenantId.getValue())
                .map(this::toDetails)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId.getValue().toString()));
    }

    @Transactional
    public TenantDetails updateProfile(TenantId tenantId, UpdateTenantProfileRequest req) {
        Tenant tenant = tenantRepository.findById(tenantId.getValue())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId.getValue().toString()));
        tenant.updateProfile(req.name(), req.phone(), req.vatNumber(),
                req.address(), req.bankName(), req.bankAccount(),
                req.bankBranch(), req.paymentTerms());
        tenantRepository.save(tenant);
        log.info("Updated profile for tenant={}", tenantId);
        return toDetails(tenant);
    }

    @Transactional
    public TenantDetails uploadLogo(TenantId tenantId, UploadLogoRequest req) {
        Tenant tenant = tenantRepository.findById(tenantId.getValue())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId.getValue().toString()));
        String logoData = req.logoBase64();
        if (logoData != null && !logoData.startsWith("data:")) {
            String mime = req.mimeType() != null ? req.mimeType() : "image/png";
            logoData = "data:" + mime + ";base64," + logoData;
        }
        tenant.updateLogo(logoData);
        tenantRepository.save(tenant);
        log.info("Updated logo for tenant={}", tenantId);
        return toDetails(tenant);
    }

    // NEW: persists correctly, but toDetails()/TenantDetails below don't
    // yet surface billingEmail/billingContactName/billingPhone back in
    // the response — TenantDetails is a record I don't have the source
    // for, and extending its shape without seeing every caller risks the
    // same class of breakage flagged elsewhere in this codebase for
    // AuthResponse. The write is complete and correct; confirming it
    // back to the frontend needs that file.
    @Transactional
    public TenantDetails updateBillingContact(TenantId tenantId, UpdateBillingContactRequest req) {
        Tenant tenant = tenantRepository.findById(tenantId.getValue())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId.getValue().toString()));
        tenant.updateBillingContact(req.billingEmail(), req.billingContactName(), req.billingPhone());
        tenantRepository.save(tenant);
        log.info("Updated billing contact for tenant={}", tenantId);
        return toDetails(tenant);
    }

    // WHY duplicate toDetails here and in TenantFacadeImpl?
    // TenantFacadeImpl is package-private and crosses module boundaries.
    // TenantService is the internal write side — keeping them separate
    // avoids coupling the read facade to the write service.
    private TenantDetails toDetails(Tenant t) {
        return new TenantDetails(
                t.getId(), t.getName(), t.getSlug(), t.getVatNumber(),
                t.getPhone(), t.getEmail(), t.getAddress(), t.getLogoUrl(),
                t.getBankName(), t.getBankAccount(), t.getBankBranch(),
                t.getPaymentTerms()
        );
    }
}