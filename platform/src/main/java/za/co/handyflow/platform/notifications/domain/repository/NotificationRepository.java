package za.co.handyflow.platform.notifications.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.notifications.domain.model.Notification;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("SELECT n FROM Notification n WHERE n.tenantId = :tenantId AND n.recipientUserId = :userId " +
            "ORDER BY n.createdAt DESC")
    Page<Notification> findForUser(@Param("tenantId") TenantId tenantId,
                                   @Param("userId") UUID userId,
                                   Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.tenantId = :tenantId AND n.recipientUserId = :userId " +
            "AND n.readAt IS NULL ORDER BY n.createdAt DESC")
    Page<Notification> findUnreadForUser(@Param("tenantId") TenantId tenantId,
                                         @Param("userId") UUID userId,
                                         Pageable pageable);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.tenantId = :tenantId AND n.recipientUserId = :userId " +
            "AND n.readAt IS NULL")
    long countUnreadForUser(@Param("tenantId") TenantId tenantId, @Param("userId") UUID userId);

    @Query("SELECT n FROM Notification n WHERE n.tenantId = :tenantId AND n.id = :id AND n.recipientUserId = :userId")
    Optional<Notification> findByIdForUser(@Param("tenantId") TenantId tenantId,
                                           @Param("id") UUID id,
                                           @Param("userId") UUID userId);

    // Bulk "mark all read" — a single UPDATE beats loading N entities into the
    // persistence context just to flip one field on each.
    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now " +
            "WHERE n.tenantId = :tenantId AND n.recipientUserId = :userId AND n.readAt IS NULL")
    int markAllReadForUser(@Param("tenantId") TenantId tenantId,
                           @Param("userId") UUID userId,
                           @Param("now") Instant now);
}