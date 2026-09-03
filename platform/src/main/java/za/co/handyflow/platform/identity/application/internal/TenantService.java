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
        // FIX: blank now means "clear the logo" and is stored as a real
        // null, matching SettingsPage.tsx's "Remove logo" button (see
        // UploadLogoRequest's own Javadoc). Previously this branch
        // wrapped even an empty string into "data:image/png;base64,"
        // — a non-null, non-empty-looking value with no actual image
        // data — which meant Remove never actually cleared logoUrl and
        // would have left the frontend trying to render a broken image.
        if (logoData == null || logoData.isBlank()) {
            logoData = null;
        } else if (!logoData.startsWith("data:")) {
            String mime = req.mimeType() != null ? req.mimeType() : "image/png";
            logoData = "data:" + mime + ";base64," + logoData;
        }
        tenant.updateLogo(logoData);
        tenantRepository.save(tenant);
        log.info("Updated logo for tenant={}", tenantId);
        return toDetails(tenant);
    }

    // FIX (identity module modernization): the write here was always
    // complete and correct — the gap was entirely on the read side.
    // TenantDetails now carries billingEmail/billingContactName/
    // billingPhone (see that record's own comment), so toDetails() below
    // can finally confirm what was actually saved back to the caller,
    // and the Settings UI has something real to build a billing-contact
    // form against.
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
                t.getPaymentTerms(),
                t.getBillingEmail(), t.getBillingContactName(), t.getBillingPhone()
        );
    }
}