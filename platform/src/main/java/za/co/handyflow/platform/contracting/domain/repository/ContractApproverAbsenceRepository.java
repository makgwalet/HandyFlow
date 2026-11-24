package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.contracting.domain.model.ContractApproverAbsence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractApproverAbsenceRepository extends JpaRepository<ContractApproverAbsence, UUID> {

    /** Absences that cover the day (cancelled ones are left out). */
    @Query("SELECT a FROM ContractApproverAbsence a WHERE a.tenantId = :tenantId AND a.cancelledAt IS NULL AND a.fromDate <= :day AND a.toDate >= :day")
    List<ContractApproverAbsence> findActiveOn(@Param("tenantId") UUID tenantId, @Param("day") LocalDate day);

    /** Absences that are under way or still to come. */
    @Query("SELECT a FROM ContractApproverAbsence a WHERE a.tenantId = :tenantId AND a.cancelledAt IS NULL AND a.toDate >= :day ORDER BY a.fromDate, a.createdAt")
    List<ContractApproverAbsence> findCurrentAndUpcoming(@Param("tenantId") UUID tenantId, @Param("day") LocalDate day);

    @Query("SELECT a FROM ContractApproverAbsence a WHERE a.tenantId = :tenantId AND a.id = :id")
    Optional<ContractApproverAbsence> findOne(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
