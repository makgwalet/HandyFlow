package za.co.handyflow.platform.accounting.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.accounting.application.internal.AccountingService;
import za.co.handyflow.platform.accounting.dto.CreateJournalEntryRequest;
import za.co.handyflow.platform.accounting.dto.JournalEntryResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Public entry point for other modules that need to post real accounting
 * journal entries — matches the same pattern AccountingService itself
 * already relies on for its own cross-module calls (it injects a
 * CrmFacade rather than reaching into CRM's internal package directly).
 * <p>
 * WHY THIS EXISTS: before this class, there was no public way to create a
 * proper DRAFT->POSTED journal entry from outside the accounting module.
 * AP's ApService worked around that by hand-rolling raw JDBC INSERTs
 * directly into acc_journal_entries/acc_journal_lines, as 'POSTED', with
 * no review step and its own separate (now-redundant) number generator —
 * bypassing every validation AccountingService.createJournalEntry()
 * already does: balance checking, positive-amount checking, minimum
 * line count, and correct entry numbering via the real
 * JournalNumberGenerator. This facade is the fix — a thin pass-through,
 * not a reimplementation. All the actual logic and validation stays in
 * AccountingService; this class exists purely to expose it safely across
 * the module boundary.
 * <p>
 * Deliberately minimal — only what AP actually needs (create + post).
 * reverseJournalEntry() is not exposed here since nothing outside
 * Accounting has needed it yet; add it the same way (thin delegation)
 * if and when something does.
 */
@Service
@RequiredArgsConstructor
public class AccountingFacade {

    private final AccountingService accountingService;

    /**
     * Creates a DRAFT journal entry — validated (balanced, minimum 2
     * lines, at least one debit and one credit, positive amounts) and
     * correctly numbered via the real JournalNumberGenerator, exactly as
     * every other journal entry in the system is. Does NOT post it —
     * call postJournalEntry() separately once ready, which is what
     * actually locks it and makes it appear in reports.
     */
    public JournalEntryResponse createJournalEntry(TenantId tenantId, CreateJournalEntryRequest req) {
        return accountingService.createJournalEntry(tenantId, req);
    }

    /**
     * Posts a DRAFT journal entry — locks it, makes it real. Throws
     * IllegalStateException if the entry isn't currently DRAFT (e.g.
     * already posted).
     */
    public JournalEntryResponse postJournalEntry(TenantId tenantId, UUID id) {
        return accountingService.postJournalEntry(tenantId, id);
    }
}