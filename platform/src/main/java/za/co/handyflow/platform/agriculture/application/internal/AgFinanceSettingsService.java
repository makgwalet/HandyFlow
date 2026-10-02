package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgFinanceSettings;
import za.co.handyflow.platform.agriculture.domain.repository.AgFinanceSettingsRepository;
import za.co.handyflow.platform.agriculture.domain.rules.AgLabourRules;
import za.co.handyflow.platform.agriculture.dto.LabourDtos.FinanceSettingsResponse;
import za.co.handyflow.platform.agriculture.dto.LabourDtos.UpdateFinanceSettingsRequest;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** The tenant's labour-costing settings: weekly hours and employer on-cost (ADR-001, W3). Reading never creates a row; the defaults apply until saved. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgFinanceSettingsService {

    /** The numbers in force for a tenant right now. */
    public record Effective(BigDecimal hoursPerWeek, BigDecimal onCostPercent, boolean configured) {}

    private final AgFinanceSettingsRepository repository;

    @Transactional(readOnly = true)
    public Effective effective(TenantId tenantId) {
        Optional<AgFinanceSettings> row = repository.findForTenant(tenantId);
        return row.map(s -> new Effective(s.getStandardHoursPerWeek(), s.getLabourOnCostPercent(), true))
                .orElseGet(() -> new Effective(AgLabourRules.DEFAULT_HOURS_PER_WEEK, AgLabourRules.DEFAULT_ON_COST_PERCENT, false));
    }

    @Transactional(readOnly = true)
    public FinanceSettingsResponse get(TenantId tenantId) {
        return toResponse(effective(tenantId));
    }

    /** Saves the settings. They apply to labour costed from now on; costs already in the ledger keep the rate they were costed at. */
    @Transactional
    public FinanceSettingsResponse update(TenantId tenantId, UUID userId, UpdateFinanceSettingsRequest req) {
        AgFinanceSettings row = repository.findForTenant(tenantId).orElse(null);
        if (row == null) {
            row = AgFinanceSettings.create(tenantId, req.standardHoursPerWeek(), req.labourOnCostPercent(), userId);
        } else {
            row.update(req.standardHoursPerWeek(), req.labourOnCostPercent(), userId);
        }
        repository.save(row);
        log.info("Agriculture finance settings saved hours={} onCost={} tenant={}", req.standardHoursPerWeek(), req.labourOnCostPercent(), tenantId.getValue());
        return toResponse(new Effective(row.getStandardHoursPerWeek(), row.getLabourOnCostPercent(), true));
    }

    private static FinanceSettingsResponse toResponse(Effective e) {
        return new FinanceSettingsResponse(e.hoursPerWeek(), e.onCostPercent(), e.configured());
    }
}
