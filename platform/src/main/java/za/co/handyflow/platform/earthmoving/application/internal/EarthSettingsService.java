package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.earthmoving.domain.model.EarthSettings;
import za.co.handyflow.platform.earthmoving.domain.model.EarthmovingRules;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthSettingsRepository;
import za.co.handyflow.platform.earthmoving.dto.SettingsResponse;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** Company settings for Earthmoving. Anyone who can read the module sees them; only an administrator changes them. */
@Service
@RequiredArgsConstructor
public class EarthSettingsService {
    private final EarthSettingsRepository settings;
    private final InvoicingFacade invoicing;
    private final EarthmovingAccess access;
    private final AuditTrailFacade trail;

    @Transactional(readOnly = true)
    public SettingsResponse get(TenantId tenantId) {
        access.require(EarthmovingRules.READ, "view settings");
        return response(tenantId, contractThreshold(tenantId));
    }

    /** The threshold with no permission check, for the checks that run inside an already authorised action. Null when not set. */
    @Transactional(readOnly = true)
    public BigDecimal contractThreshold(TenantId tenantId) {
        return settings.findById(tenantId.getValue()).map(EarthSettings::getContractThreshold).orElse(null);
    }

    @Transactional
    public SettingsResponse setContractThreshold(TenantId tenantId, BigDecimal threshold, UUID actor) {
        access.require(EarthmovingRules.ADMIN, "change settings");
        EarthSettings s = settings.findById(tenantId.getValue()).orElseGet(() -> EarthSettings.forTenant(tenantId));
        BigDecimal before = s.getContractThreshold();
        s.setContractThreshold(threshold, actor);
        settings.save(s);
        trail.record(tenantId, "EARTHMOVING_SETTINGS_CHANGED", "EARTHMOVING_SETTINGS", tenantId.getValue(),
                Map.of("contractThreshold", before == null ? "off" : before.toPlainString()),
                Map.of("contractThreshold", s.getContractThreshold() == null ? "off" : s.getContractThreshold().toPlainString()));
        return response(tenantId, s.getContractThreshold());
    }

    private SettingsResponse response(TenantId tenantId, BigDecimal threshold) {
        InvoicingFacade.VatTreatment vat = invoicing.vatTreatment(tenantId, EarthHireService.today());
        return new SettingsResponse(threshold, vat.registered(), vat.ratePercent());
    }
}
