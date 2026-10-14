package za.co.handyflow.platform.accountant.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Client-level access in the practice (finance patch F15). Staff with ACCOUNTANT_ALL_CLIENTS see every client; everyone else
 * sees the clients they are assigned to. Used by a request filter for every {@code /clients/{id}/...} URL and by the
 * service for lists and for endpoints that name a client some other way.
 */
@Service
@RequiredArgsConstructor
public class ClientAccessService {

    public static final String ALL_CLIENTS = "ACCOUNTANT_ALL_CLIENTS";

    private final JdbcTemplate jdbc;

    /** True when the signed-in person holds ACCOUNTANT_ALL_CLIENTS. */
    public boolean seesAll() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) return false;
        return a.getAuthorities().stream().anyMatch(g -> ALL_CLIENTS.equals(g.getAuthority()));
    }

    public Set<UUID> assigned(TenantId tenantId, UUID userId) {
        if (userId == null) return Set.of();
        return new HashSet<>(jdbc.queryForList(
                "SELECT DISTINCT client_id FROM acc_client_assignments WHERE tenant_id = ? AND user_id = ?",
                UUID.class, tenantId.getValue(), userId));
    }

    /** Throws 403 unless the signed-in person may work with this client. */
    public void require(TenantId tenantId, UUID clientId) {
        if (seesAll()) return;
        UUID me = currentUser();
        if (!ClientAccessRules.canAccess(false, assigned(tenantId, me), clientId))
            throw new HandyFlowException(ClientAccessRules.denied(), HttpStatus.FORBIDDEN, "CLIENT_NOT_ASSIGNED");
    }

    /** The items of clients the signed-in person may see. */
    public <T> List<T> visible(TenantId tenantId, List<T> items, Function<T, UUID> clientOf) {
        boolean all = seesAll();
        return ClientAccessRules.visible(all, all ? Set.of() : assigned(tenantId, currentUser()), items, clientOf);
    }

    /** The client a fee note belongs to, for endpoints that only name the fee note. */
    public UUID clientOfFeeNote(TenantId tenantId, UUID feeNoteId) {
        return first("SELECT client_id FROM acc_fee_notes WHERE tenant_id = ? AND id = ?", tenantId, feeNoteId);
    }

    public UUID clientOfTimeEntry(TenantId tenantId, UUID entryId) {
        return first("SELECT client_id FROM acc_time_entries WHERE tenant_id = ? AND id = ?", tenantId, entryId);
    }

    private UUID first(String sql, TenantId tenantId, UUID id) {
        List<UUID> r = jdbc.queryForList(sql, UUID.class, tenantId.getValue(), id);
        return r.isEmpty() ? null : r.get(0);
    }

    private static UUID currentUser() {
        try { return TenantContext.getCurrentUserId(); } catch (RuntimeException e) { return null; }
    }
}
