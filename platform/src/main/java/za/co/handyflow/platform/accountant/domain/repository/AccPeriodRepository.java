package za.co.handyflow.platform.accountant.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import za.co.handyflow.platform.accountant.domain.model.AccPeriod;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccPeriodRepository extends JpaRepository<AccPeriod, UUID> {

    /**
     * Resolves a client's period for a given year/month, if one exists.
     * A missing result is a valid state (no journals were ever posted
     * for that client in that period) — callers should treat it as
     * "zero activity", not as an error.
     */
    @Query("""
        SELECT p FROM AccountantPeriod p
        WHERE p.clientId = :clientId
          AND p.periodYear = :periodYear
          AND p.periodMonth = :periodMonth
    """)
    Optional<AccPeriod> findByClientAndYearMonth(@Param("clientId") UUID clientId,
                                                 @Param("periodYear") int periodYear,
                                                 @Param("periodMonth") int periodMonth);
}