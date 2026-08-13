package za.co.handyflow.platform.bookingagency.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.bookingagency.domain.model.BookAgencyOffering;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookAgencyOfferingRepository extends JpaRepository<BookAgencyOffering, UUID> {

    @Query("SELECT o FROM BookAgencyOffering o WHERE o.tenantId = :tenantId AND o.id = :id")
    Optional<BookAgencyOffering> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT o FROM BookAgencyOffering o WHERE o.clientId = :clientId AND o.active = true ORDER BY o.name ASC")
    List<BookAgencyOffering> findActiveByClient(@Param("clientId") UUID clientId);
}