package za.co.handyflow.platform.identity.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.identity.domain.model.TenantUiPreferences;

import java.util.UUID;

public interface TenantUiPreferencesRepository extends JpaRepository<TenantUiPreferences, UUID> {
}
