package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.ComplianceSignal;
import za.co.handyflow.platform.shared.ComplianceSignal.Severity;
import za.co.handyflow.platform.shared.ComplianceSignalSource;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.domain.repository.ScSupplierComplianceCounts;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Supply Chain's contribution to the Legal Compliance cross-module view: active suppliers whose B-BBEE certificate has expired, or expires within 30 days. These are warnings, not
 * critical: an expired certificate changes the supplier's procurement recognition, it is not unlawful to keep trading. Counts suppliers only (no names).
 */
@Component
@RequiredArgsConstructor
class ScComplianceSignalSource implements ComplianceSignalSource {

    static final int WARNING_DAYS = 30;
    private static final String URL = "/supply-chain/suppliers";

    private final ScSupplierComplianceCounts counts;

    @Override public String moduleKey() { return "supply_chain"; }
    @Override public String moduleName() { return "Supply chain"; }

    @Override
    @Transactional(readOnly = true)
    public List<ComplianceSignal> signals(TenantId t) {
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Johannesburg"));
        UUID id = t.getValue();
        long expired = counts.bbbeeExpired(id, today), expiring = counts.bbbeeExpiring(id, today, today.plusDays(WARNING_DAYS));
        List<ComplianceSignal> out = new ArrayList<>();
        if (expired > 0) out.add(new ComplianceSignal("supply_chain", "bbbeeExpired", "active supplier(s) with an expired B-BBEE certificate", expired, Severity.WARNING, URL));
        if (expiring > 0) out.add(new ComplianceSignal("supply_chain", "bbbeeExpiring", "active supplier(s) whose B-BBEE certificate expires within " + WARNING_DAYS + " days", expiring, Severity.INFO, URL));
        return out;
    }
}
