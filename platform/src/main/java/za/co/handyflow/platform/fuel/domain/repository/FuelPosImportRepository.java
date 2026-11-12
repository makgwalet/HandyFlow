package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelPosImport;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelPosImportRepository extends JpaRepository<FuelPosImport, UUID> {

    @Query("SELECT i FROM FuelPosImport i WHERE i.tenantId = :tenantId AND i.source = :source AND i.externalId = :externalId")
    Optional<FuelPosImport> findByRef(TenantId tenantId, String source, String externalId);

    @Query("SELECT i FROM FuelPosImport i WHERE i.tenantId = :tenantId AND i.id = :id")
    Optional<FuelPosImport> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT i FROM FuelPosImport i WHERE i.tenantId = :tenantId AND (:status IS NULL OR i.status = :status) ORDER BY i.receivedAt DESC")
    List<FuelPosImport> findByStatus(TenantId tenantId, String status, Pageable page);
}
