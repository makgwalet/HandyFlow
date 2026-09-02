package za.co.handyflow.platform.insurancebrokerage.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.insurancebrokerage.domain.model.InsBrokInsurer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InsBrokInsurerRepository extends JpaRepository<InsBrokInsurer, UUID> {

    @Query("SELECT i FROM InsBrokInsurer i WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL ORDER BY i.name ASC")
    Page<InsBrokInsurer> findAllActive(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("SELECT i FROM InsBrokInsurer i WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL ORDER BY i.name ASC")
    List<InsBrokInsurer> findAllActiveList(@Param("tenantId") UUID tenantId);

    @Query("SELECT i FROM InsBrokInsurer i WHERE i.tenantId = :tenantId AND i.id = :id AND i.deletedAt IS NULL")
    Optional<InsBrokInsurer> findActiveById(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
