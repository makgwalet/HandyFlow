package za.co.handyflow.platform.identity.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves a Recipient for a single platform user by id — used wherever a
 * module needs to notify a specific person it only knows by userId
 * (Expenses notifying a claimant, etc.).
 * <p>
 * Its own @Component/REQUIRES_NEW bean, not a private helper on the calling
 * service, for the same reason TenantAdminRecipientsImpl and
 * BillingRecipientResolver are: @Transactional on a private, self-invoked
 * method is silently ignored by Spring's proxy (self-invocation bypasses
 * the proxy entirely), so it would not actually get its own transaction —
 * a failed lookup here would abort the CALLER's transaction instead of
 * just this one, exactly as happened before this class existed.
 * <p>
 * Tenant-scoped deliberately: an earlier version of this lookup queried
 * users by id alone, with no tenant_id check at all, meaning a userId
 * from any tenant would resolve. Never repeat that — always scope to the
 * tenant that's asking.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRecipientResolverImpl implements UserRecipientResolver {

    private final JdbcTemplate jdbc;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<Recipient> resolveUser(TenantId tenantId, UUID userId) {
        if (userId == null) return Optional.empty();
        try {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT id, first_name || ' ' || last_name AS name, email, phone " +
                            "FROM users WHERE id = ? AND tenant_id = ?",
                    userId, tenantId.getValue());
            return Optional.of(Recipient.user(
                    UUID.fromString(row.get("id").toString()),
                    (String) row.get("name"),
                    (String) row.get("email"),
                    (String) row.get("phone")));
        } catch (Exception e) {
            log.warn("Could not resolve user recipient tenant={} userId={}: {}",
                    tenantId, userId, e.getMessage());
            return Optional.empty();
        }
    }
}