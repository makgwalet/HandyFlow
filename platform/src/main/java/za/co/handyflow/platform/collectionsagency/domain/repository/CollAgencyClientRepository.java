package za.co.handyflow.platform.collectionsagency.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyClient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollAgencyClientRepository extends JpaRepository<CollAgencyClient, UUID> {

    @Query("SELECT c FROM CollAgencyClient c WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL ORDER BY c.tradingName ASC")
    Page<CollAgencyClient> findAllActive(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("SELECT c FROM CollAgencyClient c WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL ORDER BY c.tradingName ASC")
    List<CollAgencyClient> findAllActiveList(@Param("tenantId") UUID tenantId);

    @Query("SELECT c FROM CollAgencyClient c WHERE c.tenantId = :tenantId AND c.id = :id AND c.deletedAt IS NULL")
    Optional<CollAgencyClient> findActiveById(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
