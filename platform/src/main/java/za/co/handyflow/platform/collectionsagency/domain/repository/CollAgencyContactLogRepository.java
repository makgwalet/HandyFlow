package za.co.handyflow.platform.collectionsagency.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyContactLog;

import java.util.List;
import java.util.UUID;

public interface CollAgencyContactLogRepository extends JpaRepository<CollAgencyContactLog, UUID> {

    @Query("SELECT l FROM CollAgencyContactLog l WHERE l.tenantId = :tenantId AND l.debtorAccountId = :debtorAccountId ORDER BY l.contactDate DESC, l.createdAt DESC")
    List<CollAgencyContactLog> findByDebtorAccount(@Param("tenantId") UUID tenantId, @Param("debtorAccountId") UUID debtorAccountId);
}
