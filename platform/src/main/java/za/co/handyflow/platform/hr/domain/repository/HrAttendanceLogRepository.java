package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrAttendanceLog;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface HrAttendanceLogRepository extends JpaRepository<HrAttendanceLog, UUID> {

    @Query("SELECT l FROM HrAttendanceLog l WHERE l.tenantId = :#{#tenantId.value} AND l.entryId IN :entryIds ORDER BY l.doneAt")
    List<HrAttendanceLog> forEntries(TenantId tenantId, Collection<UUID> entryIds);
}
