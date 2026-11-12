package za.co.handyflow.platform.fuel.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelPrice;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelPriceRepository extends JpaRepository<FuelPrice, UUID> {

    @Query("SELECT p FROM FuelPrice p WHERE p.tenantId = :tenantId ORDER BY p.fuelType, p.accountId, p.minLitres, p.validFrom DESC")
    List<FuelPrice> findAllForTenant(TenantId tenantId);

    @Query("SELECT p FROM FuelPrice p WHERE p.tenantId = :tenantId AND p.id = :id")
    Optional<FuelPrice> findOwned(TenantId tenantId, UUID id);

    /** Standard-list prices live on a date. */
    @Query("SELECT p FROM FuelPrice p WHERE p.tenantId = :tenantId AND p.fuelType = :fuelType AND p.accountId IS NULL AND p.endedAt IS NULL AND p.approvalStatus = 'APPROVED' AND p.validFrom <= :on AND (p.validTo IS NULL OR p.validTo >= :on)")
    List<FuelPrice> liveStandard(TenantId tenantId, String fuelType, LocalDate on);

    /** Standard and this customer's contract prices live on a date. */
    @Query("SELECT p FROM FuelPrice p WHERE p.tenantId = :tenantId AND p.fuelType = :fuelType AND (p.accountId IS NULL OR p.accountId = :accountId) AND p.endedAt IS NULL AND p.approvalStatus = 'APPROVED' AND p.validFrom <= :on AND (p.validTo IS NULL OR p.validTo >= :on)")
    List<FuelPrice> liveForAccount(TenantId tenantId, String fuelType, UUID accountId, LocalDate on);

    /** Same list, product and volume break whose dates overlap [from, to] (to may be open-ended). */
    @Query("SELECT p FROM FuelPrice p WHERE p.tenantId = :tenantId AND p.fuelType = :fuelType AND p.accountId IS NULL AND p.minLitres = :minLitres AND p.endedAt IS NULL AND p.approvalStatus <> 'REJECTED' AND (p.validTo IS NULL OR p.validTo >= :from) AND (:to IS NULL OR p.validFrom <= :to)")
    List<FuelPrice> overlapStandard(TenantId tenantId, String fuelType, BigDecimal minLitres, LocalDate from, LocalDate to);

    @Query("SELECT p FROM FuelPrice p WHERE p.tenantId = :tenantId AND p.fuelType = :fuelType AND p.accountId = :accountId AND p.minLitres = :minLitres AND p.endedAt IS NULL AND p.approvalStatus <> 'REJECTED' AND (p.validTo IS NULL OR p.validTo >= :from) AND (:to IS NULL OR p.validFrom <= :to)")
    List<FuelPrice> overlapAccount(TenantId tenantId, String fuelType, UUID accountId, BigDecimal minLitres, LocalDate from, LocalDate to);

    /** The approved standard price this one would replace: same product and break, live on the start date. */
    @Query("SELECT p FROM FuelPrice p WHERE p.tenantId = :tenantId AND p.fuelType = :fuelType AND p.accountId IS NULL AND p.minLitres = :minLitres AND p.endedAt IS NULL AND p.approvalStatus = 'APPROVED' AND p.validFrom <= :on AND (p.validTo IS NULL OR p.validTo >= :on) ORDER BY p.validFrom DESC")
    List<FuelPrice> liveComparableStandard(TenantId tenantId, String fuelType, BigDecimal minLitres, LocalDate on);

    /** The same for one customer's contract price. */
    @Query("SELECT p FROM FuelPrice p WHERE p.tenantId = :tenantId AND p.fuelType = :fuelType AND p.accountId = :accountId AND p.minLitres = :minLitres AND p.endedAt IS NULL AND p.approvalStatus = 'APPROVED' AND p.validFrom <= :on AND (p.validTo IS NULL OR p.validTo >= :on) ORDER BY p.validFrom DESC")
    List<FuelPrice> liveComparableAccount(TenantId tenantId, String fuelType, UUID accountId, BigDecimal minLitres, LocalDate on);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM FuelPrice p WHERE p.tenantId = :tenantId AND p.id = :id")
    Optional<FuelPrice> findOwnedForUpdate(TenantId tenantId, UUID id);
}
