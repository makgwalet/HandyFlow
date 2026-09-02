package za.co.handyflow.platform.agriculture.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.agriculture.domain.model.AgSpecies;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface AgSpeciesRepository extends JpaRepository<AgSpecies, UUID> {

    @Query("SELECT s FROM AgSpecies s WHERE s.tenantId = :tenantId AND s.id = :id AND s.deletedAt IS NULL")
    Optional<AgSpecies> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT s FROM AgSpecies s WHERE s.tenantId = :tenantId AND s.deletedAt IS NULL ORDER BY s.name")
    Page<AgSpecies> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("SELECT s FROM AgSpecies s WHERE s.tenantId = :tenantId AND s.category = :category AND s.deletedAt IS NULL ORDER BY s.name")
    Page<AgSpecies> findAllActiveByCategory(TenantId tenantId, String category, Pageable pageable);
}
