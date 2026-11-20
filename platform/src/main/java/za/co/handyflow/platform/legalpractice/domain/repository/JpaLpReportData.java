package za.co.handyflow.platform.legalpractice.domain.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalpractice.application.internal.LpReportData;
import za.co.handyflow.platform.legalpractice.dto.ReportDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The report totals as read-only SQL. The statements are kept as constants so they can be run on their own against PostgreSQL (see checks/V832-checks.sql). */
@Repository("legalPracticeReportData")
public class JpaLpReportData implements LpReportData {

    public static final String SQL_MATTER_COUNTS = """
            SELECT m.status, m.matter_type, COUNT(*) FROM lp_matters m
             WHERE m.tenant_id = :tenant AND m.id NOT IN (:bm) AND m.client_id NOT IN (:bc)
             GROUP BY m.status, m.matter_type ORDER BY m.status, m.matter_type""";

    public static final String SQL_UNBILLED = """
            SELECT m.id, m.matter_number, m.matter_name, c.name,
                   COALESCE(te.hours, 0), COALESCE(te.val, 0), COALESCE(d.amt, 0)
              FROM lp_matters m
              JOIN lp_clients c ON c.id = m.client_id AND c.tenant_id = m.tenant_id
              LEFT JOIN (SELECT matter_id, SUM(hours) AS hours, SUM(hours * hourly_rate) AS val FROM lp_time_entries
                          WHERE tenant_id = :tenant AND billable AND status = 'UNBILLED' GROUP BY matter_id) te ON te.matter_id = m.id
              LEFT JOIN (SELECT matter_id, SUM(amount) AS amt FROM lp_disbursements
                          WHERE tenant_id = :tenant AND status = 'UNBILLED' GROUP BY matter_id) d ON d.matter_id = m.id
             WHERE m.tenant_id = :tenant AND m.id NOT IN (:bm) AND m.client_id NOT IN (:bc) AND (te.hours IS NOT NULL OR d.amt IS NOT NULL)
             ORDER BY COALESCE(te.val, 0) + COALESCE(d.amt, 0) DESC, m.matter_number
             LIMIT :lim""";

    public static final String SQL_OPEN_INVOICES = """
            SELECT i.id, i.invoice_number, c.name, m.matter_number, m.matter_name, i.issue_date, i.due_date, i.total_amount, i.amount_paid
              FROM lp_invoices i
              JOIN lp_clients c ON c.id = i.client_id AND c.tenant_id = i.tenant_id
              LEFT JOIN lp_matters m ON m.id = i.matter_id AND m.tenant_id = i.tenant_id
             WHERE i.tenant_id = :tenant AND i.status IN ('SENT', 'PARTIALLY_PAID') AND i.total_amount > i.amount_paid
               AND i.client_id NOT IN (:bc) AND (i.matter_id IS NULL OR i.matter_id NOT IN (:bm))
             ORDER BY i.due_date ASC NULLS LAST, i.invoice_number
             LIMIT :lim""";

    public static final String SQL_INVOICES_ISSUED = """
            SELECT to_char(i.issue_date, 'YYYY-MM'), i.status, COUNT(*), SUM(i.subtotal), SUM(i.vat_amount), SUM(i.total_amount), SUM(i.amount_paid)
              FROM lp_invoices i
             WHERE i.tenant_id = :tenant AND i.status <> 'DRAFT' AND i.issue_date BETWEEN :from AND :to
               AND i.client_id NOT IN (:bc) AND (i.matter_id IS NULL OR i.matter_id NOT IN (:bm))
             GROUP BY 1, 2 ORDER BY 1, 2""";

    public static final String SQL_TRUST = """
            SELECT c.id, c.name, SUM(CASE WHEN t.transaction_type = 'RECEIPT' THEN t.amount ELSE -t.amount END), MAX(t.transaction_date)
              FROM lp_trust_transactions t
              JOIN lp_clients c ON c.id = t.client_id AND c.tenant_id = t.tenant_id
             WHERE t.tenant_id = :tenant AND t.transaction_date <= :asAt AND t.client_id NOT IN (:bc) AND (t.matter_id IS NULL OR t.matter_id NOT IN (:bm))
             GROUP BY c.id, c.name
            HAVING SUM(CASE WHEN t.transaction_type = 'RECEIPT' THEN t.amount ELSE -t.amount END) <> 0
             ORDER BY c.name""";

    @PersistenceContext
    private EntityManager em;

    private Query q(String sql, TenantId tenant, List<UUID> bm, List<UUID> bc) {
        Query q = em.createNativeQuery(sql).setParameter("tenant", tenant.getValue()).setParameter("bm", bm).setParameter("bc", bc);
        return q;
    }

    @SuppressWarnings("unchecked")
    private static List<Object[]> rows(Query q) { return (List<Object[]>) q.getResultList(); }

    private static BigDecimal dec(Object o) { return o == null ? BigDecimal.ZERO : o instanceof BigDecimal b ? b : new BigDecimal(o.toString()); }
    private static LocalDate date(Object o) { return o == null ? null : o instanceof Date d ? d.toLocalDate() : LocalDate.parse(o.toString().substring(0, 10)); }
    private static UUID uuid(Object o) { return o instanceof UUID u ? u : UUID.fromString(o.toString()); }
    private static String label(Object number, Object name) { return number == null ? null : number + " " + name; }

    @Override @Transactional(readOnly = true)
    public List<MatterCount> matterCounts(TenantId tenant, List<UUID> bm, List<UUID> bc) {
        List<MatterCount> out = new ArrayList<>();
        for (Object[] r : rows(q(SQL_MATTER_COUNTS, tenant, bm, bc))) out.add(new MatterCount((String) r[0], (String) r[1], ((Number) r[2]).longValue()));
        return out;
    }

    @Override @Transactional(readOnly = true)
    public List<UnbilledRow> unbilled(TenantId tenant, List<UUID> bm, List<UUID> bc, int limit) {
        List<UnbilledRow> out = new ArrayList<>();
        for (Object[] r : rows(q(SQL_UNBILLED, tenant, bm, bc).setParameter("lim", limit))) {
            BigDecimal value = dec(r[5]), disb = dec(r[6]);
            out.add(new UnbilledRow(uuid(r[0]), label(r[1], r[2]), (String) r[3], dec(r[4]), value, disb, value.add(disb)));
        }
        return out;
    }

    @Override @Transactional(readOnly = true)
    public List<DebtorRow> openInvoices(TenantId tenant, List<UUID> bm, List<UUID> bc, int limit) {
        List<DebtorRow> out = new ArrayList<>();
        for (Object[] r : rows(q(SQL_OPEN_INVOICES, tenant, bm, bc).setParameter("lim", limit)))
            out.add(new DebtorRow(uuid(r[0]), (String) r[1], (String) r[2], label(r[3], r[4]), date(r[5]), date(r[6]), dec(r[7]), dec(r[8]), null, 0, null));
        return out;
    }

    @Override @Transactional(readOnly = true)
    public List<BillingRow> invoicesIssued(TenantId tenant, LocalDate from, LocalDate to, List<UUID> bm, List<UUID> bc) {
        List<BillingRow> out = new ArrayList<>();
        for (Object[] r : rows(q(SQL_INVOICES_ISSUED, tenant, bm, bc).setParameter("from", from).setParameter("to", to)))
            out.add(new BillingRow((String) r[0], (String) r[1], ((Number) r[2]).longValue(), dec(r[3]), dec(r[4]), dec(r[5]), dec(r[6])));
        return out;
    }

    @Override @Transactional(readOnly = true)
    public List<TrustRow> trustBalances(TenantId tenant, LocalDate asAt, List<UUID> bm, List<UUID> bc) {
        List<TrustRow> out = new ArrayList<>();
        for (Object[] r : rows(q(SQL_TRUST, tenant, bm, bc).setParameter("asAt", asAt)))
            out.add(new TrustRow(uuid(r[0]), (String) r[1], dec(r[2]), date(r[3])));
        return out;
    }
}
