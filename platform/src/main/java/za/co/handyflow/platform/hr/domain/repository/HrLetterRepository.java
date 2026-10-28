package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrLetter;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrLetterRepository extends JpaRepository<HrLetter, UUID> {

    @Query("SELECT l FROM HrLetter l WHERE l.tenantId = :#{#tenantId.value} AND l.id = :id")
    Optional<HrLetter> findByTenantAndId(TenantId tenantId, UUID id);

    /** Every letter of the company, newest first. Filters and who may see which are applied in the service. */
    @Query("SELECT l FROM HrLetter l WHERE l.tenantId = :#{#tenantId.value} ORDER BY l.createdAt DESC")
    List<HrLetter> forTenant(TenantId tenantId);

    @Query("SELECT l FROM HrLetter l WHERE l.tenantId = :#{#tenantId.value} AND l.employeeId = :employeeId ORDER BY l.createdAt DESC")
    List<HrLetter> forEmployee(TenantId tenantId, UUID employeeId);

    /** Issued letters of every company that ask for acknowledgement and have not had it, for the daily reminders. */
    @Query("SELECT l FROM HrLetter l WHERE l.status = 'ISSUED' AND l.requiresAck = true AND l.acknowledgedAt IS NULL")
    List<HrLetter> awaitingAcknowledgement();
}
