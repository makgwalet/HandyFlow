package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import za.co.handyflow.platform.controls.application.ControlExceptionFacade;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.domain.enums.SupplierDocType;
import za.co.handyflow.platform.supplychain.domain.repository.ScSupplierDocumentRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

/**
 * Daily: ACTIVE suppliers whose REQUIRED document has expired are flagged on the control board
 * (at most once a week per supplier) and, if app.scm.supplier.auto-suspend-on-expiry=true,
 * suspended with the document named in the reason. Auto-suspend is OFF by default — turning it
 * on suspends suppliers without a human in the loop, so decide that deliberately.
 * Weekly (Mondays): e-mail digest of documents expiring within the warning window.
 * Suppliers that never uploaded a required document are NOT swept (enabling a policy must not
 * mass-suspend your existing suppliers) — they show as MISSING on the compliance endpoint and
 * can't be approved/reinstated until fixed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupplierComplianceScheduler {

    private final ScSupplierDocumentRepository docRepo;
    private final SupplierComplianceService compliance;
    private final SupplierLifecycleService lifecycle;
    private final SupplierAuditService audit;
    private final ControlExceptionFacade controlExceptionFacade;
    private final TenantAdminRecipients tenantAdminRecipients;
    private final EmailService emailService;

    @Value("${app.notifications.enabled:true}")
    private boolean notificationsEnabled;

    @Value("${app.scm.supplier.auto-suspend-on-expiry:false}")
    private boolean autoSuspend;

    @Scheduled(cron = "0 45 5 * * *", zone = "Africa/Johannesburg")
    public void sweepExpiredRequiredDocuments() {
        Set<SupplierDocType> required = compliance.requiredTypes();
        if (required.isEmpty()) return;

        List<Object[]> rows = docRepo.findExpiredRequired(required.stream().map(Enum::name).toList());
        log.info("[SCM] Supplier document sweep: {} expired required document(s)", rows.size());
        for (Object[] row : rows) {
            try {
                UUID tenantId = (UUID) row[0];
                UUID supplierId = (UUID) row[1];
                String name = (String) row[2];
                String docType = (String) row[3];
                String expiry = String.valueOf(row[4]);
                TenantId tid = TenantId.of(tenantId);

                if (audit.happenedWithin(tenantId, supplierId, "COMPLIANCE_EXPIRED", Duration.ofDays(7))) continue;

                String reason = "Required document " + docType + " expired on " + expiry;
                audit.record(tenantId, supplierId, "COMPLIANCE_EXPIRED", null, null, reason, null, "System");
                if (autoSuspend) {
                    lifecycle.suspend(tid, supplierId, null, "System", reason);   // raises its own control exception
                } else {
                    controlExceptionFacade.raise(tid, "supplychain", "SUPPLIER_DOCUMENT_EXPIRED", "ScSupplier",
                            supplierId, "CRITICAL", "Supplier " + name + ": " + reason);
                }
            } catch (Exception e) {
                log.error("[SCM] Supplier document sweep error: {}", e.getMessage(), e);
            }
        }
    }

    @Scheduled(cron = "0 45 6 * * MON", zone = "Africa/Johannesburg")
    public void weeklyExpiryDigest() {
        if (!notificationsEnabled) return;
        Map<UUID, List<String>> byTenant = new LinkedHashMap<>();
        for (Object[] row : docRepo.findExpiringSoon(compliance.warnDays())) {
            byTenant.computeIfAbsent((UUID) row[0], k -> new ArrayList<>()).add(
                    HtmlUtils.htmlEscape((String) row[1]) + " — " + row[2] + " expires " + toDate(row[3]));
        }
        byTenant.forEach((tenantId, lines) -> {
            try {
                StringBuilder items = new StringBuilder();
                lines.forEach(l -> items.append("<li>").append(l).append("</li>"));
                String html = "<p>These supplier compliance documents expire within " + compliance.warnDays() + " days:</p><ul>"
                        + items + "</ul><p>Log in to HandyFlow to request renewed documents.</p>";
                for (Recipient admin : tenantAdminRecipients.resolveTenantAdmins(tenantId)) {
                    if (admin.email() != null && !admin.email().isBlank())
                        emailService.send(admin.email(), "Supplier documents expiring soon", html);
                }
            } catch (Exception e) {
                log.error("[SCM] Expiry digest error for tenant {}: {}", tenantId, e.getMessage());
            }
        });
    }

    private static LocalDate toDate(Object o) {
        if (o instanceof java.sql.Date d) return d.toLocalDate();
        if (o instanceof LocalDate l) return l;
        return LocalDate.parse(String.valueOf(o));
    }
}
