package za.co.handyflow.platform.notifications.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.notifications.domain.model.NotificationPreference;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {

    @Query("SELECT p FROM NotificationPreference p WHERE p.tenantId = :tenantId AND p.userId = :userId")
    List<NotificationPreference> findAllForUser(@Param("tenantId") TenantId tenantId,
                                                @Param("userId") UUID userId);

    @Query("SELECT p FROM NotificationPreference p WHERE p.tenantId = :tenantId AND p.userId = :userId " +
            "AND p.channel = :channel")
    Optional<NotificationPreference> findForUserAndChannel(@Param("tenantId") TenantId tenantId,
                                                           @Param("userId") UUID userId,
                                                           @Param("channel") String channel);
}