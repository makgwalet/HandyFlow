package za.co.handyflow.platform.identity.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.identity.domain.model.Role;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    /** List all roles for a tenant — used in role management UI. */
    List<Role> findByTenantId(TenantId tenantId);

    /** Get a specific role scoped to the tenant — prevents cross-tenant role access. */
    Optional<Role> findByIdAndTenantId(UUID id, TenantId tenantId);

    /** Find role by name in a tenant — used for default role lookup (ADMIN, EMPLOYEE). */
    Optional<Role> findByNameAndTenantId(String name, TenantId tenantId);

    /** Check if a role name already exists in this tenant — used before creating a new role. */
    boolean existsByNameAndTenantId(String name, TenantId tenantId);
}
