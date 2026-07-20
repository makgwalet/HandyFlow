package za.co.handyflow.platform.accountant.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import za.co.handyflow.platform.accountant.domain.model.AccJournalLine;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * NEW: closes the "trial balance" gap. AccJournalLine.journalId and
 * .accountId are plain UUID columns, not @ManyToOne relationships —
 * confirmed by reading the entity directly. JPQL's implicit-join syntax
 * only works through mapped associations, so a JPQL join across
 * AccJournalLine/AccJournal/AccPeriod based on a raw UUID match isn't
 * possible here. Native SQL is used instead for the two aggregate
 * queries below — an established pattern already in this module
 * (FeeNoteNumberGenerator, AccountantScheduler.lookupFirmEmail,
 * DeadlineEngine.isPublicHoliday all use raw JdbcTemplate/native SQL),
 * not something foreign being introduced here.
 * <p>
 * CORRECTED: the journal tables are prac_journals/prac_journal_lines
 * (see AccJournal.java/AccJournalLine.java's own @Table annotations),
 * NOT acc_journals/acc_journal_lines. V58__accountant_module.sql does
 * define acc_journals/acc_journal_lines, but the real, actually-used
 * entities map to prac_journals/prac_journal_lines instead — confirmed
 * by real journal data working correctly throughout this session, and
 * confirmed acc_journals genuinely doesn't exist in the live database
 * via a real "relation acc_journals does not exist" error after
 * wrongly assuming the "acc_" prefix pattern from the other newly-
 * discovered tables (acc_periods, acc_coa_accounts, acc_fica_documents)
 * applied here too. It didn't — journals predate that naming
 * convention and were never migrated to it.
 */
@Repository
public interface AccJournalLineRepository extends JpaRepository<AccJournalLine, UUID> {

    /** Per-account debit/credit movement for POSTED journals in one specific period. */
    @Query(value = """
        SELECT jl.account_id AS accountId,
               COALESCE(SUM(jl.debit), 0)  AS totalDebit,
               COALESCE(SUM(jl.credit), 0) AS totalCredit
        FROM prac_journal_lines jl
        JOIN prac_journals j ON j.id = jl.journal_id
        WHERE j.client_id = :clientId
          AND j.status = 'POSTED'
          AND j.period_id = :periodId
        GROUP BY jl.account_id
        """, nativeQuery = true)
    List<AccountBalanceRow> sumByAccountForPeriod(@Param("clientId") UUID clientId,
                                                  @Param("periodId") UUID periodId);

    /**
     * Per-account cumulative debit/credit movement for POSTED journals
     * in every period strictly before the given year/month — the basis
     * for a trial balance's opening balance column. acc_periods has no
     * stored opening-balance field at all (confirmed by reading the
     * real table), so this is derived, not looked up.
     */
    @Query(value = """
        SELECT jl.account_id AS accountId,
               COALESCE(SUM(jl.debit), 0)  AS totalDebit,
               COALESCE(SUM(jl.credit), 0) AS totalCredit
        FROM prac_journal_lines jl
        JOIN prac_journals j ON j.id = jl.journal_id
        JOIN acc_periods p ON p.id = j.period_id
        WHERE j.client_id = :clientId
          AND j.status = 'POSTED'
          AND (p.period_year < :periodYear
               OR (p.period_year = :periodYear AND p.period_month < :periodMonth))
        GROUP BY jl.account_id
        """, nativeQuery = true)
    List<AccountBalanceRow> sumByAccountBeforePeriod(@Param("clientId") UUID clientId,
                                                     @Param("periodYear") int periodYear,
                                                     @Param("periodMonth") int periodMonth);

    interface AccountBalanceRow {
        UUID getAccountId();
        BigDecimal getTotalDebit();
        BigDecimal getTotalCredit();
    }
}