package za.co.handyflow.platform.invoicing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.identity.TenantDetails;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.invoicing.domain.model.VatRules;
import za.co.handyflow.platform.invoicing.domain.model.VatSetting;
import za.co.handyflow.platform.invoicing.domain.repository.VatSettingRepository;
import za.co.handyflow.platform.invoicing.dto.VatSettingRequest;
import za.co.handyflow.platform.invoicing.dto.VatSettingResponse;
import za.co.handyflow.platform.invoicing.dto.VatSettingsOverview;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.VatRateProvider;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The company's VAT treatment over time. Other modules ask for the treatment on a day (through the Invoicing facade) and put the rate on the
 * lines they supply; nothing here ever changes a document that already exists. Callers check permission: the controller requires SETTINGS_MANAGE to change it.
 */
@Service
@RequiredArgsConstructor
public class VatSettingService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private final VatSettingRepository settings;
    private final TenantFacade tenants;
    private final VatRateProvider platformRate;
    private final AuditTrailFacade trail;

    static LocalDate today() { return LocalDate.now(ZONE); }

    @Transactional(readOnly = true)
    public VatRules.Treatment treatment(TenantId tenantId, LocalDate on) {
        return VatRules.on(rows(tenantId), on == null ? today() : on, platformRate.ratePercent());
    }

    @Transactional(readOnly = true)
    public VatSettingsOverview overview(TenantId tenantId) {
        LocalDate today = today();
        List<VatSetting> live = settings.findLive(tenantId);
        VatRules.Treatment now = VatRules.on(live.stream().map(VatSetting::toRow).toList(), today, platformRate.ratePercent());
        List<VatSettingResponse> upcoming = live.stream().filter(v -> v.getEffectiveFrom().isAfter(today)).map(VatSettingService::toResponse).toList();
        List<VatSettingResponse> history = live.stream().filter(v -> !v.getEffectiveFrom().isAfter(today)).map(VatSettingService::toResponse).toList();
        return new VatSettingsOverview(now.registered(), now.ratePercent(), now.configured(), now.effectiveFrom(), upcoming, history, vatNumberOnFile(tenantId), platformRate.ratePercent());
    }

    @Transactional
    public VatSettingsOverview set(TenantId tenantId, VatSettingRequest req, UUID actor) {
        List<VatSetting> live = settings.findLive(tenantId);
        List<VatRules.Row> rows = live.stream().map(VatSetting::toRow).toList();
        var rate = VatRules.check(req.vatRegistered(), req.standardRate(), req.effectiveFrom(), today(), vatNumberOnFile(tenantId), rows, platformRate.ratePercent());
        String reason = req.reason() == null ? null : req.reason().trim();
        if (reason != null && reason.length() > 300) throw new IllegalArgumentException("Keep the reason to 300 characters.");
        VatRules.Treatment before = VatRules.on(rows, req.effectiveFrom(), platformRate.ratePercent());
        VatSetting saved = settings.save(VatSetting.of(tenantId, req.vatRegistered(), rate, req.effectiveFrom(), reason, actor));
        Map<String, Object> was = new LinkedHashMap<>();
        was.put("registered", before.registered());
        was.put("ratePercent", before.ratePercent().toPlainString());
        Map<String, Object> now = new LinkedHashMap<>();
        now.put("registered", saved.isVatRegistered());
        now.put("ratePercent", saved.getStandardRate().toPlainString());
        now.put("effectiveFrom", saved.getEffectiveFrom().toString());
        if (saved.getReason() != null) now.put("reason", saved.getReason());
        trail.record(tenantId, "VAT_SETTING_CHANGED", "VAT_SETTING", saved.getId(), was, now);
        return overview(tenantId);
    }

    @Transactional
    public VatSettingsOverview cancel(TenantId tenantId, UUID id, UUID actor) {
        VatSetting v = settings.findOwned(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("VAT setting", id.toString()));
        if (v.getCancelledAt() != null) throw new IllegalStateException("This VAT change was already cancelled.");
        VatRules.checkCancel(v.getEffectiveFrom(), today());
        v.cancel();
        settings.save(v);
        trail.record(tenantId, "VAT_SETTING_CANCELLED", "VAT_SETTING", v.getId(),
                Map.of("registered", v.isVatRegistered(), "ratePercent", v.getStandardRate().toPlainString(), "effectiveFrom", v.getEffectiveFrom().toString()), null);
        return overview(tenantId);
    }

    private List<VatRules.Row> rows(TenantId tenantId) { return settings.findLive(tenantId).stream().map(VatSetting::toRow).toList(); }

    private boolean vatNumberOnFile(TenantId tenantId) {
        return tenants.findTenantDetails(tenantId).map(TenantDetails::vatNumber).map(n -> !n.isBlank()).orElse(false);
    }

    private static VatSettingResponse toResponse(VatSetting v) {
        return new VatSettingResponse(v.getId(), v.isVatRegistered(), v.getStandardRate(), v.getEffectiveFrom(), v.getReason(), v.getCreatedAt());
    }
}
