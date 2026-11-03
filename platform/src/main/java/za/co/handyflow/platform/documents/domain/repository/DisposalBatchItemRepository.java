package za.co.handyflow.platform.documents.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.documents.domain.model.DisposalBatchItem;

import java.util.List;
import java.util.UUID;

public interface DisposalBatchItemRepository extends JpaRepository<DisposalBatchItem, DisposalBatchItem.Key> {

    @Query("SELECT i FROM DisposalBatchItem i WHERE i.batchId = :batchId")
    List<DisposalBatchItem> findForBatch(@Param("batchId") UUID batchId);
}
