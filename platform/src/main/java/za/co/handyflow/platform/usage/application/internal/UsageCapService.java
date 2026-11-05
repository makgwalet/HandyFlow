package za.co.handyflow.platform.usage.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.usage.UsageCapFacade;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
class UsageCapService implements UsageCapFacade {

    private final JdbcTemplate jdbc;
    private final UsageService usage;

    /** Off by default: the limits are provisional, so nothing is blocked until the owner switches this on. */
    @Value("${handyflow.usage.invoice-hard-cap.enabled:false}")
    private boolean enabled;

    @Override
    public Decision checkAndLock(TenantId tenantId) {
        if (!enabled) return new Decision(false, true, 0, null, null);
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {}, "invoice-cap:" + tenantId.getValue());
        UsageService.Snapshot s = usage.snapshot(tenantId, LocalDate.now(UsageService.SA));
        Integer limit = s.invoices().limit();
        boolean enforced = limit != null && limit > 0;
        return new Decision(enforced, UsageRules.capAllows(s.invoices().used(), limit), s.invoices().used(), limit, s.period());
    }

    @Override
    public void requireCapacity(TenantId tenantId) {
        Decision d = checkAndLock(tenantId);
        if (!d.allowed())
            throw new HandyFlowException("You have issued " + d.used() + " of the " + d.limit() + " invoices your plan allows this month ("
                    + d.period() + "). Upgrade your plan or wait for next month. Drafts, credit notes and your existing records are not affected.",
                    HttpStatus.CONFLICT, "INVOICE_CAP_REACHED");
    }
}
