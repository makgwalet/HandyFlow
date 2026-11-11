package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelComplianceReminder;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FuelComplianceReminderRepository extends JpaRepository<FuelComplianceReminder, UUID> {
    @Query("SELECT r FROM FuelComplianceReminder r WHERE r.itemId IN :itemIds")
    List<FuelComplianceReminder> findByItemIds(Collection<UUID> itemIds);
}
