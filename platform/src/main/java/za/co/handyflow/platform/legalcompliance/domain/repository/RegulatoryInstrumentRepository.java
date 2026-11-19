package za.co.handyflow.platform.legalcompliance.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.legalcompliance.domain.model.ObligationCategory;
import za.co.handyflow.platform.legalcompliance.domain.model.RegulatoryInstrument;

import java.util.Optional;
import java.util.UUID;

public interface RegulatoryInstrumentRepository extends JpaRepository<RegulatoryInstrument, UUID> {

    /** scope: ALL (platform + mine), PLATFORM, MINE. q is already lower-cased and wrapped in %…% ("%" = no filter). */
    @Query("""
        SELECT i FROM RegulatoryInstrument i
        WHERE i.active = true
          AND (i.tenantId IS NULL OR i.tenantId = :tid)
          AND (:scope = 'ALL' OR (:scope = 'PLATFORM' AND i.tenantId IS NULL) OR (:scope = 'MINE' AND i.tenantId = :tid))
          AND (:topic IS NULL OR i.topic = :topic)
          AND (lower(i.title) LIKE :q OR lower(i.identifier) LIKE :q)
        ORDER BY i.title
        """)
    Page<RegulatoryInstrument> search(@Param("tid") UUID tenantId, @Param("scope") String scope,
                                      @Param("topic") ObligationCategory topic, @Param("q") String q, Pageable pageable);

    /** Platform rows and the tenant's own rows only — never another tenant's. */
    @Query("SELECT i FROM RegulatoryInstrument i WHERE i.id = :id AND (i.tenantId IS NULL OR i.tenantId = :tid)")
    Optional<RegulatoryInstrument> findVisible(@Param("tid") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT i FROM RegulatoryInstrument i WHERE i.id = :id AND i.tenantId = :tid")
    Optional<RegulatoryInstrument> findOwned(@Param("tid") UUID tenantId, @Param("id") UUID id);

    /** Row lock that serialises version publishing for one instrument. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM RegulatoryInstrument i WHERE i.id = :id AND i.tenantId = :tid")
    Optional<RegulatoryInstrument> lockOwned(@Param("tid") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT i FROM RegulatoryInstrument i WHERE i.tenantId = :tid AND i.sourceInstrumentId = :sourceId")
    Optional<RegulatoryInstrument> findAdopted(@Param("tid") UUID tenantId, @Param("sourceId") UUID sourceId);

    @Query("SELECT i FROM RegulatoryInstrument i WHERE i.tenantId = :tid AND lower(i.title) = lower(:title)")
    Optional<RegulatoryInstrument> findByTitle(@Param("tid") UUID tenantId, @Param("title") String title);
}
