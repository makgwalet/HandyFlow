package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplier;

import java.time.LocalDate;
import java.util.UUID;

/** Per-tenant COUNTS of active suppliers whose B-BBEE certificate has expired or is about to. Kept apart from ScSupplierRepository so that file is untouched. */
public interface ScSupplierComplianceCounts extends Repository<ScSupplier, UUID> {

    String ACTIVE = "s.tenantId = :tenantId AND s.deletedAt IS NULL AND s.status = za.co.handyflow.platform.supplychain.domain.enums.SupplierStatus.ACTIVE AND s.bbbeeExpiry IS NOT NULL";

    @Query("SELECT COUNT(s) FROM ScSupplier s WHERE " + ACTIVE + " AND s.bbbeeExpiry < :today")
    long bbbeeExpired(@Param("tenantId") UUID tenantId, @Param("today") LocalDate today);

    @Query("SELECT COUNT(s) FROM ScSupplier s WHERE " + ACTIVE + " AND s.bbbeeExpiry >= :today AND s.bbbeeExpiry <= :limit")
    long bbbeeExpiring(@Param("tenantId") UUID tenantId, @Param("today") LocalDate today, @Param("limit") LocalDate limit);
}
