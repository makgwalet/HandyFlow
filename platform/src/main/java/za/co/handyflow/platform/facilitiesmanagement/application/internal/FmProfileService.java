package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmProfile;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmProfileRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmProfileResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.UpsertFmProfileRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

/**
 * One profile row per tenant — created on first save, updated thereafter. Chunk 7: also carries the
 * billing settings (VAT, terms, ledger account codes). Any change to those settings is audited, because
 * they decide how every future invoice is calculated and posted.
 */
@Service
@RequiredArgsConstructor
public class FmProfileService {

    private final FmProfileRepository profileRepository;
    private final FmAuditService auditService;

    @Transactional(readOnly = true)
    public FmProfileResponse getProfile(TenantId tenantId) {
        return profileRepository.findByTenant(tenantId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("FmProfile", tenantId.getValue().toString()));
    }

    @Transactional
    public FmProfileResponse upsertProfile(TenantId tenantId, UpsertFmProfileRequest req) {
        FmProfile profile = profileRepository.findByTenant(tenantId).orElse(null);
        String before = profile == null ? "none" : billingSummary(profile);
        if (profile == null) {
            profile = FmProfile.create(tenantId, req.companyName(), req.registrationNumber(),
                    req.contactEmail(), req.contactPhone(), req.notes());
        } else {
            profile.update(req.companyName(), req.registrationNumber(), req.contactEmail(),
                    req.contactPhone(), req.notes());
        }
        profile.changeAddress(req.address());
        profile.updateBillingSettings(req.vatRegistered(), req.vatNumber(), req.vatRate(), req.paymentTermsDays(),
                req.arAccountCode(), req.revenueAccountCode(), req.vatAccountCode(), req.bankAccountCode());
        profileRepository.save(profile);

        String after = billingSummary(profile);
        if (!after.equals(before)) {
            auditService.record(tenantId, "BILLING_SETTINGS_CHANGED", "PROFILE", profile.getId(), before + " -> " + after);
        }
        return toResponse(profile);
    }

    private String billingSummary(FmProfile p) {
        return "vatRegistered=" + p.isVatRegistered() + " vatNumber=" + p.getVatNumber() + " vatRate=" + p.getVatRate()
                + " terms=" + p.getPaymentTermsDays() + " ar=" + p.getArAccountCode() + " revenue=" + p.getRevenueAccountCode()
                + " vatAcct=" + p.getVatAccountCode() + " bankAcct=" + p.getBankAccountCode()
                + " address=" + p.getAddress();
    }

    private FmProfileResponse toResponse(FmProfile p) {
        return new FmProfileResponse(p.getId(), p.getCompanyName(), p.getRegistrationNumber(),
                p.getContactEmail(), p.getContactPhone(), p.getNotes(), p.getCreatedAt(),
                p.isVatRegistered(), p.getVatNumber(), p.getVatRate(), p.getPaymentTermsDays(),
                p.getArAccountCode(), p.getRevenueAccountCode(), p.getVatAccountCode(), p.getBankAccountCode(), p.getAddress());
    }
}