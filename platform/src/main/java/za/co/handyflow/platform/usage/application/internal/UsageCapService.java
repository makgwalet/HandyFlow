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
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The hard caps. Approving the tier limits did not switch these on: every cap is OFF until the owner approves a staged
 * rollout. When a cap is switched on it can be limited to named businesses first ({@code handyflow.usage.hard-cap.tenants},
 * a comma-separated list of tenant ids); an empty list means every business.
 */
@Service
@RequiredArgsConstructor
class UsageCapService implements UsageCapFacade {

    private final JdbcTemplate jdbc;
    private final UsageService usage;

    @Value("${handyflow.usage.invoice-hard-cap.enabled:false}")
    private boolean enabled;

    @Value("${handyflow.usage.quote-hard-cap.enabled:false}")
    private boolean quotesEnabled;

    /** Staged rollout: only these businesses are capped while the list is not empty. */
    @Value("${handyflow.usage.hard-cap.tenants:}")
    private String rolloutTenants;

    @Override
    public Decision checkAndLock(TenantId tenantId) {
        return check(tenantId, enabled, false);
    }

    @Override
    public void requireCapacity(TenantId tenantId) {
        Decision d = check(tenantId, enabled, false);
        if (!d.allowed())
            throw new HandyFlowException("You have issued " + d.used() + " of the " + d.limit() + " invoices your plan allows this month ("
                    + d.period() + "). Upgrade your plan or wait for next month. Drafts, credit notes and your existing records are not affected.",
                    HttpStatus.CONFLICT, "INVOICE_CAP_REACHED");
    }

    @Override
    public void requireQuoteCapacity(TenantId tenantId) {
        Decision d = check(tenantId, quotesEnabled, true);
        if (!d.allowed())
            throw new HandyFlowException("You have sent " + d.used() + " of the " + d.limit() + " quotes your plan allows this month ("
                    + d.period() + "). Upgrade your plan or wait for next month. Drafts and your existing quotes are not affected.",
                    HttpStatus.CONFLICT, "QUOTE_CAP_REACHED");
    }

    private Decision check(TenantId tenantId, boolean switchedOn, boolean quotes) {
        if (!switchedOn || !inRollout(tenantId)) return new Decision(false, true, 0, null, null);
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {}, (quotes ? "quote-cap:" : "invoice-cap:") + tenantId.getValue());
        UsageService.Snapshot s = usage.snapshot(tenantId, LocalDate.now(UsageService.SA));
        UsageService.Meter m = quotes ? s.quotes() : s.invoices();
        Integer limit = m.limit();
        boolean enforced = limit != null && limit > 0;
        return new Decision(enforced, UsageRules.capAllows(m.used(), limit), m.used(), limit, s.period());
    }

    private boolean inRollout(TenantId tenantId) {
        if (rolloutTenants == null || rolloutTenants.isBlank()) return true;
        Set<String> ids = Arrays.stream(rolloutTenants.split(",")).map(String::trim).filter(x -> !x.isEmpty()).collect(Collectors.toSet());
        return ids.contains(String.valueOf(tenantId.getValue()));
    }
}
