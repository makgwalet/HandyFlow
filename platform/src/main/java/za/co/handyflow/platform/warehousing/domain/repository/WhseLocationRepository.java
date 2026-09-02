package za.co.handyflow.platform.warehousing.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.warehousing.domain.model.WhseLocation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WhseLocationRepository extends JpaRepository<WhseLocation, UUID> {

    @Query("SELECT l FROM WhseLocation l WHERE l.tenantId = :tenantId AND l.deletedAt IS NULL ORDER BY l.code ASC")
    List<WhseLocation> findAllActive(@Param("tenantId") UUID tenantId);

    @Query("SELECT l FROM WhseLocation l WHERE l.tenantId = :tenantId AND l.id = :id AND l.deletedAt IS NULL")
    Optional<WhseLocation> findActiveById(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT l FROM WhseLocation l WHERE l.tenantId = :tenantId AND l.code = :code AND l.deletedAt IS NULL")
    Optional<WhseLocation> findActiveByCode(@Param("tenantId") UUID tenantId, @Param("code") String code);
}
