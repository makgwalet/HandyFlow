package za.co.handyflow.platform.identity.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.identity.domain.model.User;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    // ── Login queries ─────────────────────────────────────────────────────────

    // WHY include tenantId in every query?
    // Multi-tenancy enforcement at the DB layer.
    // Even if someone passes the wrong userId, they can't get
    // another tenant's user because tenantId won't match.
    Optional<User> findByEmailAndTenantId(String email, TenantId tenantId);

    boolean existsByEmailAndTenantId(String email, TenantId tenantId);

    // WHY this query? Login only needs email — we don't know tenantId yet.
    // We find by email alone, then validate the tenant separately.
    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmailAcrossTenants(String email);

    // ── User management queries ───────────────────────────────────────────────

    /** List all users in a tenant, sorted alphabetically. */
    List<User> findByTenantIdOrderByLastNameAscFirstNameAsc(TenantId tenantId);

    /** Get a specific user, scoped to the tenant — prevents cross-tenant access. */
    Optional<User> findByIdAndTenantId(UUID id, TenantId tenantId);

    /** Check if email is already taken in this tenant — used before sending invitation. */
    boolean existsByTenantIdAndEmail(TenantId tenantId, String email);

    /** Count users assigned to a specific role name in this tenant — used for role display. */
    @Query("""
        SELECT COUNT(u) FROM User u
        JOIN u.roles r
        WHERE u.tenantId = :tenantId
        AND r.name = :roleName
        """)
    int countByTenantIdAndRoleName(TenantId tenantId, String roleName);
}
