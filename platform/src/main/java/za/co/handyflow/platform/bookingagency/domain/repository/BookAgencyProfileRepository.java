package za.co.handyflow.platform.bookingagency.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.bookingagency.domain.model.BookAgencyProfile;

import java.util.Optional;
import java.util.UUID;

public interface BookAgencyProfileRepository extends JpaRepository<BookAgencyProfile, UUID> {

    @Query("SELECT p FROM BookAgencyProfile p WHERE p.tenantId = :tenantId")
    Optional<BookAgencyProfile> findByTenantId(@Param("tenantId") UUID tenantId);
}