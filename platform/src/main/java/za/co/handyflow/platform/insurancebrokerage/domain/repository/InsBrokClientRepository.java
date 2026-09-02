package za.co.handyflow.platform.insurancebrokerage.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.insurancebrokerage.domain.model.InsBrokClient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InsBrokClientRepository extends JpaRepository<InsBrokClient, UUID> {

    @Query("SELECT c FROM InsBrokClient c WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL ORDER BY c.clientName ASC")
    Page<InsBrokClient> findAllActive(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("SELECT c FROM InsBrokClient c WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL ORDER BY c.clientName ASC")
    List<InsBrokClient> findAllActiveList(@Param("tenantId") UUID tenantId);

    @Query("SELECT c FROM InsBrokClient c WHERE c.tenantId = :tenantId AND c.id = :id AND c.deletedAt IS NULL")
    Optional<InsBrokClient> findActiveById(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
