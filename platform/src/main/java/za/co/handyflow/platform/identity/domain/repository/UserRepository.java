package za.co.handyflow.platform.identity.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.identity.domain.model.User;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    // WHY include tenantId in every query?
    // This is our multi-tenancy enforcement at the DB layer.
    // Even if someone passes the wrong userId, they can't get
    // another tenant's user because tenantId won't match.
    Optional<User> findByEmailAndTenantId(String email, TenantId tenantId);

    boolean existsByEmailAndTenantId(String email, TenantId tenantId);

    // WHY this query? Login only needs email — we don't know tenantId yet.
    // We find by email alone, then validate the tenant separately.
    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmailAcrossTenants(String email);
}
