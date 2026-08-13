package za.co.handyflow.platform.bookingagency.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.bookingagency.domain.model.BookAgencyClient;

import java.util.Optional;
import java.util.UUID;

public interface BookAgencyClientRepository extends JpaRepository<BookAgencyClient, UUID> {

    @Query("""
        SELECT c FROM BookAgencyClient c
        WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL
        ORDER BY c.tradingName ASC
    """)
    Page<BookAgencyClient> findAllActive(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("""
        SELECT c FROM BookAgencyClient c
        WHERE c.tenantId = :tenantId AND c.id = :id AND c.deletedAt IS NULL
    """)
    Optional<BookAgencyClient> findActiveById(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}