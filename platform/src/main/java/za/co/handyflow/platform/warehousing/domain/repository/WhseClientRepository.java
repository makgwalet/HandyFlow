package za.co.handyflow.platform.warehousing.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.warehousing.domain.model.WhseClient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WhseClientRepository extends JpaRepository<WhseClient, UUID> {

    @Query("SELECT c FROM WhseClient c WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL ORDER BY c.tradingName ASC")
    Page<WhseClient> findAllActive(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("SELECT c FROM WhseClient c WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL ORDER BY c.tradingName ASC")
    List<WhseClient> findAllActiveList(@Param("tenantId") UUID tenantId);

    @Query("SELECT c FROM WhseClient c WHERE c.tenantId = :tenantId AND c.id = :id AND c.deletedAt IS NULL")
    Optional<WhseClient> findActiveById(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
