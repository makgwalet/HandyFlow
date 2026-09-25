package za.co.handyflow.platform.identity.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.identity.domain.model.UserUiPreferences;

import java.util.Optional;
import java.util.UUID;

public interface UserUiPreferencesRepository extends JpaRepository<UserUiPreferences, UUID> {

    /** Tenant-scoped lookup. Never use findById for this entity. */
    Optional<UserUiPreferences> findByUserIdAndTenantId(UUID userId, UUID tenantId);
}
