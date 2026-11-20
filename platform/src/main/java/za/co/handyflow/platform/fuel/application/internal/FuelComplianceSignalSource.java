package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.repository.FuelComplianceItemRepository;
import za.co.handyflow.platform.shared.ComplianceSignal;
import za.co.handyflow.platform.shared.ComplianceSignal.Severity;
import za.co.handyflow.platform.shared.ComplianceSignalSource;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Fuel's contribution to the Legal Compliance cross-module view. An expired company licence (the petroleum licence) is critical; other expired items and anything
 * expiring within 30 days are warnings. Uses the repository's existing count queries, so it loads no rows beyond the expired company licences.
 */
@Component
@RequiredArgsConstructor
class FuelComplianceSignalSource implements ComplianceSignalSource {

    static final int WARNING_DAYS = 30;

    private final FuelComplianceItemRepository items;

    @Override
    public String moduleKey() { return "fuel"; }

    @Override
    public String moduleName() { return "Fuel"; }

    @Override
    @Transactional(readOnly = true)
    public List<ComplianceSignal> signals(TenantId tenantId) {
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Johannesburg"));
        long licences = items.findExpiredCompanyLicences(tenantId, today).size();
        long expiredOther = Math.max(0, items.countExpired(tenantId, today) - licences);
        long expiring = items.countExpiring(tenantId, today, today.plusDays(WARNING_DAYS));
        List<ComplianceSignal> out = new ArrayList<>();
        if (licences > 0) out.add(new ComplianceSignal("fuel", "licencesExpired", "company licence(s) expired", licences, Severity.CRITICAL, "/fuel/compliance"));
        if (expiredOther > 0) out.add(new ComplianceSignal("fuel", "itemsExpired", "compliance item(s) expired", expiredOther, Severity.WARNING, "/fuel/compliance"));
        if (expiring > 0) out.add(new ComplianceSignal("fuel", "itemsExpiring", "compliance item(s) expire within " + WARNING_DAYS + " days", expiring, Severity.WARNING, "/fuel/compliance"));
        return out;
    }
}
