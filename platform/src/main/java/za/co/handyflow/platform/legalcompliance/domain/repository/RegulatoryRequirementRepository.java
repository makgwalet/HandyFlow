package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.legalcompliance.domain.model.RegulatoryRequirement;
import za.co.handyflow.platform.legalcompliance.domain.model.VersionStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegulatoryRequirementRepository extends JpaRepository<RegulatoryRequirement, UUID> {

    @Query("SELECT r FROM RegulatoryRequirement r WHERE r.versionId = :versionId ORDER BY r.reference")
    List<RegulatoryRequirement> findByVersion(@Param("versionId") UUID versionId);

    @Query("SELECT count(r) FROM RegulatoryRequirement r WHERE r.versionId = :versionId")
    long countByVersion(@Param("versionId") UUID versionId);

    @Query("SELECT count(r) > 0 FROM RegulatoryRequirement r WHERE r.versionId = :versionId AND lower(r.reference) = lower(:reference) AND r.id <> :excludeId")
    boolean referenceTaken(@Param("versionId") UUID versionId, @Param("reference") String reference, @Param("excludeId") UUID excludeId);

    @Query("SELECT r FROM RegulatoryRequirement r WHERE r.id = :id AND (r.tenantId IS NULL OR r.tenantId = :tid)")
    Optional<RegulatoryRequirement> findVisible(@Param("tid") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT r FROM RegulatoryRequirement r WHERE r.id = :id AND r.tenantId = :tid")
    Optional<RegulatoryRequirement> findOwned(@Param("tid") UUID tenantId, @Param("id") UUID id);

    /** Every requirement, visible to this tenant, that sits in a CURRENT version of an active instrument. */
    @Query("""
        SELECT r FROM RegulatoryRequirement r, RegulatoryVersion v, RegulatoryInstrument i
        WHERE v.id = r.versionId AND i.id = r.instrumentId
          AND v.status = :current AND i.active = true
          AND (r.tenantId IS NULL OR r.tenantId = :tid)
        ORDER BY i.title, r.reference
        """)
    List<RegulatoryRequirement> findCurrentVisible(@Param("tid") UUID tenantId, @Param("current") VersionStatus current);
}
