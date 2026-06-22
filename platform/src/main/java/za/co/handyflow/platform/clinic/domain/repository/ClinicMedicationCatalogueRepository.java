package za.co.handyflow.platform.clinic.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.clinic.domain.model.ClinicMedicationCatalogue;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClinicMedicationCatalogueRepository extends JpaRepository<ClinicMedicationCatalogue, UUID> {

    @Query("""
        SELECT m FROM ClinicMedicationCatalogue m
        WHERE m.active = true
        AND (m.tenantId IS NULL OR m.tenantId = :#{#tenantId.value})
        AND (LOWER(m.genericName) LIKE LOWER(CONCAT('%', :search, '%'))
             OR LOWER(m.brandName)  LIKE LOWER(CONCAT('%', :search, '%'))
             OR m.nappiCode          LIKE CONCAT('%', :search, '%'))
        ORDER BY m.genericName
        """)
    List<ClinicMedicationCatalogue> search(TenantId tenantId, String search);

    @Query("""
        SELECT m FROM ClinicMedicationCatalogue m
        WHERE m.active = true
        AND (m.tenantId IS NULL OR m.tenantId = :#{#tenantId.value})
        ORDER BY m.genericName
        """)
    List<ClinicMedicationCatalogue> findAll(TenantId tenantId);

    /**
     * FIX #1 — Cross-tenant data leak: original query had no tenantId parameter.
     * ORDER BY tenantId DESC NULLS LAST returned whichever tenant's UUID sorted
     * higher — a random other practice's price override, not the calling tenant's.
     *
     * Fix: explicitly scope to (this tenant OR global). This ensures the calling
     * practice's override wins over global when one exists, and falls back to
     * global (tenantId IS NULL) when no tenant-specific override is present.
     * No other tenant's entry can ever match.
     */
    @Query("""
        SELECT m.singleExitPrice FROM ClinicMedicationCatalogue m
        WHERE m.nappiCode = :nappiCode
          AND m.active    = true
          AND (m.tenantId IS NULL OR m.tenantId = :#{#tenantId.value})
        ORDER BY m.tenantId DESC NULLS LAST
        LIMIT 1
        """)
    Optional<BigDecimal> findBySep(String nappiCode, TenantId tenantId);
}
