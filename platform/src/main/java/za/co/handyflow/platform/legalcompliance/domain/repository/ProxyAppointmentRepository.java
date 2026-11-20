package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.ProxyAppointment;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProxyAppointmentRepository extends JpaRepository<ProxyAppointment, UUID> {

    @Query("SELECT x FROM ProxyAppointment x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<ProxyAppointment> findForTenant(TenantId tenantId, UUID id);

    /** All proxies for a meeting, live and revoked, oldest first. */
    @Query("SELECT x FROM ProxyAppointment x WHERE x.tenantId = :tenantId AND x.meetingId = :meetingId ORDER BY x.createdAt")
    List<ProxyAppointment> findForMeeting(TenantId tenantId, UUID meetingId);
}
