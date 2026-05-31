package za.co.handyflow.platform.ap.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.ap.domain.model.ApBatchItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApBatchItemRepository extends JpaRepository<ApBatchItem, UUID> {

    List<ApBatchItem> findByBatchId(UUID batchId);

    Optional<ApBatchItem> findByBatchIdAndBillId(UUID batchId, UUID billId);

    boolean existsByBillId(UUID billId);

    @Transactional
    void deleteByBatchId(UUID batchId);
}
