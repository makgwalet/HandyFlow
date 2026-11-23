package za.co.handyflow.platform.agriculture.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.agriculture.domain.model.AgAnimalTagHistory;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface AgAnimalTagHistoryRepository extends JpaRepository<AgAnimalTagHistory, UUID> {

    @Query("SELECT h FROM AgAnimalTagHistory h WHERE h.tenantId = :tenantId AND h.animalId = :animalId ORDER BY h.changedAt DESC, h.id DESC")
    List<AgAnimalTagHistory> findForAnimal(TenantId tenantId, UUID animalId);
}
