package za.co.handyflow.platform.accounting.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Invoice status and the ledger must not silently disagree. Every night this looks for invoices that are issued (or later)
 * but have no live issuance journal. A gap is either already queued as an open posting failure (a person can retry it) or
 * missing altogether (nothing was ever tried, which is what INV-01 was). Both are logged as {@code LEDGER-RECONCILE}.
 * Nothing is posted here: a person decides, because the period may be locked and the date matters.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class LedgerReconciliationJob {

    private final JdbcTemplate jdbc;

    record Gap(UUID tenantId, long missing, long queued, BigDecimal total) { }

    @Scheduled(cron = "0 30 2 * * *", zone = "Africa/Johannesburg")
    void run() {
        List<Gap> gaps = gaps();
        if (gaps.isEmpty()) {
            log.info("LEDGER-RECONCILE every issued invoice has its issuance journal");
            return;
        }
        for (Gap g : gaps) {
            log.warn("LEDGER-RECONCILE tenant={} invoices without an issuance journal: {} never tried, {} queued as posting problems, total R {}",
                    g.tenantId(), g.missing(), g.queued(), g.total());
        }
    }

    List<Gap> gaps() {
        return jdbc.query("""
                SELECT i.tenant_id,
                       count(*) FILTER (WHERE NOT q.queued) AS missing,
                       count(*) FILTER (WHERE q.queued)     AS queued,
                       sum(i.total)                         AS total
                  FROM invoices i
                 CROSS JOIN LATERAL (
                       SELECT EXISTS (SELECT 1 FROM acc_posting_failures f
                                       WHERE f.tenant_id = i.tenant_id AND f.status = 'OPEN'
                                         AND f.source_key = 'invoicing:invoice-issued:' || i.id) AS queued) q
                 WHERE i.status NOT IN ('DRAFT', 'CANCELLED') AND i.deleted_at IS NULL
                   AND NOT EXISTS (SELECT 1 FROM acc_journal_entries j
                                    WHERE j.tenant_id = i.tenant_id AND j.deleted_at IS NULL
                                      AND j.source_key = 'invoicing:invoice-issued:' || i.id)
                 GROUP BY i.tenant_id
                 ORDER BY i.tenant_id""",
                (rs, n) -> new Gap(rs.getObject(1, UUID.class), rs.getLong(2), rs.getLong(3), rs.getBigDecimal(4)));
    }
}
