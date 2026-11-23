package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Invoice drafts of every site at once, for the "Invoice drafts" screen. Read only: saving, approving and voiding stay on the
 * site (InvoiceDraftService). The frozen snapshot is not included; open one draft for that.
 */
@Service
@RequiredArgsConstructor
public class InvoiceDraftOverviewService {

    /** More rows than this are cut off and the answer says so, so a total is never shown for a partial list. */
    public static final int LIMIT = 500;

    public record Item(UUID id, UUID siteId, String siteName, String month, String basis, String currency, String status, long totalCents,
                       boolean inProgress, double unpricedHours, boolean mixedVat, String note, OffsetDateTime createdAt,
                       OffsetDateTime approvedAt, OffsetDateTime voidedAt, String voidReason) {}

    public record Overview(List<Item> items, boolean truncated) {}

    private final JdbcTemplate jdbc;

    /** `month` (2026-10) and `status` are optional filters; `allowed` null = the whole company, otherwise only those sites. */
    @Transactional(readOnly = true)
    public Overview list(TenantId tenantId, String month, String status, Set<UUID> allowed) {
        String m = month == null || month.isBlank() ? null : InvoiceDraftRules.requireMonth(month.trim());
        String st = status == null || status.isBlank() ? null : status.trim();
        if (st != null && !st.equals(InvoiceDraftRules.DRAFT) && !st.equals(InvoiceDraftRules.APPROVED) && !st.equals(InvoiceDraftRules.VOID)) {
            throw new HandyFlowException("Status must be DRAFT, APPROVED or VOID.", HttpStatus.BAD_REQUEST, "DRAFT_STATUS");
        }
        StringBuilder sql = new StringBuilder("""
                SELECT d.id, d.site_id, s.name AS site_name, d.month, d.basis, d.currency, d.status, d.total_cents, d.in_progress,
                       d.unpriced_hours, d.mixed_vat, d.note, d.created_at, d.approved_at, d.voided_at, d.void_reason
                FROM security_invoice_drafts d JOIN security_sites s ON s.id = d.site_id AND s.tenant_id = d.tenant_id
                WHERE d.tenant_id = ?""");
        List<Object> p = new ArrayList<>();
        p.add(tenantId.getValue());
        if (m != null) { sql.append(" AND d.month = ?"); p.add(m); }
        if (st != null) { sql.append(" AND d.status = ?"); p.add(st); }
        sql.append(SiteScopeRules.sqlFilter("d.site_id", allowed));
        p.addAll(SiteScopeRules.sqlArgs(allowed));
        sql.append(" ORDER BY d.month DESC, s.name, d.created_at DESC LIMIT ").append(LIMIT + 1);
        List<Item> rows = jdbc.query(sql.toString(), (rs, n) -> new Item(rs.getObject("id", UUID.class), rs.getObject("site_id", UUID.class),
                rs.getString("site_name"), rs.getString("month"), rs.getString("basis"), rs.getString("currency").trim(), rs.getString("status"),
                rs.getLong("total_cents"), rs.getBoolean("in_progress"), rs.getDouble("unpriced_hours"), rs.getBoolean("mixed_vat"), rs.getString("note"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("approved_at", OffsetDateTime.class), rs.getObject("voided_at", OffsetDateTime.class),
                rs.getString("void_reason")), p.toArray());
        boolean truncated = rows.size() > LIMIT;
        return new Overview(truncated ? new ArrayList<>(rows.subList(0, LIMIT)) : rows, truncated);
    }
}
