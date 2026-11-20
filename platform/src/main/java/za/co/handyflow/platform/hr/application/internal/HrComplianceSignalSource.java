package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.repository.HrComplianceCounts;
import za.co.handyflow.platform.shared.ComplianceSignal;
import za.co.handyflow.platform.shared.ComplianceSignal.Severity;
import za.co.handyflow.platform.shared.ComplianceSignalSource;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * HR's contribution to the Legal Compliance cross-module view: current employees whose work permit or passport has expired or ends within 30 days. An employee working on an
 * expired work permit is critical (employing someone without a valid permit is an offence); an expired passport and anything expiring soon are warnings. Counts people only.
 */
@Component
@RequiredArgsConstructor
class HrComplianceSignalSource implements ComplianceSignalSource {

    static final int WARNING_DAYS = 30;
    private static final String URL = "/hr/employees";

    private final HrComplianceCounts counts;

    @Override public String moduleKey() { return "hr"; }
    @Override public String moduleName() { return "HR"; }

    @Override
    @Transactional(readOnly = true)
    public List<ComplianceSignal> signals(TenantId t) {
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Johannesburg")), limit = today.plusDays(WARNING_DAYS);
        List<ComplianceSignal> out = new ArrayList<>();
        add(out, "workPermitsExpired", "employee work permit(s) expired", counts.workPermitsExpired(t, today), Severity.CRITICAL);
        add(out, "workPermitsExpiring", "employee work permit(s) expire within " + WARNING_DAYS + " days", counts.workPermitsExpiring(t, today, limit), Severity.WARNING);
        add(out, "passportsExpired", "employee passport(s) expired", counts.passportsExpired(t, today), Severity.WARNING);
        add(out, "passportsExpiring", "employee passport(s) expire within " + WARNING_DAYS + " days", counts.passportsExpiring(t, today, limit), Severity.INFO);
        return out;
    }

    private static void add(List<ComplianceSignal> out, String key, String label, long n, Severity s) {
        if (n > 0) out.add(new ComplianceSignal("hr", key, label, n, s, URL));
    }
}
