package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.ProjectTransmittalRecipientItem;

import java.util.List;
import java.util.UUID;

public interface ProjectTransmittalRecipientItemRepository
        extends JpaRepository<ProjectTransmittalRecipientItem, ProjectTransmittalRecipientItem.Key> {

    @Query("SELECT x.itemId FROM ProjectTransmittalRecipientItem x WHERE x.recipientId = :recipientId")
    List<UUID> findItemIds(@Param("recipientId") UUID recipientId);
}
