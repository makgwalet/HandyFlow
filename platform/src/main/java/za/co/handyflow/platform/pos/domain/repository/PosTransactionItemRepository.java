package za.co.handyflow.platform.pos.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.pos.domain.model.PosTransactionItem;

import java.util.List;
import java.util.UUID;

public interface PosTransactionItemRepository extends JpaRepository<PosTransactionItem, UUID> {
    List<PosTransactionItem> findByTransactionId(UUID transactionId);
}
