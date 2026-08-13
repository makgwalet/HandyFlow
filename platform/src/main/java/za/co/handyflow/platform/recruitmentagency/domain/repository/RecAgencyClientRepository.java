package za.co.handyflow.platform.recruitmentagency.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.recruitmentagency.domain.model.RecAgencyClient;

import java.util.Optional;
import java.util.UUID;

public interface RecAgencyClientRepository extends JpaRepository<RecAgencyClient, UUID> {

    @Query("""
        SELECT c FROM RecAgencyClient c
        WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL
        ORDER BY c.tradingName ASC
    """)
    Page<RecAgencyClient> findAllActive(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("""
        SELECT c FROM RecAgencyClient c
        WHERE c.tenantId = :tenantId AND c.id = :id AND c.deletedAt IS NULL
    """)
    Optional<RecAgencyClient> findActiveById(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}