package za.co.handyflow.platform.admin.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Single source of truth for per-module adoption/MRR metrics.
 * <p>
 * Confirmed real, before this class existed: four separate hand-written
 * SQL blocks answered overlapping versions of "module adoption / MRR by
 * module" —
 * <ul>
 *   <li>AdminLookupController.getModuleStats() → AdminLookupService
 *       (real SQL never located despite two searches)</li>
 *   <li>AdminController.getModuleAdoption() → AdminService
 *       (real SQL never located — controller body was cut off both times
 *       it surfaced in search)</li>
 *   <li>AdminController.getMrrBreakdown() → AdminService.getMrrBreakdown()
 *       — confirmed real, LEFT JOIN with no status filter on the join
 *       itself, buckets active/trial via CASE</li>
 *   <li>AdminService.getDashboard()'s inline mrrByModule block —
 *       confirmed real, DIFFERENT join strategy (filters the join itself
 *       to AND tm.status = 'ACTIVE'), doesn't compute trial_count at all</li>
 * </ul>
 * The last two alone were confirmed capable of producing different
 * numbers for the same underlying question given the same data — a
 * CANCELLED tenant_modules row is counted differently by each join
 * strategy. For a metric literally called MRR, that's a real risk, not
 * cosmetic debt.
 * <p>
 * CAUTION before redirecting getModuleStats()/getModuleAdoption() onto
 * this: their real current SQL was never found, only their endpoint
 * descriptions ("active, trial, cancelled, conversion rate per module").
 * conversion_rate_pct below is a PROPOSED definition — of everything that
 * ever went through a trial (trial_ends_at IS NOT NULL) and has since
 * resolved to ACTIVE or CANCELLED, what fraction converted to ACTIVE —
 * not confirmed to match whatever getModuleAdoption() currently computes.
 * Confirm the real logic there before cutting that endpoint over, or this
 * consolidation risks silently changing a number someone's already
 * looking at.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminReportingService {

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getModuleMetrics() {
        return jdbc.queryForList("""
            SELECT mc.key, mc.name, mc.category, mc.monthly_price,
                   COUNT(CASE WHEN tm.status = 'ACTIVE'    THEN 1 END) AS active_count,
                   COUNT(CASE WHEN tm.status = 'TRIAL'     THEN 1 END) AS trial_count,
                   COUNT(CASE WHEN tm.status = 'CANCELLED' THEN 1 END) AS cancelled_count,
                   COUNT(CASE WHEN tm.status = 'ACTIVE' THEN 1 END) * mc.monthly_price AS module_mrr,
                   CASE WHEN COUNT(CASE WHEN tm.trial_ends_at IS NOT NULL
                                        AND tm.status IN ('ACTIVE','CANCELLED') THEN 1 END) > 0
                        THEN ROUND(100.0 * COUNT(CASE WHEN tm.status = 'ACTIVE' AND tm.trial_ends_at IS NOT NULL THEN 1 END)
                            / COUNT(CASE WHEN tm.trial_ends_at IS NOT NULL AND tm.status IN ('ACTIVE','CANCELLED') THEN 1 END), 1)
                        ELSE NULL END AS conversion_rate_pct
            FROM module_catalogue mc
            LEFT JOIN tenant_modules tm ON tm.module_key = mc.key
            GROUP BY mc.key, mc.name, mc.category, mc.monthly_price, mc.sort_order
            ORDER BY module_mrr DESC, mc.sort_order
            """);
    }
}