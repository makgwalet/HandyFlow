package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import za.co.handyflow.platform.fuel.domain.model.FuelEnquiry;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelEnquiryRepository extends JpaRepository<FuelEnquiry, UUID> {
    @Query("SELECT e FROM FuelEnquiry e WHERE e.tenantId = :tenantId ORDER BY e.createdAt DESC")
    List<FuelEnquiry> findRecent(TenantId tenantId, Pageable page);

    @Query("SELECT e FROM FuelEnquiry e WHERE e.tenantId = :tenantId AND e.status = :status ORDER BY e.createdAt DESC")
    List<FuelEnquiry> findByStatus(TenantId tenantId, String status, Pageable page);

    @Query("SELECT e FROM FuelEnquiry e WHERE e.tenantId = :tenantId AND e.id = :id")
    Optional<FuelEnquiry> findOwned(TenantId tenantId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM FuelEnquiry e WHERE e.tenantId = :tenantId AND e.id = :id")
    Optional<FuelEnquiry> findOwnedForUpdate(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(e) FROM FuelEnquiry e WHERE e.tenantId = :tenantId AND e.status = 'NEW'")
    long countNew(TenantId tenantId);
}
