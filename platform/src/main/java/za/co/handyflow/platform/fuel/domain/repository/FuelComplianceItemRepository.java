package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelComplianceItem;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelComplianceItemRepository extends JpaRepository<FuelComplianceItem, UUID> {

    @Query("SELECT i FROM FuelComplianceItem i WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL ORDER BY i.expiresOn")
    List<FuelComplianceItem> findAllActive(TenantId tenantId);

    @Query("SELECT i FROM FuelComplianceItem i WHERE i.tenantId = :tenantId AND i.id = :id AND i.deletedAt IS NULL")
    Optional<FuelComplianceItem> findActiveById(TenantId tenantId, UUID id);

    /** Items that have expired and still matter for a subject (or the company as a whole). */
    @Query("SELECT i FROM FuelComplianceItem i WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL AND i.expiresOn < :today AND ((i.subjectType = :subjectType AND i.subjectId = :subjectId) OR (i.subjectType = 'COMPANY' AND i.itemType = 'LICENCE'))")
    List<FuelComplianceItem> findExpiredFor(TenantId tenantId, String subjectType, UUID subjectId, LocalDate today);

    @Query("SELECT i FROM FuelComplianceItem i WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL AND i.expiresOn < :today AND i.subjectType = 'COMPANY' AND i.itemType = 'LICENCE'")
    List<FuelComplianceItem> findExpiredCompanyLicences(TenantId tenantId, LocalDate today);

    @Query("SELECT COUNT(i) FROM FuelComplianceItem i WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL AND i.expiresOn < :today")
    long countExpired(TenantId tenantId, LocalDate today);

    @Query("SELECT COUNT(i) FROM FuelComplianceItem i WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL AND i.expiresOn >= :today AND i.expiresOn <= :until")
    long countExpiring(TenantId tenantId, LocalDate today, LocalDate until);

    /** Every tenant's items whose expiry falls in [from, until]: the daily reminder sweep. */
    @Query("SELECT i FROM FuelComplianceItem i WHERE i.deletedAt IS NULL AND i.expiresOn >= :from AND i.expiresOn <= :until ORDER BY i.expiresOn")
    List<FuelComplianceItem> findExpiringBetweenAllTenants(LocalDate from, LocalDate until);
}
