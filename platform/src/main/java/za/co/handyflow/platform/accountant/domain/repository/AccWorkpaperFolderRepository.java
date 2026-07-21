package za.co.handyflow.platform.accountant.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import za.co.handyflow.platform.accountant.domain.model.AccWorkpaperFolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccWorkpaperFolderRepository extends JpaRepository<AccWorkpaperFolder, UUID> {

    @Query("""
        SELECT f FROM AccountantWorkpaperFolder f
        WHERE f.tenantId = :tenantId AND f.clientId = :clientId
        ORDER BY f.sortOrder ASC, f.name ASC
    """)
    List<AccWorkpaperFolder> findByTenantIdAndClientId(@Param("tenantId") UUID tenantId,
                                                       @Param("clientId") UUID clientId);

    @Query("SELECT f FROM AccountantWorkpaperFolder f WHERE f.tenantId = :tenantId AND f.id = :id")
    Optional<AccWorkpaperFolder> findByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}