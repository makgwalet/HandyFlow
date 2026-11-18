package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

/**
 * Daily housekeeping for supplier contracts (06:15 SAST):
 * <ol>
 *   <li>an ACTIVE contract past its end date either renews for another term of the same length (auto-renew) or becomes EXPIRED;</li>
 *   <li>one reminder to the company's admins when a contract enters its renewal-notice window, listing the contracts per company.</li>
 * </ol>
 * Each step is its own small transaction per row, so one bad contract cannot stop the rest.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractScheduler {

    private final JdbcTemplate jdbc;
    private final ScmNotificationService notifications;

    @Scheduled(cron = "0 15 6 * * *", zone = "Africa/Johannesburg")
    public void run() {
        LocalDate today = LocalDate.now();
        expireOrRenew(today);
        remind(today);
    }

    void expireOrRenew(LocalDate today) {
        List<Map<String, Object>> ended = jdbc.queryForList(
                "SELECT id, tenant_id, contract_number, start_date, end_date, auto_renew FROM sc_contracts WHERE status = 'ACTIVE' AND end_date < ?", Date.valueOf(today));
        for (Map<String, Object> c : ended) {
            try {
                UUID id = (UUID) c.get("id");
                if (Boolean.TRUE.equals(c.get("auto_renew"))) {
                    var next = ContractRules.nextTerm(((Date) c.get("start_date")).toLocalDate(), ((Date) c.get("end_date")).toLocalDate());
                    jdbc.update("UPDATE sc_contracts SET start_date = ?, end_date = ?, renewal_notice_sent_at = NULL, updated_at = now() WHERE id = ? AND status = 'ACTIVE'",
                            Date.valueOf(next.start()), Date.valueOf(next.end()), id);
                    log.info("[SCM] Contract {} auto-renewed to {}", c.get("contract_number"), next.end());
                } else {
                    jdbc.update("UPDATE sc_contracts SET status = 'EXPIRED', updated_at = now() WHERE id = ? AND status = 'ACTIVE'", id);
                    log.info("[SCM] Contract {} expired", c.get("contract_number"));
                }
            } catch (Exception e) {
                log.error("[SCM] Contract {} expiry/renewal failed: {}", c.get("contract_number"), e.getMessage());
            }
        }
    }

    void remind(LocalDate today) {
        List<Map<String, Object>> due = jdbc.queryForList("""
                SELECT id, tenant_id, contract_number, supplier_name, title, end_date, auto_renew FROM sc_contracts
                 WHERE status = 'ACTIVE' AND renewal_notice_sent_at IS NULL AND end_date >= ? AND end_date - renewal_notice_days <= ?
                 ORDER BY tenant_id, end_date""", Date.valueOf(today), Date.valueOf(today));
        Map<UUID, List<Map<String, Object>>> byTenant = new LinkedHashMap<>();
        for (Map<String, Object> c : due) byTenant.computeIfAbsent((UUID) c.get("tenant_id"), k -> new ArrayList<>()).add(c);
        byTenant.forEach((tenantId, contracts) -> {
            try {
                StringBuilder body = new StringBuilder("<p>These supplier contracts are inside their renewal notice period. Decide whether to renew, renegotiate or let them end.</p><ul>");
                for (Map<String, Object> c : contracts)
                    body.append("<li><strong>").append(HtmlUtils.htmlEscape((String) c.get("contract_number"))).append("</strong> ")
                            .append(HtmlUtils.htmlEscape((String) c.get("supplier_name"))).append(" — ").append(HtmlUtils.htmlEscape((String) c.get("title")))
                            .append(", ends ").append(((Date) c.get("end_date")).toLocalDate())
                            .append(Boolean.TRUE.equals(c.get("auto_renew")) ? " (renews automatically)" : "").append("</li>");
                body.append("</ul>");
                notifications.notifyAdmins(tenantId, "Supplier contracts approaching renewal (" + contracts.size() + ")", body.toString());
                for (Map<String, Object> c : contracts)
                    jdbc.update("UPDATE sc_contracts SET renewal_notice_sent_at = now() WHERE id = ?", c.get("id"));
            } catch (Exception e) {
                log.error("[SCM] Contract renewal reminder failed for tenant={}: {}", tenantId, e.getMessage());
            }
        });
    }
}
