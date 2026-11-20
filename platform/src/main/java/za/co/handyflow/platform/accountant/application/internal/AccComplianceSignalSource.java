package za.co.handyflow.platform.accountant.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accountant.domain.repository.AccFicaComplianceCounts;
import za.co.handyflow.platform.shared.ComplianceSignal;
import za.co.handyflow.platform.shared.ComplianceSignal.Severity;
import za.co.handyflow.platform.shared.ComplianceSignalSource;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Accountant module's contribution to the Legal Compliance cross-module view: the practice's own client due-diligence (FICA) documents that have expired without a replacement,
 * or expire within 30 days. Both are warnings: the practice decides what its risk-management programme requires. Counts documents only.
 */
@Component
@RequiredArgsConstructor
class AccComplianceSignalSource implements ComplianceSignalSource {

    static final int WARNING_DAYS = 30;
    private static final String URL = "/accountant/clients";

    private final AccFicaComplianceCounts counts;

    @Override public String moduleKey() { return "accountant"; }
    @Override public String moduleName() { return "Accountant"; }

    @Override
    @Transactional(readOnly = true)
    public List<ComplianceSignal> signals(TenantId t) {
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Johannesburg"));
        UUID id = t.getValue();
        long expired = counts.expiredNotReplaced(id, today), expiring = counts.expiringNotReplaced(id, today, today.plusDays(WARNING_DAYS));
        List<ComplianceSignal> out = new ArrayList<>();
        if (expired > 0) out.add(new ComplianceSignal("accountant", "ficaExpired", "client FICA document(s) expired and not replaced", expired, Severity.WARNING, URL));
        if (expiring > 0) out.add(new ComplianceSignal("accountant", "ficaExpiring", "client FICA document(s) expire within " + WARNING_DAYS + " days", expiring, Severity.WARNING, URL));
        return out;
    }
}
