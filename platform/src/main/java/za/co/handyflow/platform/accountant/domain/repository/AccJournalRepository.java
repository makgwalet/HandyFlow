package za.co.handyflow.platform.accountant.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import za.co.handyflow.platform.accountant.domain.model.AccJournal;

import java.util.List;
import java.util.UUID;

@Repository
public interface AccJournalRepository extends JpaRepository<AccJournal, UUID> {

    @Query("""
        SELECT j FROM AccountantJournal j
        WHERE j.clientId = :clientId
        ORDER BY j.journalDate DESC, j.createdAt DESC
    """)
    Page<AccJournal> findByClient(@Param("clientId") UUID clientId, Pageable pageable);

    /** All posted journals for a period — used for trial balance computation. */
    @Query("""
        SELECT j FROM AccountantJournal j
        WHERE j.clientId = :clientId
          AND j.periodId = :periodId
          AND j.status   = 'POSTED'
        ORDER BY j.journalDate ASC
    """)
    List<AccJournal> findPostedByPeriod(@Param("clientId") UUID clientId,
                                        @Param("periodId") UUID periodId);

    /** Journals awaiting review or approval — for the preparer/reviewer dashboard. */
    @Query("""
        SELECT j FROM AccountantJournal j
        WHERE j.clientId = :clientId
          AND j.status IN ('DRAFT','PREPARED','REVIEWED')
        ORDER BY j.createdAt DESC
    """)
    List<AccJournal> findPendingApproval(@Param("clientId") UUID clientId);

    /** All posted journals for a client in a year — used for year-end close. */
    @Query("""
        SELECT j FROM AccountantJournal j
        WHERE j.clientId = :clientId
          AND j.status   = 'POSTED'
          AND j.journalDate >= :from
          AND j.journalDate <= :to
        ORDER BY j.journalDate ASC
    """)
    List<AccJournal> findPostedInRange(@Param("clientId") UUID clientId,
                                       @Param("from") java.time.LocalDate from,
                                       @Param("to")   java.time.LocalDate to);
}
