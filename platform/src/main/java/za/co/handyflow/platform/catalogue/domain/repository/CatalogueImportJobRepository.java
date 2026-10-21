package za.co.handyflow.platform.catalogue.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueImportJob;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CatalogueImportJobRepository extends JpaRepository<CatalogueImportJob, UUID> {

    @Query("SELECT j FROM CatalogueImportJob j WHERE j.tenantId = :tenantId ORDER BY j.createdAt DESC")
    List<CatalogueImportJob> recent(TenantId tenantId, Pageable pageable);

    /** Imports since a date that skipped or failed at least one row. */
    @Query("SELECT COUNT(j) FROM CatalogueImportJob j WHERE j.tenantId = :tenantId AND j.createdAt >= :since AND (j.failed > 0 OR j.skipped > 0)")
    long countWithProblemsSince(TenantId tenantId, Instant since);
}
