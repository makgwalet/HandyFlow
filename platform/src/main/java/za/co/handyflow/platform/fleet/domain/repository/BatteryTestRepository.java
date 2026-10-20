package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.BatteryTest;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface BatteryTestRepository extends JpaRepository<BatteryTest, UUID> {

    @Query("SELECT t FROM BatteryTest t WHERE t.tenantId = :tenantId AND t.batteryId = :batteryId ORDER BY t.testedAt DESC")
    List<BatteryTest> findByBattery(TenantId tenantId, UUID batteryId, Pageable pageable);
}