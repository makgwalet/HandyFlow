package za.co.handyflow.platform.bookingagency.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.bookingagency.domain.model.BookAgencyResource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookAgencyResourceRepository extends JpaRepository<BookAgencyResource, UUID> {

    @Query("SELECT r FROM BookAgencyResource r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<BookAgencyResource> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT r FROM BookAgencyResource r WHERE r.clientId = :clientId AND r.active = true ORDER BY r.name ASC")
    List<BookAgencyResource> findActiveByClient(@Param("clientId") UUID clientId);
}