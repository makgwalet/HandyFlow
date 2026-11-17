package za.co.handyflow.platform.fuel.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopStayRequest;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelTruckStopStayRequestRepository extends JpaRepository<FuelTruckStopStayRequest, UUID> {

    @Query("SELECT r FROM FuelTruckStopStayRequest r WHERE r.tenantId = :tenantId AND r.accountId = :accountId ORDER BY r.createdAt DESC")
    List<FuelTruckStopStayRequest> findByAccount(TenantId tenantId, UUID accountId, Pageable page);

    @Query("SELECT r FROM FuelTruckStopStayRequest r WHERE r.tenantId = :tenantId AND (:status IS NULL OR r.status = :status) ORDER BY r.createdAt DESC")
    List<FuelTruckStopStayRequest> findByStatus(TenantId tenantId, String status, Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM FuelTruckStopStayRequest r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<FuelTruckStopStayRequest> findForUpdate(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(r) FROM FuelTruckStopStayRequest r WHERE r.tenantId = :tenantId AND r.accountId = :accountId AND r.status = 'PENDING'")
    long countPending(TenantId tenantId, UUID accountId);

    @Query("SELECT COUNT(r) FROM FuelTruckStopStayRequest r WHERE r.tenantId = :tenantId AND r.accountId = :accountId AND r.status = 'PENDING' "
            + "AND r.vehicleRegistration = :registration AND r.arrivalDate = :arrival AND r.departureDate = :departure")
    long countSamePending(TenantId tenantId, UUID accountId, String registration, LocalDate arrival, LocalDate departure);
}
