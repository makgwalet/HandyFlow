package za.co.handyflow.platform.admin.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.admin.domain.model.AdminAuditLog;
import za.co.handyflow.platform.admin.domain.repository.AdminAuditLogRepository;
import za.co.handyflow.platform.admin.dto.SupportActionDescriptor;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.SupportAction;
import za.co.handyflow.platform.shared.SupportActionResult;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The dispatcher side of the support-action framework — see
 * {@code shared.SupportAction}'s own Javadoc for the full architecture
 * (a published interface in {@code shared}, implementations living in
 * their own owning modules, collected here by Spring's ordinary
 * {@code List<SupportAction>} collection-injection). This class itself
 * never imports a single type from {@code identity} or any other
 * business module — {@code admin}'s {@code package-info.java}
 * {@code allowedDependencies} stays exactly {@code {"shared"}}, the same
 * as it was before this framework existed.
 * <p>
 * {@code actions} is genuinely just whatever beans of type
 * {@code SupportAction} exist in the running application at startup — if
 * a module never registers one, its actions simply never appear here.
 * There's no separate registration step to forget.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupportActionService {

    private final List<SupportAction> actions;
    private final AdminAuditLogRepository auditRepo;

    public List<SupportActionDescriptor> listActions() {
        return actions.stream()
                .map(a -> new SupportActionDescriptor(a.key(), a.label(), a.description()))
                .sorted(Comparator.comparing(SupportActionDescriptor::label))
                .toList();
    }

    /**
     * Executes the named action and audits the outcome — success or
     * failure, and whether it failed because the action itself returned
     * a failure result or because it threw. Every path through this
     * method ends in exactly one audit log entry; there's no way to
     * execute a support action that doesn't get recorded.
     */
    @Transactional
    public SupportActionResult execute(String actionKey, UUID tenantId, UUID targetId, Map<String, String> params,
                                       UUID adminId, String adminEmail, String ipAddress) {
        SupportAction action = actions.stream()
                .filter(a -> a.key().equals(actionKey))
                .findFirst()
                .orElseThrow(() -> new HandyFlowException(
                        "Unknown support action: " + actionKey, HttpStatus.BAD_REQUEST, "UNKNOWN_SUPPORT_ACTION"));

        SupportActionResult result;
        try {
            result = action.execute(TenantId.of(tenantId), targetId, params != null ? params : Map.of(), adminId);
        } catch (Exception ex) {
            log.error("Support action {} threw for tenant={} target={}: {}", actionKey, tenantId, targetId, ex.getMessage(), ex);
            result = SupportActionResult.failure("Action failed: " + ex.getMessage());
        }

        auditRepo.save(AdminAuditLog.create(adminId, adminEmail,
                "SUPPORT_ACTION:" + actionKey, "SUPPORT_ACTION",
                targetId != null ? targetId.toString() : tenantId.toString(),
                action.label(),
                "{\"tenantId\":\"" + tenantId + "\",\"success\":" + result.success()
                        + ",\"message\":" + jsonString(result.message()) + "}",
                ipAddress));

        log.info("Support action {} executed tenant={} target={} success={} by={}",
                actionKey, tenantId, targetId, result.success(), adminEmail);
        return result;
    }

    // Minimal, dependency-free JSON string escaping for the one field here
    // that's genuinely free text (an action's own result message) — the
    // rest of this details blob is closed-set values (uuids, booleans)
    // that don't need it. Not a general-purpose JSON builder; if this
    // audit details blob grows more fields, reach for the project's real
    // ObjectMapper instead of extending this by hand.
    private String jsonString(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
