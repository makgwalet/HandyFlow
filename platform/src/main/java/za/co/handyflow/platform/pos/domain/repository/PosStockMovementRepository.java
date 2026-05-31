package za.co.handyflow.platform.pos.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.pos.domain.model.PosStockMovement;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface PosStockMovementRepository extends JpaRepository<PosStockMovement, UUID> {
    List<PosStockMovement> findByStockItemIdOrderByCreatedAtDesc(UUID stockItemId);
}
