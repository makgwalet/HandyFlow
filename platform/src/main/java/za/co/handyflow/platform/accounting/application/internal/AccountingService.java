package za.co.handyflow.platform.accounting.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accounting.domain.model.*;
import za.co.handyflow.platform.accounting.domain.repository.*;
import za.co.handyflow.platform.accounting.dto.*;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.invoicing.domain.model.Invoice;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.accounting.dto.MonthlySummaryResponse;
import java.time.YearMonth;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountingService {

    private final AccAccountRepository         accountRepo;
    private final AccJournalEntryRepository    journalRepo;
    private final AccBankAccountRepository     bankAccountRepo;
    private final AccBankTransactionRepository bankTxRepo;
    private final AccVatPeriodRepository       vatPeriodRepo;
    private final ChartOfAccountsSeeder        coaSeeder;
    private final JournalNumberGenerator       numberGen;
    private final InvoicingFacade               invoicingFacade;
    private final CrmFacade                     crmFacade;
    private final EmailService                  emailService;

    // ── Chart of Accounts ─────────────────────────────────────────────────────

    @Transactional
    public List<AccountResponse> getAccounts(TenantId tenantId) {
        coaSeeder.seedForTenant(tenantId);
        return accountRepo.findAllActive(tenantId).stream().map(this::toAccountResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsByType(TenantId tenantId, String type) {
        return accountRepo.findByType(tenantId, type).stream().map(this::toAccountResponse).toList();
    }

    private static final Set<String> VALID_ACCOUNT_TYPES =
            Set.of("ASSET", "LIABILITY", "EQUITY", "INCOME", "EXPENSE");

    @Transactional
    public AccountResponse createAccount(TenantId tenantId, CreateAccountRequest req) {
        coaSeeder.seedForTenant(tenantId); // same "ensure seeded first" pattern createJournalEntry() already uses

        if (!VALID_ACCOUNT_TYPES.contains(req.accountType())) {
            throw new IllegalArgumentException(
                    "accountType must be one of " + VALID_ACCOUNT_TYPES);
        }
        boolean codeExists = accountRepo.findAllActive(tenantId).stream()
                .anyMatch(a -> a.getAccountCode().equals(req.accountCode()));
        if (codeExists) {
            throw new IllegalArgumentException("Account code '" + req.accountCode() + "' already exists");
        }

        // isSystem=false — distinguishes custom accounts from the 47
        // seeded ones, in case future logic ever needs to (e.g. blocking
        // edits/deletes on seeded accounts but allowing them on custom
        // ones — not built here, just kept possible).
        AccAccount account = AccAccount.create(tenantId, req.accountCode(), req.accountName(),
                req.accountType(), req.accountSubtype(), false, req.description());
        accountRepo.save(account);
        log.info("Created custom account={} code={} tenant={}", account.getId(), req.accountCode(), tenantId);
        return toAccountResponse(account);
    }

    // ── Journal Entries ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<JournalEntryResponse> getJournalEntries(TenantId tenantId,
                                                        String status, Pageable pageable) {
        Map<UUID, AccAccount> accountMap = accountRepo.findAllActive(tenantId)
                .stream().collect(Collectors.toMap(AccAccount::getId, a -> a));
        return journalRepo.findAllActive(tenantId, status, pageable)
                .map(e -> toJournalResponse(e, accountMap));
    }

    @Transactional
    public JournalEntryResponse createJournalEntry(TenantId tenantId,
                                                   CreateJournalEntryRequest req) {
        return createJournalEntry(tenantId, req, null);
    }

    // New overload — the only caller that should ever pass a real
    // createdBy is the controller's own POST /journal-entries endpoint
    // (the manual "New Journal Entry" flow). AccountingFacade and
    // reconcileWithNewJournal() keep calling the 2-arg version above
    // unchanged, so createdBy stays null for AP- and reconciliation-
    // triggered journals — correct, since those already went through
    // their own review elsewhere and don't need a second one here.
    @Transactional
    public JournalEntryResponse createJournalEntry(TenantId tenantId,
                                                   CreateJournalEntryRequest req,
                                                   UUID createdBy) {
        coaSeeder.seedForTenant(tenantId);

        if (req.lines() == null || req.lines().size() < 2)
            throw new IllegalArgumentException("Journal entry requires at least 2 lines");

        boolean hasDebit  = req.lines().stream().anyMatch(l ->
                l.debitAmount()  != null && l.debitAmount().compareTo(BigDecimal.ZERO)  > 0);
        boolean hasCredit = req.lines().stream().anyMatch(l ->
                l.creditAmount() != null && l.creditAmount().compareTo(BigDecimal.ZERO) > 0);
        if (!hasDebit || !hasCredit)
            throw new IllegalArgumentException(
                    "Journal entry must have at least one debit and one credit line");

        for (int li = 0; li < req.lines().size(); li++) {
            var lr = req.lines().get(li);
            BigDecimal d = lr.debitAmount()  != null ? lr.debitAmount()  : BigDecimal.ZERO;
            BigDecimal c = lr.creditAmount() != null ? lr.creditAmount() : BigDecimal.ZERO;
            if (d.compareTo(BigDecimal.ZERO) < 0 || c.compareTo(BigDecimal.ZERO) < 0)
                throw new IllegalArgumentException("Journal line amounts must be positive");
            // A line with BOTH a debit and a credit filled in used to
            // silently lose the credit value entirely — the line-building
            // logic below picks debit-only whenever debit > 0, discarding
            // whatever credit was also entered without any error. Caught
            // live: the frontend summed both columns independently and
            // said "Balanced" for an entry the backend then rejected for
            // a reason the UI never explained. Rejecting explicitly here,
            // before any data gets thrown away, instead of after.
            if (d.compareTo(BigDecimal.ZERO) > 0 && c.compareTo(BigDecimal.ZERO) > 0)
                throw new IllegalArgumentException(
                        "Line " + (li + 1) + " has both a debit and a credit amount — "
                                + "each line must be one or the other, not both");
        }

        String entryNumber = numberGen.next(tenantId);
        AccJournalEntry entry = AccJournalEntry.create(
                tenantId, entryNumber, req.entryDate(),
                req.description(), req.reference(), req.entryType(), createdBy);
        journalRepo.save(entry);

        int i = 0;
        for (var lr : req.lines()) {
            BigDecimal debit  = lr.debitAmount()  != null ? lr.debitAmount()  : BigDecimal.ZERO;
            BigDecimal credit = lr.creditAmount() != null ? lr.creditAmount() : BigDecimal.ZERO;
            AccJournalLine line = debit.compareTo(BigDecimal.ZERO) > 0
                    ? AccJournalLine.debit(tenantId.getValue(),  entry.getId(), lr.accountId(), debit,  lr.description(), i)
                    : AccJournalLine.credit(tenantId.getValue(), entry.getId(), lr.accountId(), credit, lr.description(), i);
            entry.addLine(line);
            i++;
        }

        if (!entry.isBalanced())
            throw new IllegalArgumentException(
                    "Journal does not balance — debits R" + entry.getTotalDebit() +
                            " ≠ credits R" + entry.getTotalCredit());

        journalRepo.save(entry);
        log.info("Created journal entry={} tenant={} createdBy={}", entryNumber, tenantId, createdBy);

        Map<UUID, AccAccount> accountMap = accountRepo.findAllActive(tenantId)
                .stream().collect(Collectors.toMap(AccAccount::getId, a -> a));
        return toJournalResponse(entry, accountMap);
    }

    @Transactional
    public JournalEntryResponse postJournalEntry(TenantId tenantId, UUID id) {
        AccJournalEntry entry = journalRepo.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("JournalEntry", id.toString()));
        if (!"DRAFT".equals(entry.getStatus()))
            throw new IllegalStateException(
                    "Only DRAFT entries can be posted — current status: " + entry.getStatus());
        entry.post();
        journalRepo.save(entry);
        log.info("Posted journal entry={}", entry.getEntryNumber());
        Map<UUID, AccAccount> accountMap = accountRepo.findAllActive(tenantId)
                .stream().collect(Collectors.toMap(AccAccount::getId, a -> a));
        return toJournalResponse(entry, accountMap);
    }

    /**
     * The maker-checker-enforcing post — this is what the controller's
     * own POST /journal-entries/{id}/post endpoint calls, NOT
     * postJournalEntry() directly. AccountingFacade (AP) and
     * reconcileWithNewJournal() both still call the plain
     * postJournalEntry() above, unchanged — this check only applies to
     * journals posted through the manual Journal Entries UI.
     * <p>
     * createdBy == null (every entry created before this feature existed,
     * or anything created via the facade/reconciliation paths) skips the
     * check entirely rather than blocking — this is a going-forward
     * control, not a retroactive lockout of existing data.
     */
    @Transactional
    public JournalEntryResponse postJournalEntryWithReview(TenantId tenantId, UUID id, UUID postedBy) {
        AccJournalEntry entry = journalRepo.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("JournalEntry", id.toString()));
        if (entry.getCreatedBy() != null && entry.getCreatedBy().equals(postedBy)) {
            throw new HandyFlowException(
                    "This journal entry was created by you — a different person must post it",
                    HttpStatus.BAD_REQUEST, "SAME_PERSON");
        }
        return postJournalEntry(tenantId, id);
    }

    @Transactional
    public JournalEntryResponse reverseJournalEntry(TenantId tenantId, UUID id,
                                                    LocalDate reversalDate) {
        AccJournalEntry original = journalRepo.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("JournalEntry", id.toString()));
        if (!"POSTED".equals(original.getStatus()))
            throw new IllegalStateException("Only POSTED entries can be reversed");

        LocalDate date = reversalDate != null ? reversalDate : LocalDate.now();
        String reversalNumber = numberGen.next(tenantId);

        AccJournalEntry reversal = AccJournalEntry.create(
                tenantId, reversalNumber, date,
                "REVERSAL of " + original.getEntryNumber() + ": " + original.getDescription(),
                "REV-" + original.getEntryNumber(), "ADJUSTMENT");
        journalRepo.save(reversal);

        int i = 0;
        for (AccJournalLine origLine : original.getLines()) {
            AccJournalLine reversalLine;
            if (origLine.getDebitAmount().compareTo(BigDecimal.ZERO) > 0) {
                reversalLine = AccJournalLine.credit(tenantId.getValue(), reversal.getId(),
                        origLine.getAccountId(), origLine.getDebitAmount(),
                        "Reversal: " + origLine.getDescription(), i);
            } else {
                reversalLine = AccJournalLine.debit(tenantId.getValue(), reversal.getId(),
                        origLine.getAccountId(), origLine.getCreditAmount(),
                        "Reversal: " + origLine.getDescription(), i);
            }
            reversal.addLine(reversalLine);
            i++;
        }

        reversal.post();
        journalRepo.save(reversal);
        original.markReversed(reversalNumber);
        journalRepo.save(original);

        log.info("Reversed entry={} → reversal={}", original.getEntryNumber(), reversalNumber);
        Map<UUID, AccAccount> accountMap = accountRepo.findAllActive(tenantId)
                .stream().collect(Collectors.toMap(AccAccount::getId, a -> a));
        return toJournalResponse(reversal, accountMap);
    }

    // ── Bank Accounts ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BankAccountResponse> getBankAccounts(TenantId tenantId) {
        return bankAccountRepo.findAllActive(tenantId).stream().map(this::toBankAccountResponse).toList();
    }

    @Transactional
    public BankAccountResponse createBankAccount(TenantId tenantId, CreateBankAccountRequest req) {
        AccBankAccount bank = AccBankAccount.create(tenantId, req.bankName(),
                req.accountName(), req.accountNumber(), req.branchCode(), req.accountType());
        bankAccountRepo.save(bank);
        log.info("Created bank account={} tenant={}", bank.getId(), tenantId);
        return toBankAccountResponse(bank);
    }

    // See AccBankAccount.linkAccount()'s own comment for why this exists —
    // create() never sets accountId, so every bank account needs this
    // (or an equivalent) called at least once before reconciliation's
    // match-candidates search can work at all.
    @Transactional
    public BankAccountResponse linkBankAccount(TenantId tenantId, UUID bankAccountId, LinkBankAccountRequest req) {
        AccBankAccount bank = bankAccountRepo.findActiveById(tenantId, bankAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("BankAccount", bankAccountId.toString()));
        accountRepo.findByTenantAndId(tenantId, req.accountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account", req.accountId().toString()));

        bank.linkAccount(req.accountId());
        bankAccountRepo.save(bank);
        log.info("Linked bank account={} to GL account={}", bankAccountId, req.accountId());
        return toBankAccountResponse(bank);
    }

    // threshold == null clears it, disabling low-balance alerting for
    // this account rather than erroring — see setLowBalanceThreshold()'s
    // own comment on AccBankAccount.
    @Transactional
    public BankAccountResponse updateLowBalanceThreshold(TenantId tenantId, UUID bankAccountId,
                                                         SetLowBalanceThresholdRequest req) {
        AccBankAccount bank = bankAccountRepo.findActiveById(tenantId, bankAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("BankAccount", bankAccountId.toString()));
        if (req.threshold() != null && req.threshold().compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Threshold cannot be negative");

        bank.setLowBalanceThreshold(req.threshold());
        bankAccountRepo.save(bank);
        log.info("Set low-balance threshold={} for bank account={}", req.threshold(), bankAccountId);
        return toBankAccountResponse(bank);
    }

    @Transactional
    public BankAccountResponse addTransaction(TenantId tenantId, UUID bankAccountId,
                                              AddBankTransactionRequest req) {
        if (req.amount() == null || req.amount().compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Transaction amount must be positive");

        AccBankAccount bank = bankAccountRepo.findActiveById(tenantId, bankAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("BankAccount", bankAccountId.toString()));

        BigDecimal newBalance = "CREDIT".equals(req.transactionType())
                ? bank.getCurrentBalance().add(req.amount())
                : bank.getCurrentBalance().subtract(req.amount());

        AccBankTransaction tx = AccBankTransaction.create(tenantId, bankAccountId,
                req.transactionDate(), req.description(), req.reference(),
                req.amount(), req.transactionType(), newBalance);
        bankTxRepo.save(tx);
        bank.updateBalance(newBalance);
        bankAccountRepo.save(bank); // same @Transactional — both flush together, atomically
        log.info("Added {} tx bank={} amount={} newBalance={}",
                req.transactionType(), bankAccountId, req.amount(), newBalance);
        return toBankAccountResponse(bank);
    }

    @Transactional(readOnly = true)
    public Page<BankTransactionResponse> getBankTransactions(TenantId tenantId,
                                                             UUID bankAccountId, Pageable pageable) {
        bankAccountRepo.findActiveById(tenantId, bankAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("BankAccount", bankAccountId.toString()));
        return bankTxRepo.findByBankAccount(tenantId, bankAccountId, pageable)
                .map(this::toBankTransactionResponse);
    }

    // ── Bank statement import ────────────────────────────────────────────────

    private static final DateTimeFormatter ISO_DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter SA_DATE_FMT  = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Generic 4-column CSV import: Date, Description, Reference, Amount
     * (header row expected, skipped automatically). Amount is signed —
     * positive = money in, negative = money out — translated into the
     * always-positive-amount + transactionType shape AccBankTransaction
     * actually stores. Duplicate rows (same account/date/amount/
     * description as something already imported) are skipped, not
     * errored, since re-uploading the same statement by mistake is a
     * realistic thing to happen.
     */
    @Transactional
    public ImportBankTransactionsResponse importBankTransactions(TenantId tenantId, UUID bankAccountId,
                                                                 ImportBankTransactionsRequest req) {
        AccBankAccount bank = bankAccountRepo.findActiveById(tenantId, bankAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("BankAccount", bankAccountId.toString()));

        String csv;
        try {
            csv = new String(Base64.getDecoder().decode(req.csvBase64()), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Could not decode CSV file — expected base64");
        }

        String[] rawLines = csv.split("\r?\n");
        int imported = 0, skipped = 0, failed = 0;
        List<String> errors = new ArrayList<>();
        BigDecimal runningBalance = bank.getCurrentBalance();

        // Row 0 assumed to be a header — skipped unconditionally, matching
        // "Date,Description,Reference,Amount" as documented.
        for (int i = 1; i < rawLines.length; i++) {
            String raw = rawLines[i].trim();
            if (raw.isEmpty()) continue;

            List<String> cols = parseCsvLine(raw);
            if (cols.size() < 4) {
                failed++;
                errors.add("Row " + (i + 1) + ": expected 4 columns (Date, Description, Reference, Amount), got " + cols.size());
                continue;
            }

            try {
                LocalDate date = parseFlexibleDate(cols.get(0));
                String description = cols.get(1);
                String reference = cols.get(2);
                BigDecimal signedAmount = parseAmount(cols.get(3));

                String transactionType = signedAmount.compareTo(BigDecimal.ZERO) >= 0 ? "CREDIT" : "DEBIT";
                BigDecimal amount = signedAmount.abs();

                if (bankTxRepo.existsDuplicate(tenantId, bankAccountId, date, amount, description)) {
                    skipped++;
                    continue;
                }

                runningBalance = "CREDIT".equals(transactionType)
                        ? runningBalance.add(amount)
                        : runningBalance.subtract(amount);

                AccBankTransaction tx = AccBankTransaction.create(tenantId, bankAccountId,
                        date, description, reference, amount, transactionType, runningBalance);
                bankTxRepo.save(tx);
                imported++;
            } catch (Exception e) {
                failed++;
                errors.add("Row " + (i + 1) + ": " + e.getMessage());
            }
        }

        bank.updateBalance(runningBalance);
        bankAccountRepo.save(bank);

        log.info("Imported {} bank transactions ({} skipped duplicates, {} failed) bankAccount={} newBalance={}",
                imported, skipped, failed, bankAccountId, runningBalance);

        return new ImportBankTransactionsResponse(imported, skipped, failed, errors, runningBalance);
    }

    // Basic quote-aware CSV split — handles "field, with a comma" but not
    // escaped ("") quotes within a quoted field. A full RFC4180 parser
    // felt like overkill for a generic 4-column importer; this covers the
    // realistic case (bank descriptions containing commas) without it.
    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(field.toString().trim());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        result.add(field.toString().trim());
        return result;
    }

    private LocalDate parseFlexibleDate(String s) {
        String trimmed = s.trim();
        try {
            return LocalDate.parse(trimmed, ISO_DATE_FMT);
        } catch (Exception e) {
            return LocalDate.parse(trimmed, SA_DATE_FMT); // dd/MM/yyyy — let this throw naturally if still bad
        }
    }

    // Defensive against "R 1234.56" / " 1234.56 " style pasted values —
    // does NOT handle thousands-separator commas, since comma is the CSV
    // delimiter itself; those need proper quoting in the source file.
    private BigDecimal parseAmount(String s) {
        String cleaned = s.trim().replace("R", "").replace(" ", "");
        return new BigDecimal(cleaned);
    }

    // ── Reconciliation ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MatchCandidateResponse> getMatchCandidates(TenantId tenantId, UUID bankAccountId, UUID transactionId) {
        AccBankAccount bank = bankAccountRepo.findActiveById(tenantId, bankAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("BankAccount", bankAccountId.toString()));
        AccBankTransaction tx = bankTxRepo.findById(transactionId)
                .filter(t -> t.getBankAccountId().equals(bankAccountId))
                .orElseThrow(() -> new ResourceNotFoundException("BankTransaction", transactionId.toString()));

        if (bank.getAccountId() == null) {
            throw new IllegalStateException(
                    "This bank account has no linked Chart of Accounts entry — cannot suggest matches");
        }

        // +/- 30 days around the transaction date — a real bank statement
        // rarely lines up exactly with when a journal was actually entered.
        LocalDate from = tx.getTransactionDate().minusDays(30);
        LocalDate to   = tx.getTransactionDate().plusDays(30);
        List<AccJournalEntry> entries = journalRepo.findPostedInRange(tenantId, from, to);

        Set<UUID> alreadyLinked = new HashSet<>(bankTxRepo.findLinkedJournalLineIds(tenantId));

        // Money IN to the bank account is a DEBIT to that GL account
        // (increasing an asset); money OUT is a CREDIT — matching exactly
        // how AP's own postPaymentJournal() already credits a bank
        // account when a payment goes out.
        boolean wantDebitLine = "CREDIT".equals(tx.getTransactionType());

        List<MatchCandidateResponse> candidates = new ArrayList<>();
        for (AccJournalEntry entry : entries) {
            for (AccJournalLine line : entry.getLines()) {
                if (!bank.getAccountId().equals(line.getAccountId())) continue;
                if (alreadyLinked.contains(line.getId())) continue;

                BigDecimal lineAmount = wantDebitLine ? line.getDebitAmount() : line.getCreditAmount();
                if (lineAmount == null || lineAmount.compareTo(BigDecimal.ZERO) <= 0) continue;

                boolean exact = lineAmount.compareTo(tx.getAmount()) == 0
                        && entry.getEntryDate().equals(tx.getTransactionDate());

                candidates.add(new MatchCandidateResponse(line.getId(), entry.getId(), entry.getEntryNumber(),
                        entry.getEntryDate(), entry.getDescription(), lineAmount, exact));
            }
        }

        // Exact matches first, then closest amount, then closest date.
        candidates.sort((a, b) -> {
            if (a.exactMatch() != b.exactMatch()) return a.exactMatch() ? -1 : 1;
            int amountCmp = a.amount().subtract(tx.getAmount()).abs()
                    .compareTo(b.amount().subtract(tx.getAmount()).abs());
            if (amountCmp != 0) return amountCmp;
            long aDays = Math.abs(ChronoUnit.DAYS.between(a.entryDate(), tx.getTransactionDate()));
            long bDays = Math.abs(ChronoUnit.DAYS.between(b.entryDate(), tx.getTransactionDate()));
            return Long.compare(aDays, bDays);
        });

        return candidates;
    }

    @Transactional
    public BankTransactionResponse reconcileTransaction(TenantId tenantId, UUID bankAccountId,
                                                        UUID transactionId, ReconcileRequest req) {
        bankAccountRepo.findActiveById(tenantId, bankAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("BankAccount", bankAccountId.toString()));
        AccBankTransaction tx = bankTxRepo.findById(transactionId)
                .filter(t -> t.getBankAccountId().equals(bankAccountId))
                .orElseThrow(() -> new ResourceNotFoundException("BankTransaction", transactionId.toString()));

        if (tx.isReconciled())
            throw new IllegalStateException("This transaction is already reconciled");
        if (bankTxRepo.findLinkedJournalLineIds(tenantId).contains(req.journalLineId()))
            throw new IllegalStateException("That journal line is already linked to a different transaction");

        tx.reconcile(req.journalLineId());
        bankTxRepo.save(tx);
        log.info("Reconciled bank transaction={} to journalLine={}", transactionId, req.journalLineId());
        return toBankTransactionResponse(tx);
    }

    /**
     * For transactions with no existing journal to match against — the
     * common case for most real transactions on a first import. Creates
     * a brand-new two-line journal entry (bank's own GL account
     * automatically on the correct side per the transaction's direction,
     * otherAccountId as the other side) and links the bank line straight
     * to it. Same underlying createJournalEntry()/postJournalEntry() this
     * class already exposes — no separate posting logic reimplemented.
     */
    @Transactional
    public BankTransactionResponse reconcileWithNewJournal(TenantId tenantId, UUID bankAccountId,
                                                           UUID transactionId, ReconcileWithNewJournalRequest req) {
        AccBankAccount bank = bankAccountRepo.findActiveById(tenantId, bankAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("BankAccount", bankAccountId.toString()));
        AccBankTransaction tx = bankTxRepo.findById(transactionId)
                .filter(t -> t.getBankAccountId().equals(bankAccountId))
                .orElseThrow(() -> new ResourceNotFoundException("BankTransaction", transactionId.toString()));

        if (tx.isReconciled())
            throw new IllegalStateException("This transaction is already reconciled");
        if (bank.getAccountId() == null)
            throw new IllegalStateException(
                    "This bank account has no linked Chart of Accounts entry — cannot create a journal entry for it");

        boolean isMoneyIn = "CREDIT".equals(tx.getTransactionType());
        String description = req.description() != null ? req.description() : tx.getDescription();

        List<CreateJournalEntryRequest.JournalLineRequest> lines = isMoneyIn
                ? List.of(
                new CreateJournalEntryRequest.JournalLineRequest(bank.getAccountId(), description, tx.getAmount(), null),
                new CreateJournalEntryRequest.JournalLineRequest(req.otherAccountId(), description, null, tx.getAmount()))
                : List.of(
                new CreateJournalEntryRequest.JournalLineRequest(req.otherAccountId(), description, tx.getAmount(), null),
                new CreateJournalEntryRequest.JournalLineRequest(bank.getAccountId(), description, null, tx.getAmount()));

        // entryType: "MANUAL", not "BANK_RECONCILIATION" as originally
        // written here — that value hit a real acc_journal_entries_
        // entry_type_check constraint violation. "MANUAL" is used
        // deliberately instead: it's AccJournalEntry.create()'s own
        // null-fallback default, and it's the exact value AP's
        // postApprovalJournal() already posts successfully via
        // AccountingFacade, confirmed against real data earlier this
        // session — not a second guess at an unverified constraint.
        CreateJournalEntryRequest createReq = new CreateJournalEntryRequest(
                tx.getTransactionDate(), description, tx.getReference(), "MANUAL", lines);

        JournalEntryResponse created = createJournalEntry(tenantId, createReq);
        JournalEntryResponse posted  = postJournalEntry(tenantId, created.id());

        UUID bankLineId = posted.lines().stream()
                .filter(l -> bank.getAccountId().equals(l.accountId()))
                .findFirst()
                .map(JournalEntryResponse.JournalLineResponse::id)
                .orElseThrow(() -> new IllegalStateException(
                        "Could not find the bank account's line in the newly created journal entry"));

        tx.reconcile(bankLineId);
        bankTxRepo.save(tx);
        log.info("Reconciled bank transaction={} via new journal={}", transactionId, posted.entryNumber());
        return toBankTransactionResponse(tx);
    }

    // ── VAT ───────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<VatPeriodResponse> getVatPeriods(TenantId tenantId) {
        return vatPeriodRepo.findAll(tenantId).stream().map(this::toVatPeriodResponse).toList();
    }

    @Transactional
    public VatPeriodResponse createVatPeriod(TenantId tenantId,
                                             LocalDate periodStart, LocalDate periodEnd) {
        if (!periodEnd.isAfter(periodStart))
            throw new IllegalArgumentException("Period end must be after period start");
        vatPeriodRepo.findOpenPeriod(tenantId).ifPresent(p -> {
            throw new IllegalStateException(
                    "An open VAT period already exists: " + p.getPeriodStart() + " to " + p.getPeriodEnd());
        });
        AccVatPeriod period = AccVatPeriod.create(tenantId, periodStart, periodEnd);
        vatPeriodRepo.save(period);
        return toVatPeriodResponse(period);
    }

    @Transactional
    public VatPeriodResponse closeVatPeriod(TenantId tenantId, UUID periodId) {
        AccVatPeriod period = vatPeriodRepo.findByTenantAndId(tenantId, periodId)
                .orElseThrow(() -> new ResourceNotFoundException("VatPeriod", periodId.toString()));
        if (!"OPEN".equals(period.getStatus()))
            throw new IllegalStateException("VAT period is not OPEN");
        period.close();
        vatPeriodRepo.save(period);
        return toVatPeriodResponse(period);
    }

    /**
     * Attach a freshly-calculated VAT201 result to a period — the exact
     * gap the original audit named: "no button to attach a calculated
     * VAT201 result to a specific period before closing it. An
     * accountant has to manually copy numbers across." Recomputes using
     * the PERIOD's own periodStart/periodEnd, not whatever from/to the
     * calculator currently happens to show — so the attached figures
     * always genuinely correspond to that period's real boundaries.
     * Safe to call more than once (e.g. after a late invoice is added):
     * see AccVatPeriod.attachVat201Result()'s own comment for why that's
     * a replace, not an accumulate.
     */
    @Transactional
    public VatPeriodResponse attachVat201ToPeriod(TenantId tenantId, UUID periodId) {
        AccVatPeriod period = vatPeriodRepo.findByTenantAndId(tenantId, periodId)
                .orElseThrow(() -> new ResourceNotFoundException("VatPeriod", periodId.toString()));
        if (!"OPEN".equals(period.getStatus()))
            throw new IllegalStateException("Only OPEN periods can have a VAT201 result attached");

        Vat201Response vat201 = getVat201(tenantId, period.getPeriodStart(), period.getPeriodEnd());
        period.attachVat201Result(vat201.outputVat(), vat201.inputVat());
        vatPeriodRepo.save(period);
        log.info("Attached VAT201 result to period={} outputVat={} inputVat={}",
                periodId, vat201.outputVat(), vat201.inputVat());
        return toVatPeriodResponse(period);
    }

    @Transactional(readOnly = true)
    public Vat201Response getVat201(TenantId tenantId, LocalDate from, LocalDate to) {
        InvoicingFacade.VatSummary vatSummary = invoicingFacade.getVatSummary(tenantId, from, to);
        BigDecimal outputVat  = vatSummary.totalOutputVat();
        BigDecimal totalSales = vatSummary.totalSubtotal();

        Map<UUID, AccAccount> accounts = accountRepo.findAllActive(tenantId)
                .stream().collect(Collectors.toMap(AccAccount::getId, a -> a));
        BigDecimal inputVat = journalRepo.findPostedInRange(tenantId, from, to).stream()
                .flatMap(e -> e.getLines().stream())
                .filter(l -> {
                    AccAccount acc = accounts.get(l.getAccountId());
                    return acc != null && "VAT".equals(acc.getAccountSubtype())
                            && "ASSET".equals(acc.getAccountType());
                })
                .map(l -> l.getDebitAmount().subtract(l.getCreditAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new Vat201Response(from, to, vatSummary.invoiceCount(), totalSales, outputVat,
                inputVat, outputVat.subtract(inputVat));
    }

    // ── AR Aging ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AgingReportResponse getArAging(TenantId tenantId) {
        List<InvoicingFacade.OutstandingInvoiceSummary> outstanding =
                invoicingFacade.findOutstandingInvoices(tenantId);
        LocalDate today = LocalDate.now();

        BigDecimal current = BigDecimal.ZERO, days30 = BigDecimal.ZERO,
                days60 = BigDecimal.ZERO, days90 = BigDecimal.ZERO, over90 = BigDecimal.ZERO;
        List<AgingReportResponse.AgingLine> lines = new ArrayList<>();

        for (InvoicingFacade.OutstandingInvoiceSummary inv : outstanding) {
            BigDecimal balance = inv.total().subtract(
                    inv.amountPaid() != null ? inv.amountPaid() : BigDecimal.ZERO);
            if (balance.compareTo(BigDecimal.ZERO) <= 0) continue;

            LocalDate due = inv.dueDate() != null ? inv.dueDate() : today;
            long daysOverdue = ChronoUnit.DAYS.between(due, today);

            String bucket;
            if      (daysOverdue <= 0)  { bucket = "CURRENT"; current = current.add(balance); }
            else if (daysOverdue <= 30) { bucket = "1-30";    days30  = days30.add(balance);  }
            else if (daysOverdue <= 60) { bucket = "31-60";   days60  = days60.add(balance);  }
            else if (daysOverdue <= 90) { bucket = "61-90";   days90  = days90.add(balance);  }
            else                        { bucket = "90+";     over90  = over90.add(balance);  }

            String customerName = inv.customerId() != null
                    ? crmFacade.findCustomerById(tenantId, inv.customerId())
                    .map(c -> c.name())
                    .orElse("Customer " + inv.customerId().toString().substring(0, 8))
                    : inv.walkinClientName();
            lines.add(new AgingReportResponse.AgingLine(
                    inv.id(), inv.invoiceNumber(),
                    customerName,
                    inv.dueDate(), daysOverdue > 0 ? (int) daysOverdue : 0,
                    balance, bucket));
        }

        lines.sort(Comparator.comparingLong(AgingReportResponse.AgingLine::daysOverdue).reversed());
        BigDecimal total = current.add(days30).add(days60).add(days90).add(over90);
        return new AgingReportResponse(today, lines, current, days30, days60, days90, over90, total);
    }

    // ── Financial Reports ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public FinancialReportResponse getProfitAndLoss(TenantId tenantId,
                                                    LocalDate from, LocalDate to) {
        coaSeeder.seedForTenant(tenantId);
        List<AccJournalEntry> entries = journalRepo.findPostedInRange(tenantId, from, to);
        Map<UUID, AccAccount> accounts = accountRepo.findAllActive(tenantId)
                .stream().collect(Collectors.toMap(AccAccount::getId, a -> a));
        Map<UUID, BigDecimal> balances = computeBalancesWithOpeningBalances(entries, accounts);

        var incomeLines  = buildLines(accounts, balances, "INCOME",  true);
        var expenseLines = buildLines(accounts, balances, "EXPENSE", false);
        BigDecimal totalIncome  = sum(incomeLines);
        BigDecimal totalExpense = sum(expenseLines);

        return new FinancialReportResponse("PROFIT_AND_LOSS", from, to,
                List.of(new FinancialReportResponse.ReportSection("Income",   incomeLines,  totalIncome),
                        new FinancialReportResponse.ReportSection("Expenses", expenseLines, totalExpense)),
                totalIncome.subtract(totalExpense));
    }

    @Transactional(readOnly = true)
    public FinancialReportResponse getTrialBalance(TenantId tenantId,
                                                   LocalDate from, LocalDate to) {
        coaSeeder.seedForTenant(tenantId);
        List<AccJournalEntry> entries = journalRepo.findPostedInRange(tenantId, from, to);
        Map<UUID, AccAccount> accounts = accountRepo.findAllActive(tenantId)
                .stream().collect(Collectors.toMap(AccAccount::getId, a -> a));

        Map<UUID, BigDecimal[]> grossMap = new HashMap<>();
        for (AccJournalEntry e : entries) {
            for (AccJournalLine l : e.getLines()) {
                grossMap.computeIfAbsent(l.getAccountId(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                grossMap.get(l.getAccountId())[0] = grossMap.get(l.getAccountId())[0].add(l.getDebitAmount());
                grossMap.get(l.getAccountId())[1] = grossMap.get(l.getAccountId())[1].add(l.getCreditAmount());
            }
        }

        List<FinancialReportResponse.ReportLine> lines = new ArrayList<>();
        BigDecimal totalDebits = BigDecimal.ZERO, totalCredits = BigDecimal.ZERO;
        for (var entry : grossMap.entrySet()) {
            AccAccount acc = accounts.get(entry.getKey());
            if (acc == null) continue;
            BigDecimal debit  = entry.getValue()[0];
            BigDecimal credit = entry.getValue()[1];
            if (debit.compareTo(BigDecimal.ZERO) == 0 && credit.compareTo(BigDecimal.ZERO) == 0) continue;
            lines.add(new FinancialReportResponse.ReportLine(
                    acc.getAccountCode(), acc.getAccountName(),
                    debit.subtract(credit), debit, credit));
            totalDebits  = totalDebits.add(debit);
            totalCredits = totalCredits.add(credit);
        }
        lines.sort(Comparator.comparing(FinancialReportResponse.ReportLine::accountCode));

        return new FinancialReportResponse("TRIAL_BALANCE", from, to,
                List.of(new FinancialReportResponse.ReportSection("All Accounts", lines, totalDebits)),
                totalDebits.subtract(totalCredits));
    }

    @Transactional(readOnly = true)
    public FinancialReportResponse getBalanceSheet(TenantId tenantId, LocalDate from, LocalDate to) {
        coaSeeder.seedForTenant(tenantId);
        List<AccJournalEntry> entries = journalRepo.findPostedInRange(tenantId, from, to);
        Map<UUID, AccAccount> accounts = accountRepo.findAllActive(tenantId)
                .stream().collect(Collectors.toMap(AccAccount::getId, a -> a));
        Map<UUID, BigDecimal> balances = computeBalancesWithOpeningBalances(entries, accounts);

        // THE FIX: buildLines() below only ever reads ASSET/LIABILITY/
        // EQUITY account types — every journal line that hits an INCOME
        // or EXPENSE account (i.e. every revenue and cost posting ever
        // made) was silently invisible to the Balance Sheet, with no
        // closing entry ever folding that activity into Equity. Assets
        // would only ever equal Liabilities + Equity by coincidence, for
        // a tenant that happened to break exactly even. Confirmed
        // directly against real data: zero journal lines have ever
        // touched account 3020 or 3040 for this tenant, for any period.
        //
        // Fix: compute Net Profit the identical way getProfitAndLoss()
        // already does (same buildLines() calls, same sign convention),
        // then fold it into the real "Current Year Earnings" (3040)
        // account before the Equity section is built — not an ad-hoc
        // synthetic line, but the actual seeded account that exists
        // specifically for this purpose.
        var incomeLinesForClose  = buildLines(accounts, balances, "INCOME",  true);
        var expenseLinesForClose = buildLines(accounts, balances, "EXPENSE", false);
        BigDecimal netProfitForClose = sum(incomeLinesForClose).subtract(sum(expenseLinesForClose));

        accountRepo.findByTenantAndCode(tenantId, "3040").ifPresent(currentYearEarnings ->
                balances.merge(currentYearEarnings.getId(), netProfitForClose.negate(), BigDecimal::add));
        // .negate() because buildLines() negates EQUITY balances again on
        // the way out (positiveIsCredit=true) — storing the pre-negated
        // value here means a real profit displays as a positive Equity
        // contribution, not a negative one.

        var assetLines     = buildLines(accounts, balances, "ASSET",     false);
        var liabilityLines = buildLines(accounts, balances, "LIABILITY", true);
        var equityLines    = buildLines(accounts, balances, "EQUITY",    true);

        BigDecimal totalAssets      = sum(assetLines);
        BigDecimal totalLiabilities = sum(liabilityLines);
        BigDecimal totalEquity      = sum(equityLines);

        return new FinancialReportResponse("BALANCE_SHEET", from, to,
                List.of(new FinancialReportResponse.ReportSection("Assets",      assetLines,     totalAssets),
                        new FinancialReportResponse.ReportSection("Liabilities", liabilityLines, totalLiabilities),
                        new FinancialReportResponse.ReportSection("Equity",      equityLines,    totalEquity)),
                totalLiabilities.add(totalEquity));
    }

    /**
     * Drill down from any report line (P&L, Balance Sheet, or Trial
     * Balance — all three work identically here) into the actual POSTED
     * journal lines that fed its displayed amount. Deliberately the same
     * source query (findPostedInRange, filtered to this account) every
     * report method already uses — this isn't a separate reimplementation
     * that could drift out of sync with what the reports themselves show.
     */
    @Transactional(readOnly = true)
    public AccountDrillDownResponse getAccountDrillDown(TenantId tenantId, String accountCode,
                                                        LocalDate from, LocalDate to) {
        AccAccount account = accountRepo.findByTenantAndCode(tenantId, accountCode)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountCode));

        List<AccJournalEntry> entries = journalRepo.findPostedInRange(tenantId, from, to);

        List<AccountDrillDownResponse.DrillDownLine> lines = new ArrayList<>();
        BigDecimal totalDebit = BigDecimal.ZERO, totalCredit = BigDecimal.ZERO;
        for (AccJournalEntry entry : entries) {
            for (AccJournalLine line : entry.getLines()) {
                if (!account.getId().equals(line.getAccountId())) continue;
                lines.add(new AccountDrillDownResponse.DrillDownLine(
                        entry.getId(), entry.getEntryNumber(), entry.getEntryDate(),
                        entry.getDescription(), line.getDescription(),
                        line.getDebitAmount(), line.getCreditAmount()));
                totalDebit  = totalDebit.add(line.getDebitAmount());
                totalCredit = totalCredit.add(line.getCreditAmount());
            }
        }
        lines.sort(Comparator.comparing(AccountDrillDownResponse.DrillDownLine::entryDate));

        return new AccountDrillDownResponse(account.getAccountCode(), account.getAccountName(),
                account.getOpeningBalance(), lines, totalDebit, totalCredit,
                totalDebit.subtract(totalCredit));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<UUID, BigDecimal> computeBalancesWithOpeningBalances(
            List<AccJournalEntry> entries, Map<UUID, AccAccount> accounts) {
        Map<UUID, BigDecimal> balances = new HashMap<>();
        for (AccAccount acc : accounts.values()) {
            BigDecimal ob = acc.getOpeningBalance();
            if (ob != null && ob.compareTo(BigDecimal.ZERO) != 0)
                balances.put(acc.getId(), ob);
        }
        for (AccJournalEntry entry : entries) {
            for (AccJournalLine line : entry.getLines()) {
                BigDecimal net = line.getDebitAmount().subtract(line.getCreditAmount());
                balances.merge(line.getAccountId(), net, BigDecimal::add);
            }
        }
        return balances;
    }

    private List<FinancialReportResponse.ReportLine> buildLines(
            Map<UUID, AccAccount> accounts, Map<UUID, BigDecimal> balances,
            String type, boolean positiveIsCredit) {
        return accounts.values().stream()
                .filter(a -> type.equals(a.getAccountType()))
                .map(a -> {
                    BigDecimal bal = balances.getOrDefault(a.getId(), BigDecimal.ZERO);
                    BigDecimal displayed = positiveIsCredit ? bal.negate() : bal;
                    return new FinancialReportResponse.ReportLine(
                            a.getAccountCode(), a.getAccountName(), displayed);
                })
                .filter(l -> l.amount().compareTo(BigDecimal.ZERO) != 0)
                .sorted(Comparator.comparing(FinancialReportResponse.ReportLine::accountCode))
                .toList();
    }

    private BigDecimal sum(List<FinancialReportResponse.ReportLine> lines) {
        return lines.stream().map(FinancialReportResponse.ReportLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private AccountResponse toAccountResponse(AccAccount a) {
        return new AccountResponse(a.getId(), a.getAccountCode(), a.getAccountName(),
                a.getAccountType(), a.getAccountSubtype(), a.isSystem(),
                a.getOpeningBalance(), a.getDescription());
    }

    private JournalEntryResponse toJournalResponse(AccJournalEntry e,
                                                   Map<UUID, AccAccount> accountMap) {
        List<JournalEntryResponse.JournalLineResponse> lines = e.getLines().stream()
                .map(l -> {
                    AccAccount acc = accountMap.get(l.getAccountId());
                    return new JournalEntryResponse.JournalLineResponse(
                            l.getId(), l.getAccountId(),
                            acc != null ? acc.getAccountCode() : null,
                            acc != null ? acc.getAccountName() : null,
                            l.getDescription(), l.getDebitAmount(), l.getCreditAmount());
                }).toList();
        return new JournalEntryResponse(e.getId(), e.getEntryNumber(), e.getEntryDate(),
                e.getDescription(), e.getReference(), e.getEntryType(), e.getStatus(),
                e.getTotalDebit(), e.getTotalCredit(), e.isBalanced(), lines,
                e.getCreatedBy(), e.getCreatedAt());
    }

    private BankAccountResponse toBankAccountResponse(AccBankAccount b) {
        return new BankAccountResponse(b.getId(), b.getBankName(), b.getAccountName(),
                b.getAccountNumber(), b.getBranchCode(), b.getAccountType(),
                b.getCurrency(), b.getCurrentBalance(), b.isActive(), b.getAccountId(),
                b.getLowBalanceThreshold());
    }

    private BankTransactionResponse toBankTransactionResponse(AccBankTransaction t) {
        return new BankTransactionResponse(t.getId(), t.getBankAccountId(),
                t.getTransactionDate(), t.getDescription(), t.getReference(),
                t.getAmount(), t.getTransactionType(), t.getBalanceAfter(),
                t.isReconciled(), t.getCreatedAt(),
                t.getJournalLineId(), t.getReconciledAt());
    }

    private VatPeriodResponse toVatPeriodResponse(AccVatPeriod v) {
        return new VatPeriodResponse(v.getId(), v.getPeriodStart(), v.getPeriodEnd(),
                v.getStatus(), v.getOutputVat(), v.getInputVat(), v.getVatPayable());
    }

    // ── Monthly summary for dashboard charts ──────────────────────────────────

    @Transactional(readOnly = true)
    public List<MonthlySummaryResponse> getMonthlySummary(TenantId tenantId, int months) {
        List<MonthlySummaryResponse> result = new ArrayList<>();
        YearMonth current = YearMonth.now();

        for (int i = months - 1; i >= 0; i--) {
            YearMonth ym   = current.minusMonths(i);
            LocalDate from = ym.atDay(1);
            LocalDate to   = ym.atEndOfMonth();

            List<AccJournalEntry> entries = journalRepo.findPostedInRange(tenantId, from, to);
            Map<UUID, AccAccount> accounts = accountRepo.findAllActive(tenantId)
                    .stream().collect(Collectors.toMap(AccAccount::getId, a -> a));

            BigDecimal revenue  = BigDecimal.ZERO;
            BigDecimal expenses = BigDecimal.ZERO;

            for (AccJournalEntry je : entries) {
                for (var line : je.getLines()) {
                    AccAccount acc = accounts.get(line.getAccountId());
                    if (acc == null) continue;
                    switch (acc.getAccountType()) {
                        case "INCOME"  -> revenue  = revenue.add(line.getCreditAmount()
                                .subtract(line.getDebitAmount()));
                        case "EXPENSE" -> expenses = expenses.add(line.getDebitAmount()
                                .subtract(line.getCreditAmount()));
                    }
                }
            }

            result.add(new MonthlySummaryResponse(
                    ym.getYear(), ym.getMonthValue(),
                    ym.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH),
                    revenue.max(BigDecimal.ZERO),
                    expenses.max(BigDecimal.ZERO),
                    revenue.subtract(expenses)
            ));
        }
        return result;
    }

    // ── Email notifications ───────────────────────────────────────────────────

    /**
     * Send VAT period closing reminder.
     * Called by AccountingNotificationScheduler when a period closes within 7 days.
     * pdfBytes is nullable — the scheduler generates it separately (this
     * class can't inject AccountingReportPdfService itself, since that
     * service already injects AccountingService — would be circular) and
     * passes null if PDF generation failed, so a reminder still goes out
     * even without the attachment rather than not sending at all.
     */
    public void sendVatReminder(TenantId tenantId, String recipientEmail,
                                String companyName, LocalDate periodEnd, BigDecimal estimatedVat,
                                byte[] pdfBytes) {
        String subject = "VAT Period Closing Soon — " + periodEnd.format(
                DateTimeFormatter.ofPattern("d MMM yyyy"));
        String body = vatReminderEmail(companyName, periodEnd, estimatedVat);
        if (pdfBytes != null) {
            emailService.sendWithAttachment(recipientEmail, subject, body, "VAT201-summary.pdf", pdfBytes);
        } else {
            emailService.send(recipientEmail, subject, body);
        }
        log.info("Sent VAT reminder to={} periodEnd={} withPdf={}", recipientEmail, periodEnd, pdfBytes != null);
    }

    /**
     * Send overdue AR alert.
     * Called by AccountingNotificationScheduler daily for outstanding invoices > 30 days.
     * Same nullable-pdfBytes reasoning as sendVatReminder() above.
     */
    public void sendOverdueArAlert(TenantId tenantId, String recipientEmail,
                                   String companyName, AgingReportResponse aging,
                                   byte[] pdfBytes) {
        long overdueCount = aging.lines().stream()
                .filter(l -> !"CURRENT".equals(l.bucket())).count();
        if (overdueCount == 0) return;  // nothing overdue, skip

        BigDecimal overdueTotal = aging.days1to30()
                .add(aging.days31to60()).add(aging.days61to90()).add(aging.over90());

        String subject = String.format("Overdue Invoices Alert — %d invoice%s, %s outstanding",
                overdueCount, overdueCount == 1 ? "" : "s", fmtAmount(overdueTotal));
        String body = overdueArEmail(companyName, aging, overdueCount, overdueTotal);
        if (pdfBytes != null) {
            emailService.sendWithAttachment(recipientEmail, subject, body, "AR-aging-report.pdf", pdfBytes);
        } else {
            emailService.send(recipientEmail, subject, body);
        }
        log.info("Sent overdue AR alert to={} overdueCount={} total={} withPdf={}",
                recipientEmail, overdueCount, overdueTotal, pdfBytes != null);
    }

    private String fmtAmount(BigDecimal v) {
        return "R " + String.format("%,.2f", v);
    }

    // ── Email templates ───────────────────────────────────────────────────────

    private String vatReminderEmail(String company, LocalDate periodEnd, BigDecimal estimatedVat) {
        return """
            <!DOCTYPE html>
            <html><body style="font-family:Arial,sans-serif;color:#0F172A;max-width:600px;margin:0 auto;padding:20px">
              <div style="background:#7C3AED;padding:24px;border-radius:8px 8px 0 0">
                <h1 style="color:white;margin:0;font-size:20px">VAT Return Reminder</h1>
                <p style="color:#DDD6FE;margin:6px 0 0;font-size:13px">%s</p>
              </div>
              <div style="background:#F5F3FF;padding:24px;border-radius:0 0 8px 8px">
                <p style="font-size:15px">Your VAT period closes on <strong>%s</strong>.</p>
                <div style="background:white;border-radius:8px;padding:16px;margin:16px 0">
                  <div style="font-size:11px;color:#7C3AED;font-weight:700;letter-spacing:0.05em;margin-bottom:4px">ESTIMATED VAT PAYABLE</div>
                  <div style="font-size:28px;font-weight:800;color:#DC2626">%s</div>
                  <div style="font-size:12px;color:#64748B;margin-top:4px">Based on posted journal entries</div>
                </div>
                <p style="font-size:13px;color:#64748B">Log in to HandyFlow to review your VAT201 and close the period before the due date.</p>
              </div>
            </body></html>
            """.formatted(company, periodEnd.format(DateTimeFormatter.ofPattern("d MMMM yyyy")),
                fmtAmount(estimatedVat));
    }

    private String overdueArEmail(String company, AgingReportResponse aging,
                                  long overdueCount, BigDecimal overdueTotal) {
        StringBuilder rows = new StringBuilder();
        aging.lines().stream()
                .filter(l -> !"CURRENT".equals(l.bucket()))
                .limit(10)
                .forEach(l -> rows.append(String.format(
                        "<tr><td style='padding:8px;border-bottom:1px solid #F1F5F9'>%s</td>"
                                + "<td style='padding:8px;border-bottom:1px solid #F1F5F9'>%s</td>"
                                + "<td style='padding:8px;border-bottom:1px solid #F1F5F9;text-align:right;color:#DC2626;font-weight:600'>%s</td>"
                                + "<td style='padding:8px;border-bottom:1px solid #F1F5F9;text-align:center'>"
                                + "<span style='background:#FEF2F2;color:#DC2626;padding:2px 8px;border-radius:10px;font-size:11px;font-weight:700'>%s</span></td></tr>",
                        l.invoiceNumber(), l.customerName(), fmtAmount(l.balance()), l.bucket())));

        return """
            <!DOCTYPE html>
            <html><body style="font-family:Arial,sans-serif;color:#0F172A;max-width:600px;margin:0 auto;padding:20px">
              <div style="background:#DC2626;padding:24px;border-radius:8px 8px 0 0">
                <h1 style="color:white;margin:0;font-size:20px">Overdue Invoices Alert</h1>
                <p style="color:#FEE2E2;margin:6px 0 0;font-size:13px">%s</p>
              </div>
              <div style="background:#FEF2F2;padding:24px;border-radius:0 0 8px 8px">
                <p style="font-size:15px">You have <strong>%d overdue invoice%s</strong> totalling <strong style="color:#DC2626">%s</strong>.</p>
                <table style="width:100%%;border-collapse:collapse;background:white;border-radius:8px;overflow:hidden;margin:16px 0">
                  <thead>
                    <tr style="background:#0F172A">
                      <th style="padding:10px;color:white;font-size:11px;text-align:left">Invoice</th>
                      <th style="padding:10px;color:white;font-size:11px;text-align:left">Customer</th>
                      <th style="padding:10px;color:white;font-size:11px;text-align:right">Balance</th>
                      <th style="padding:10px;color:white;font-size:11px;text-align:center">Age</th>
                    </tr>
                  </thead>
                  <tbody>%s</tbody>
                </table>
                <p style="font-size:13px;color:#64748B">Log in to HandyFlow to view the full AR Aging report and follow up with customers.</p>
              </div>
            </body></html>
            """.formatted(company, overdueCount, overdueCount == 1 ? "" : "s",
                fmtAmount(overdueTotal), rows);
    }

    /**
     * Called daily by AccountingNotificationScheduler for every tenant
     * with at least one bank account below its own threshold. Fires
     * every day the condition persists, not just once — matching the
     * confirmed pattern NoShowAlertScheduler already uses for an
     * unresolved shift, since a persistently low balance is the same
     * kind of ongoing problem, not a one-off heads-up.
     */
    public void sendLowBalanceAlert(TenantId tenantId, String recipientEmail,
                                    String companyName, List<AccBankAccount> lowAccounts) {
        String subject = String.format("Low Bank Balance — %d account%s below threshold",
                lowAccounts.size(), lowAccounts.size() == 1 ? "" : "s");
        String body = lowBalanceEmail(companyName, lowAccounts);
        emailService.send(recipientEmail, subject, body);
        log.info("Sent low balance alert to={} accountCount={}", recipientEmail, lowAccounts.size());
    }

    /**
     * Called daily by AccountingNotificationScheduler for every OPEN VAT
     * period past its own end date. Same daily-repeat-until-resolved
     * reasoning as sendLowBalanceAlert() above — an overdue VAT period is
     * an active compliance risk, not a one-time reminder that should go
     * quiet while the actual problem is still unresolved.
     */
    public void sendVatOverdueEscalation(TenantId tenantId, String recipientEmail,
                                         String companyName, LocalDate periodEnd, long daysOverdue) {
        String subject = String.format("VAT Period Overdue — %d day%s past due",
                daysOverdue, daysOverdue == 1 ? "" : "s");
        String body = vatOverdueEmail(companyName, periodEnd, daysOverdue);
        emailService.send(recipientEmail, subject, body);
        log.info("Sent VAT overdue escalation to={} periodEnd={} daysOverdue={}",
                recipientEmail, periodEnd, daysOverdue);
    }

    private String lowBalanceEmail(String company, List<AccBankAccount> lowAccounts) {
        StringBuilder rows = new StringBuilder();
        for (AccBankAccount b : lowAccounts) {
            rows.append(String.format(
                    "<tr><td style='padding:8px;border-bottom:1px solid #F1F5F9'>%s — %s</td>"
                            + "<td style='padding:8px;border-bottom:1px solid #F1F5F9;text-align:right;color:#DC2626;font-weight:600'>%s</td>"
                            + "<td style='padding:8px;border-bottom:1px solid #F1F5F9;text-align:right;color:#64748B'>%s</td></tr>",
                    b.getBankName(), b.getAccountName(),
                    fmtAmount(b.getCurrentBalance()), fmtAmount(b.getLowBalanceThreshold())));
        }

        return """
            <!DOCTYPE html>
            <html><body style="font-family:Arial,sans-serif;color:#0F172A;max-width:600px;margin:0 auto;padding:20px">
              <div style="background:#DC2626;padding:24px;border-radius:8px 8px 0 0">
                <h1 style="color:white;margin:0;font-size:20px">Low Bank Balance Alert</h1>
                <p style="color:#FEE2E2;margin:6px 0 0;font-size:13px">%s</p>
              </div>
              <div style="background:#FEF2F2;padding:24px;border-radius:0 0 8px 8px">
                <p style="font-size:15px">The following account%s below the threshold you set:</p>
                <table style="width:100%%;border-collapse:collapse;background:white;border-radius:8px;overflow:hidden;margin:16px 0">
                  <thead>
                    <tr style="background:#0F172A">
                      <th style="padding:10px;color:white;font-size:11px;text-align:left">Account</th>
                      <th style="padding:10px;color:white;font-size:11px;text-align:right">Current Balance</th>
                      <th style="padding:10px;color:white;font-size:11px;text-align:right">Threshold</th>
                    </tr>
                  </thead>
                  <tbody>%s</tbody>
                </table>
                <p style="font-size:13px;color:#64748B">This will keep sending daily until the balance is back above the threshold, or you change/clear the threshold on that account.</p>
              </div>
            </body></html>
            """.formatted(company, lowAccounts.size() == 1 ? " is" : "s are", rows);
    }

    private String vatOverdueEmail(String company, LocalDate periodEnd, long daysOverdue) {
        return """
            <!DOCTYPE html>
            <html><body style="font-family:Arial,sans-serif;color:#0F172A;max-width:600px;margin:0 auto;padding:20px">
              <div style="background:#7F1D1D;padding:24px;border-radius:8px 8px 0 0">
                <h1 style="color:white;margin:0;font-size:20px">⚠ VAT Period Overdue</h1>
                <p style="color:#FECACA;margin:6px 0 0;font-size:13px">%s</p>
              </div>
              <div style="background:#FEF2F2;padding:24px;border-radius:0 0 8px 8px">
                <p style="font-size:15px">Your VAT period that ended <strong>%s</strong> is still open —
                  <strong style="color:#7F1D1D">%d day%s overdue</strong>.</p>
                <p style="font-size:13px;color:#64748B">Missing a SARS VAT201 deadline can carry real penalties. Log in to HandyFlow, review and close this period, and submit as soon as possible. This reminder will repeat daily until the period is closed.</p>
              </div>
            </body></html>
            """.formatted(company, periodEnd.format(DateTimeFormatter.ofPattern("d MMMM yyyy")),
                daysOverdue, daysOverdue == 1 ? "" : "s");
    }

}