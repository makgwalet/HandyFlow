package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.contracting.domain.model.ContractClosureDay;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractClosureDayRepository extends JpaRepository<ContractClosureDay, UUID> {

    @Query("SELECT c FROM ContractClosureDay c WHERE c.tenantId = :tenantId AND c.closedOn >= :from ORDER BY c.closedOn")
    List<ContractClosureDay> findFrom(@Param("tenantId") UUID tenantId, @Param("from") LocalDate from);

    @Query("SELECT c FROM ContractClosureDay c WHERE c.tenantId = :tenantId AND c.id = :id")
    Optional<ContractClosureDay> findOne(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT COUNT(c) > 0 FROM ContractClosureDay c WHERE c.tenantId = :tenantId AND c.closedOn = :day")
    boolean existsOn(@Param("tenantId") UUID tenantId, @Param("day") LocalDate day);

    @Query("SELECT c.closedOn FROM ContractClosureDay c WHERE c.tenantId = :tenantId AND c.closedOn BETWEEN :from AND :to")
    List<LocalDate> datesBetween(@Param("tenantId") UUID tenantId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
