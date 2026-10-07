// security/application/internal/GuardRiskSettingsService.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.GuardRiskSettings;
import za.co.handyflow.platform.security.domain.repository.GuardRiskSettingsRepository;
import za.co.handyflow.platform.security.dto.GuardPerformanceDtos.RiskSettingsDto;
import za.co.handyflow.platform.security.dto.GuardPerformanceDtos.SaveRiskSettingsRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

/** Reads and saves a tenant's risk thresholds. Until something is saved, the defaults apply. */
@Service
@RequiredArgsConstructor
public class GuardRiskSettingsService {

    private final GuardRiskSettingsRepository repository;

    @Transactional(readOnly = true)
    public GuardRiskEngine.Settings effective(TenantId tenantId) {
        return repository.findForTenant(tenantId).map(GuardRiskSettings::toSettings).orElseGet(GuardRiskEngine.Settings::defaults);
    }

    @Transactional(readOnly = true)
    public RiskSettingsDto get(TenantId tenantId) {
        return repository.findForTenant(tenantId).map(s -> dto(s.toSettings(), true, s.getUpdatedByName(), s.getUpdatedAt()))
                .orElseGet(() -> dto(GuardRiskEngine.Settings.defaults(), false, null, null));
    }

    @Transactional
    public RiskSettingsDto save(TenantId tenantId, SaveRiskSettingsRequest req, String byName) {
        var settings = new GuardRiskEngine.Settings(req.reviewAt(), req.warningAt(), req.investigationAt(), req.windowDays(),
                req.misconductAt(), req.misconductWindowDays(), req.suspensionReviewOnCritical());
        String problem = GuardRiskEngine.validate(settings);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "INVALID_RISK_SETTINGS");
        GuardRiskSettings row = repository.findForTenant(tenantId).orElseGet(() -> GuardRiskSettings.forTenant(tenantId));
        row.apply(settings, byName);
        repository.save(row);
        return dto(settings, true, row.getUpdatedByName(), row.getUpdatedAt());
    }

    public static RiskSettingsDto dto(GuardRiskEngine.Settings s, boolean customised, String by, java.time.Instant at) {
        return new RiskSettingsDto(s.reviewAt(), s.warningAt(), s.investigationAt(), s.windowDays(), s.misconductAt(), s.misconductWindowDays(),
                s.suspensionReviewOnCritical(), customised, by, at);
    }
}
