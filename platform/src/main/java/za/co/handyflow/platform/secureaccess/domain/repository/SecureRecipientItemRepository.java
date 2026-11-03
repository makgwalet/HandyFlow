package za.co.handyflow.platform.secureaccess.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.secureaccess.domain.model.SecureRecipientItem;

import java.util.List;
import java.util.UUID;

public interface SecureRecipientItemRepository
        extends JpaRepository<SecureRecipientItem, SecureRecipientItem.Key> {

    @Query("SELECT x.itemId FROM SecureRecipientItem x WHERE x.recipientId = :recipientId")
    List<UUID> findItemIds(@Param("recipientId") UUID recipientId);
}
