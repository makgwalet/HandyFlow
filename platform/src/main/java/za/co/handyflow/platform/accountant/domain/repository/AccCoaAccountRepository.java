package za.co.handyflow.platform.accountant.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import za.co.handyflow.platform.accountant.domain.model.AccCoaAccount;

import java.util.List;
import java.util.UUID;

@Repository
public interface AccCoaAccountRepository extends JpaRepository<AccCoaAccount, UUID> {

    /** Every active account for a client, ordered for trial balance display. */
    @Query("""
        SELECT a FROM AccountantCoaAccount a
        WHERE a.clientId = :clientId
          AND a.active = true
        ORDER BY a.accountCode ASC
    """)
    List<AccCoaAccount> findActiveByClient(@Param("clientId") UUID clientId);

    /**
     * NEW: closes the "journal account-name resolution" gap —
     * toJournalResponse() previously hardcoded account code/name to
     * null with a "resolve via COA service if needed" comment. Bulk
     * lookup by ID list, not one query per line — a journal with many
     * lines shouldn't cost a query per line to display.
     */
    @Query("SELECT a FROM AccountantCoaAccount a WHERE a.id IN :ids")
    List<AccCoaAccount> findByIdIn(@Param("ids") List<UUID> ids);
}