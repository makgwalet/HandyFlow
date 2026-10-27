package za.co.handyflow.platform.facilitiesmanagement.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmBusinessCalendar;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface FmBusinessCalendarRepository extends JpaRepository<FmBusinessCalendar, UUID> {

    @Query("SELECT c FROM FmBusinessCalendar c WHERE c.tenantId.value = :#{#tenantId.value}")
    Optional<FmBusinessCalendar> findByTenant(@Param("tenantId") TenantId tenantId);
}