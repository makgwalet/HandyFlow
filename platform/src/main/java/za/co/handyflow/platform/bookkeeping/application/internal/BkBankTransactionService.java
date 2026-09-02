package za.co.handyflow.platform.bookkeeping.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.bookkeeping.domain.model.BkBankAccount;
import za.co.handyflow.platform.bookkeeping.domain.model.BkBankTransaction;
import za.co.handyflow.platform.bookkeeping.domain.model.BkJournalEntry;
import za.co.handyflow.platform.bookkeeping.domain.model.BkJournalLine;
import za.co.handyflow.platform.bookkeeping.domain.repository.BkBankAccountRepository;
import za.co.handyflow.platform.bookkeeping.domain.repository.BkBankTransactionRepository;
import za.co.handyflow.platform.bookkeeping.domain.repository.BkJournalEntryRepository;
import za.co.handyflow.platform.bookkeeping.domain.repository.BkJournalLineRepository;
import za.co.handyflow.platform.bookkeeping.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A client's own bank-feed import and reconciliation — mirrors {@code
 * accounting.AccountingService}'s own {@code importBankTransactions} /
 * {@code getMatchCandidates} / {@code reconcileTransaction}/{@code
 * reconcileWithNewJournal} shape (per {@code BkBankTransaction}'s own
 * Javadoc), scoped additionally by {@code clientId}.
 * <p>
 * KNOWN SIMPLIFICATION, FLAGGED NOT SILENTLY GUESSED: the real {@code
 * AccountingService} source could not be read directly this session (the
 * {@code accounting} module isn't checked out in this sandbox) — this is
 * a faithful reproduction of the behaviour described in the build brief
 * (base64-decode, skip header row, parse Date/Description/Reference/Amount,
 * signed amount -> transactionType + always-positive amount, running
 * balance carried forward from the bank account's own current balance,
 * duplicate-skip-not-error, ±30-day exact-match-first candidate search),
 * not a byte-for-byte port of code that was never actually read.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BkBankTransactionService {

    private static final int MATCH_WINDOW_DAYS = 30;

    private final BkBankTransactionRepository transactionRepository;
    private final BkBankAccountRepository bankAccountRepository;
    private final BkJournalEntryRepository journalEntryRepository;
    private final BkJournalLineRepository journalLineRepository;
    private final BkJournalService journalService;

    @Transactional(readOnly = true)
    public Page<BkBankTransactionResponse> getTransactions(TenantId tenantId, UUID clientId, UUID bankAccountId, Pageable pageable) {
        if (bankAccountId != null) {
            return transactionRepository.findByBankAccount(tenantId, clientId, bankAccountId, pageable).map(this::toResponse);
        }
        return transactionRepository.findAllForClient(tenantId, clientId, pageable).map(this::toResponse);
    }

    /**
     * CSV import: base64-decode, skip the header row, parse {@code
     * Date,Description,Reference,Amount} columns. A signed amount becomes
     * an always-positive {@code amount} plus a derived {@code
     * transactionType} (positive -> CREDIT/money in, negative ->
     * DEBIT/money out). The running balance is carried forward from the
     * bank account's own current balance, in CSV row order, and the bank
     * account's balance is updated to the final running total. A row
     * that exactly duplicates an already-imported one (same date, amount,
     * description on this bank account) is silently skipped, not an
     * import error — matching {@code AccBankTransaction}'s own confirmed
     * duplicate-skip-not-error semantics.
     */
    @Transactional
    public ImportBkTransactionsResponse importTransactions(TenantId tenantId, UUID clientId, ImportBkTransactionsRequest req) {
        BkBankAccount bankAccount = findActiveBankAccount(tenantId, clientId, req.bankAccountId());

        String csv;
        try {
            csv = new String(Base64.getDecoder().decode(req.csvBase64()));
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException("csvBase64 is not valid base64", HttpStatus.BAD_REQUEST, "INVALID_CSV_ENCODING");
        }

        String[] rows = csv.split("\\r?\\n");
        int totalRows = 0, imported = 0, skippedDuplicates = 0;
        BigDecimal runningBalance = bankAccount.getCurrentBalance();

        for (int i = 1; i < rows.length; i++) { // skip header row
            String row = rows[i].trim();
            if (row.isEmpty()) continue;
            totalRows++;

            String[] cols = row.split(",", -1);
            if (cols.length < 4) {
                log.warn("Skipping malformed bank import row (expected Date,Description,Reference,Amount): {}", row);
                continue;
            }

            LocalDate transactionDate = LocalDate.parse(cols[0].trim());
            String description = cols[1].trim();
            String reference = cols[2].trim();
            BigDecimal signedAmount = new BigDecimal(cols[3].trim());

            String transactionType = signedAmount.signum() >= 0 ? "CREDIT" : "DEBIT";
            BigDecimal amount = signedAmount.abs().setScale(2, RoundingMode.HALF_UP);

            if (transactionRepository.existsDuplicate(tenantId, clientId, bankAccount.getId(), transactionDate, amount, description)) {
                skippedDuplicates++;
                continue;
            }

            runningBalance = "CREDIT".equals(transactionType) ? runningBalance.add(amount) : runningBalance.subtract(amount);

            BkBankTransaction transaction = BkBankTransaction.create(tenantId, clientId, bankAccount.getId(),
                    transactionDate, description, reference, amount, transactionType, runningBalance);
            transactionRepository.save(transaction);
            imported++;
        }

        bankAccount.updateBalance(runningBalance);
        bankAccountRepository.save(bankAccount);

        log.info("Bank transactions imported client={} tenant={} bankAccount={} imported={} skippedDuplicates={} totalRows={}",
                clientId, tenantId.getValue(), bankAccount.getId(), imported, skippedDuplicates, totalRows);
        return new ImportBkTransactionsResponse(imported, skippedDuplicates, totalRows);
    }

    /**
     * ±30-day, exact-match-first candidate search: scans this client's
     * POSTED journal entries within the window, looks at each entry's own
     * line(s) posted against the bank account's linked {@code BkAccount},
     * excludes lines already linked to some other bank transaction, and
     * flags a candidate {@code exactMatch} when both the amount and the
     * entry date match the transaction exactly.
     */
    @Transactional(readOnly = true)
    public List<MatchCandidateResponse> getMatchCandidates(TenantId tenantId, UUID clientId, UUID transactionId) {
        BkBankTransaction transaction = transactionRepository.findById(transactionId)
                .filter(t -> t.getTenantId().getValue().equals(tenantId.getValue()) && t.getClientId().equals(clientId))
                .orElseThrow(() -> new ResourceNotFoundException("BkBankTransaction", transactionId.toString()));

        BkBankAccount bankAccount = findActiveBankAccount(tenantId, clientId, transaction.getBankAccountId());
        if (bankAccount.getAccountId() == null) {
            return List.of(); // not yet linked to a chart-of-accounts line — nothing to match against
        }

        LocalDate from = transaction.getTransactionDate().minusDays(MATCH_WINDOW_DAYS);
        LocalDate to = transaction.getTransactionDate().plusDays(MATCH_WINDOW_DAYS);
        List<BkJournalEntry> candidateEntries = journalEntryRepository.findPostedInRangeForClient(tenantId, clientId, from, to);
        Set<UUID> linkedLineIds = Set.copyOf(transactionRepository.findLinkedJournalLineIds(tenantId, clientId));

        boolean expectDebit = "CREDIT".equals(transaction.getTransactionType()); // bank inflow -> debit to the bank's own GL account

        List<MatchCandidateResponse> candidates = new ArrayList<>();
        for (BkJournalEntry entry : candidateEntries) {
            for (BkJournalLine line : entry.getLines()) {
                if (!line.getAccountId().equals(bankAccount.getAccountId())) continue;
                if (linkedLineIds.contains(line.getId())) continue;

                BigDecimal lineAmount = expectDebit ? line.getDebitAmount() : line.getCreditAmount();
                if (lineAmount == null || lineAmount.signum() == 0) continue;

                boolean exactMatch = lineAmount.compareTo(transaction.getAmount()) == 0
                        && entry.getEntryDate().equals(transaction.getTransactionDate());

                candidates.add(new MatchCandidateResponse(line.getId(), entry.getId(), entry.getEntryNumber(),
                        entry.getEntryDate(), entry.getDescription(), lineAmount, exactMatch));
            }
        }

        return candidates.stream()
                .sorted(Comparator.comparing(MatchCandidateResponse::exactMatch).reversed()
                        .thenComparing(c -> Math.abs(c.entryDate().toEpochDay() - transaction.getTransactionDate().toEpochDay())))
                .toList();
    }

    @Transactional
    public BkBankTransactionResponse reconcileTransaction(TenantId tenantId, UUID clientId, UUID transactionId, UUID journalLineId) {
        BkBankTransaction transaction = findOwnedTransaction(tenantId, clientId, transactionId);
        journalLineRepository.findByIdForClient(tenantId, clientId, journalLineId)
                .orElseThrow(() -> new ResourceNotFoundException("BkJournalLine", journalLineId.toString()));

        transaction.reconcile(journalLineId);
        transactionRepository.save(transaction);
        log.info("Bank transaction reconciled id={} against journalLine={} client={} tenant={}",
                transactionId, journalLineId, clientId, tenantId.getValue());
        return toResponse(transaction);
    }

    /**
     * Reconciles by creating a brand-new balanced 2-line journal entry on
     * the fly (the bank-linked {@code BkAccount} on one side, the caller-
     * supplied contra account on the other) via {@link BkJournalService},
     * posts it immediately, and links the transaction to the resulting
     * line — for a bank movement with no existing matching entry yet.
     */
    @Transactional
    public BkBankTransactionResponse reconcileWithNewJournal(TenantId tenantId, UUID clientId, UUID transactionId,
                                                               UUID createdBy, ReconcileBkTransactionWithNewJournalRequest req) {
        BkBankTransaction transaction = findOwnedTransaction(tenantId, clientId, transactionId);
        BkBankAccount bankAccount = findActiveBankAccount(tenantId, clientId, transaction.getBankAccountId());
        if (bankAccount.getAccountId() == null) {
            throw new IllegalStateException("This bank account isn't linked to a chart-of-accounts line yet");
        }

        String description = req.description() != null ? req.description() : transaction.getDescription();
        boolean isCredit = "CREDIT".equals(transaction.getTransactionType()); // money in -> debit the bank account

        CreateBkJournalEntryRequest.JournalLineRequest bankLine = new CreateBkJournalEntryRequest.JournalLineRequest(
                bankAccount.getAccountId(), description, isCredit ? transaction.getAmount() : null, isCredit ? null : transaction.getAmount());
        CreateBkJournalEntryRequest.JournalLineRequest contraLine = new CreateBkJournalEntryRequest.JournalLineRequest(
                req.contraAccountId(), description, isCredit ? null : transaction.getAmount(), isCredit ? transaction.getAmount() : null);

        CreateBkJournalEntryRequest journalReq = new CreateBkJournalEntryRequest(clientId, transaction.getTransactionDate(),
                description, transaction.getReference(), "MANUAL", List.of(bankLine, contraLine));

        BkJournalEntryResponse created = journalService.createJournal(tenantId, createdBy, journalReq);
        journalService.postJournalEntry(tenantId, created.id());

        UUID newBankLineId = created.lines().stream()
                .filter(l -> l.accountId().equals(bankAccount.getAccountId()))
                .findFirst()
                .map(BkJournalEntryResponse.JournalLineResponse::id)
                .orElseThrow(() -> new IllegalStateException("Failed to resolve the newly-created bank-side journal line"));

        transaction.reconcile(newBankLineId);
        transactionRepository.save(transaction);
        log.info("Bank transaction reconciled with new journal id={} entry={} client={} tenant={}",
                transactionId, created.entryNumber(), clientId, tenantId.getValue());
        return toResponse(transaction);
    }

    private BkBankTransaction findOwnedTransaction(TenantId tenantId, UUID clientId, UUID transactionId) {
        return transactionRepository.findById(transactionId)
                .filter(t -> t.getTenantId().getValue().equals(tenantId.getValue()) && t.getClientId().equals(clientId))
                .orElseThrow(() -> new ResourceNotFoundException("BkBankTransaction", transactionId.toString()));
    }

    private BkBankAccount findActiveBankAccount(TenantId tenantId, UUID clientId, UUID bankAccountId) {
        return bankAccountRepository.findActiveById(tenantId, bankAccountId)
                .filter(b -> b.getClientId().equals(clientId))
                .orElseThrow(() -> new ResourceNotFoundException("BkBankAccount", bankAccountId.toString()));
    }

    private BkBankTransactionResponse toResponse(BkBankTransaction t) {
        return new BkBankTransactionResponse(t.getId(), t.getClientId(), t.getBankAccountId(), t.getTransactionDate(),
                t.getDescription(), t.getReference(), t.getAmount(), t.getTransactionType(), t.getBalanceAfter(),
                t.isReconciled(), t.getReconciledAt(), t.getJournalLineId(), t.getCreatedAt());
    }
}
