package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.legalcompliance.domain.model.RegulatoryVersion;
import za.co.handyflow.platform.legalcompliance.domain.model.VersionStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegulatoryVersionRepository extends JpaRepository<RegulatoryVersion, UUID> {

    @Query("SELECT v FROM RegulatoryVersion v WHERE v.instrumentId = :instrumentId ORDER BY v.createdAt DESC")
    List<RegulatoryVersion> findByInstrument(@Param("instrumentId") UUID instrumentId);

    @Query("SELECT v FROM RegulatoryVersion v WHERE v.instrumentId = :instrumentId AND v.status = :status")
    Optional<RegulatoryVersion> findByInstrumentAndStatus(@Param("instrumentId") UUID instrumentId, @Param("status") VersionStatus status);

    @Query("SELECT v FROM RegulatoryVersion v WHERE v.id = :id AND (v.tenantId IS NULL OR v.tenantId = :tid)")
    Optional<RegulatoryVersion> findVisible(@Param("tid") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT v FROM RegulatoryVersion v WHERE v.id = :id AND v.tenantId = :tid")
    Optional<RegulatoryVersion> findOwned(@Param("tid") UUID tenantId, @Param("id") UUID id);
}
