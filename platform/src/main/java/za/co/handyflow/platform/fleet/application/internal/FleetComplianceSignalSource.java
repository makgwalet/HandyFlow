package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.model.VehicleStatus;
import za.co.handyflow.platform.fleet.domain.repository.VehicleRepository;
import za.co.handyflow.platform.shared.ComplianceSignal;
import za.co.handyflow.platform.shared.ComplianceSignal.Severity;
import za.co.handyflow.platform.shared.ComplianceSignalSource;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Fleet's contribution to the Legal Compliance cross-module view: lapsed and soon-to-lapse licence disc, roadworthy and insurance dates. Counts only. */
@Component
@RequiredArgsConstructor
class FleetComplianceSignalSource implements ComplianceSignalSource {

    private static final int MAX_VEHICLES = 500;

    private final VehicleRepository vehicles;

    @Override
    public String moduleKey() { return "fleet"; }

    @Override
    public String moduleName() { return "Fleet"; }

    @Override
    @Transactional(readOnly = true)
    public List<ComplianceSignal> signals(TenantId tenantId) {
        Page<Vehicle> page = vehicles.findAllActive(tenantId, PageRequest.of(0, MAX_VEHICLES));
        List<FleetSignalRules.Dates> dates = page.getContent().stream().filter(v -> v.getStatus() != VehicleStatus.RETIRED)
                .map(v -> new FleetSignalRules.Dates(v.getLicenceDiscExpiry(), v.getRoadworthyExpiry(), v.getInsuranceExpiry())).toList();
        FleetSignalRules.Counts c = FleetSignalRules.count(dates, LocalDate.now(ZoneId.of("Africa/Johannesburg")));
        List<ComplianceSignal> out = new ArrayList<>();
        if (c.expired() > 0) out.add(new ComplianceSignal("fleet", "datesExpired", "vehicle licence, roadworthy or insurance date(s) expired", c.expired(), Severity.CRITICAL, "/fleet/compliance"));
        if (c.expiring() > 0) out.add(new ComplianceSignal("fleet", "datesExpiring", "vehicle licence, roadworthy or insurance date(s) expire within " + FleetSignalRules.WARNING_DAYS + " days", c.expiring(), Severity.WARNING, "/fleet/compliance"));
        if (page.getTotalElements() > MAX_VEHICLES)
            out.add(new ComplianceSignal("fleet", "truncated", "vehicle(s) not checked (only the first " + MAX_VEHICLES + " are)", page.getTotalElements() - MAX_VEHICLES, Severity.INFO, "/fleet/compliance"));
        return out;
    }
}
