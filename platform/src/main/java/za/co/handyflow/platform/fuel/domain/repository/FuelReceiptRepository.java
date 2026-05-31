// fuel/domain/repository/FuelReceiptRepository.java

package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelReceipt;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

public interface FuelReceiptRepository extends JpaRepository<FuelReceipt, UUID> {

    @Query("SELECT r FROM FuelReceipt r WHERE r.tankId = :tankId AND r.deletedAt IS NULL ORDER BY r.receivedAt DESC")
    Page<FuelReceipt> findByTank(UUID tankId, Pageable pageable);

    @Query("SELECT r FROM FuelReceipt r WHERE r.tenantId = :tenantId AND r.deletedAt IS NULL ORDER BY r.receivedAt DESC")
    Page<FuelReceipt> findAllActive(TenantId tenantId, Pageable pageable);
}