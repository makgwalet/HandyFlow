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
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountingService {

    private final AccAccountRepository      accountRepo;
    private final AccJournalEntryRepository journalRepo;
    private final AccBankAccountRepository  bankAccountRepo;
    private final AccBankTransactionRepository bankTxRepo;
    private final AccVatPeriodRepository    vatPeriodRepo;
    private final ChartOfAccountsSeeder     coaSeeder;
    private final JournalNumberGenerator    numberGen;

    // ── Chart of Accounts ─────────────────────────────────────────────────────

    @Transactional
    public List<AccountResponse> getAccounts(TenantId tenantId) {
        coaSeeder.seedForTenant(tenantId); // idempotent — only seeds once
        return accountRepo.findAllActive(tenantId)
                .stream().map(this::toAccountResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsByType(TenantId tenantId, String type) {
        return accountRepo.findByType(tenantId, type)
                .stream().map(this::toAccountResponse).toList();
    }

    // ── Journal Entries ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<JournalEntryResponse> getJournalEntries(TenantId tenantId,
                                                        String status, Pageable pageable) {
        return journalRepo.findAllActive(tenantId, status, pageable)
                .map(e -> toJournalResponse(e, tenantId));
    }

    @Transactional
    public JournalEntryResponse createJournalEntry(TenantId tenantId,
                                                   CreateJournalEntryRequest req) {
        coaSeeder.seedForTenant(tenantId);

        if (req.lines().size() < 2)
            throw new IllegalArgumentException("Journal entry requires at least 2 lines");

        String entryNumber = numberGen.next(tenantId);
        AccJournalEntry entry = AccJournalEntry.create(
                tenantId, entryNumber, req.entryDate(),
                req.description(), req.reference(), req.entryType()
        );
        journalRepo.save(entry); // save first to get ID

        int i = 0;
        for (CreateJournalEntryRequest.JournalLineRequest lr : req.lines()) {
            BigDecimal debit  = lr.debitAmount()  != null ? lr.debitAmount()  : BigDecimal.ZERO;
            BigDecimal credit = lr.creditAmount() != null ? lr.creditAmount() : BigDecimal.ZERO;

            AccJournalLine line = debit.compareTo(BigDecimal.ZERO) > 0
                    ? AccJournalLine.debit(tenantId.getValue(), entry.getId(),
                    lr.accountId(), debit, lr.description(), i)
                    : AccJournalLine.credit(tenantId.getValue(), entry.getId(),
                    lr.accountId(), credit, lr.description(), i);

            entry.addLine(line);
            i++;
        }

        if (!entry.isBalanced())
            throw new IllegalArgumentException(
                    "Journal does not balance — total debits R" + entry.getTotalDebit() +
                            " ≠ total credits R" + entry.getTotalCredit());

        journalRepo.save(entry);
        log.info("Created journal entry={} tenant={}", entryNumber, tenantId);
        return toJournalResponse(entry, tenantId);
    }

    @Transactional
    public JournalEntryResponse postJournalEntry(TenantId tenantId, UUID id) {
        AccJournalEntry entry = journalRepo.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("JournalEntry", id.toString()));
        entry.post();
        journalRepo.save(entry);
        log.info("Posted journal entry={}", entry.getEntryNumber());
        return toJournalResponse(entry, tenantId);
    }

    // ── Bank Accounts ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BankAccountResponse> getBankAccounts(TenantId tenantId) {
        return bankAccountRepo.findAllActive(tenantId)
                .stream().map(this::toBankAccountResponse).toList();
    }

    @Transactional
    public BankAccountResponse createBankAccount(TenantId tenantId,
                                                 CreateBankAccountRequest req) {
        AccBankAccount bank = AccBankAccount.create(tenantId, req.bankName(),
                req.accountName(), req.accountNumber(), req.branchCode(), req.accountType());
        bankAccountRepo.save(bank);
        log.info("Created bank account={} tenant={}", bank.getId(), tenantId);
        return toBankAccountResponse(bank);
    }

    @Transactional
    public BankAccountResponse addTransaction(TenantId tenantId, UUID bankAccountId,
                                              AddBankTransactionRequest req) {
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
        bankAccountRepo.save(bank);
        return toBankAccountResponse(bank);
    }

    // ── VAT Periods ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AccVatPeriod> getVatPeriods(TenantId tenantId) {
        return vatPeriodRepo.findAll(tenantId);
    }

    @Transactional
    public AccVatPeriod createVatPeriod(TenantId tenantId,
                                        LocalDate periodStart, LocalDate periodEnd) {
        AccVatPeriod period = AccVatPeriod.create(tenantId, periodStart, periodEnd);
        vatPeriodRepo.save(period);
        return period;
    }

    // ── Financial Reports ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public FinancialReportResponse getProfitAndLoss(TenantId tenantId,
                                                    LocalDate from, LocalDate to) {
        coaSeeder.seedForTenant(tenantId);
        List<AccJournalEntry> entries = journalRepo.findPostedInRange(tenantId, from, to);
        Map<UUID, BigDecimal> balances = computeBalances(entries);
        Map<UUID, AccAccount> accounts = accountRepo.findAllActive(tenantId)
                .stream().collect(Collectors.toMap(AccAccount::getId, a -> a));

        List<FinancialReportResponse.ReportLine> incomeLines = buildLines(
                accounts, balances, "INCOME", true);
        List<FinancialReportResponse.ReportLine> expenseLines = buildLines(
                accounts, balances, "EXPENSE", false);

        BigDecimal totalIncome  = incomeLines.stream()
                .map(FinancialReportResponse.ReportLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpense = expenseLines.stream()
                .map(FinancialReportResponse.ReportLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netProfit = totalIncome.subtract(totalExpense);

        return new FinancialReportResponse(
                "PROFIT_AND_LOSS", from, to,
                List.of(
                        new FinancialReportResponse.ReportSection("Income",   incomeLines,  totalIncome),
                        new FinancialReportResponse.ReportSection("Expenses", expenseLines, totalExpense)
                ),
                netProfit
        );
    }

    @Transactional(readOnly = true)
    public FinancialReportResponse getTrialBalance(TenantId tenantId,
                                                   LocalDate from, LocalDate to) {
        coaSeeder.seedForTenant(tenantId);
        List<AccJournalEntry> entries = journalRepo.findPostedInRange(tenantId, from, to);
        Map<UUID, BigDecimal> balances = computeBalances(entries);
        Map<UUID, AccAccount> accounts = accountRepo.findAllActive(tenantId)
                .stream().collect(Collectors.toMap(AccAccount::getId, a -> a));

        List<FinancialReportResponse.ReportLine> allLines = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> e : balances.entrySet()) {
            AccAccount acc = accounts.get(e.getKey());
            if (acc != null && e.getValue().compareTo(BigDecimal.ZERO) != 0)
                allLines.add(new FinancialReportResponse.ReportLine(
                        acc.getAccountCode(), acc.getAccountName(), e.getValue()));
        }
        allLines.sort(Comparator.comparing(FinancialReportResponse.ReportLine::accountCode));

        BigDecimal totalDebits  = allLines.stream()
                .filter(l -> l.amount().compareTo(BigDecimal.ZERO) > 0)
                .map(FinancialReportResponse.ReportLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new FinancialReportResponse("TRIAL_BALANCE", from, to,
                List.of(new FinancialReportResponse.ReportSection("All Accounts", allLines, totalDebits)),
                totalDebits);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<UUID, BigDecimal> computeBalances(List<AccJournalEntry> entries) {
        Map<UUID, BigDecimal> balances = new HashMap<>();
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

    // ── Mappers ───────────────────────────────────────────────────────────────

    private AccountResponse toAccountResponse(AccAccount a) {
        return new AccountResponse(a.getId(), a.getAccountCode(), a.getAccountName(),
                a.getAccountType(), a.getAccountSubtype(), a.isSystem(),
                a.getOpeningBalance(), a.getDescription());
    }

    private JournalEntryResponse toJournalResponse(AccJournalEntry e, TenantId tenantId) {
        Map<UUID, AccAccount> accounts = accountRepo.findAllActive(tenantId)
                .stream().collect(Collectors.toMap(AccAccount::getId, a -> a));

        List<JournalEntryResponse.JournalLineResponse> lines = e.getLines().stream()
                .map(l -> {
                    AccAccount acc = accounts.get(l.getAccountId());
                    return new JournalEntryResponse.JournalLineResponse(
                            l.getId(), l.getAccountId(),
                            acc != null ? acc.getAccountCode() : null,
                            acc != null ? acc.getAccountName() : null,
                            l.getDescription(), l.getDebitAmount(), l.getCreditAmount());
                }).toList();

        return new JournalEntryResponse(e.getId(), e.getEntryNumber(), e.getEntryDate(),
                e.getDescription(), e.getReference(), e.getEntryType(), e.getStatus(),
                e.getTotalDebit(), e.getTotalCredit(), e.isBalanced(), lines, e.getCreatedAt());
    }

    private BankAccountResponse toBankAccountResponse(AccBankAccount b) {
        return new BankAccountResponse(b.getId(), b.getBankName(), b.getAccountName(),
                b.getAccountNumber(), b.getBranchCode(), b.getAccountType(),
                b.getCurrency(), b.getCurrentBalance(), b.isActive());
    }

    @Transactional(readOnly = true)
    public FinancialReportResponse getBalanceSheet(TenantId tenantId, LocalDate from, LocalDate to) {
        coaSeeder.seedForTenant(tenantId);
        List<AccJournalEntry> entries = journalRepo.findPostedInRange(tenantId, from, to);
        Map<UUID, BigDecimal> balances = computeBalances(entries);
        Map<UUID, AccAccount> accounts = accountRepo.findAllActive(tenantId)
                .stream().collect(Collectors.toMap(AccAccount::getId, a -> a));

        List<FinancialReportResponse.ReportLine> assetLines     = buildLines(accounts, balances, "ASSET",     false);
        List<FinancialReportResponse.ReportLine> liabilityLines = buildLines(accounts, balances, "LIABILITY", true);
        List<FinancialReportResponse.ReportLine> equityLines    = buildLines(accounts, balances, "EQUITY",    true);

        BigDecimal totalAssets      = assetLines.stream().map(FinancialReportResponse.ReportLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalLiabilities = liabilityLines.stream().map(FinancialReportResponse.ReportLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEquity      = equityLines.stream().map(FinancialReportResponse.ReportLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal liabPlusEquity   = totalLiabilities.add(totalEquity);

        return new FinancialReportResponse(
                "BALANCE_SHEET", from, to,
                List.of(
                        new FinancialReportResponse.ReportSection("Assets",              assetLines,     totalAssets),
                        new FinancialReportResponse.ReportSection("Liabilities",         liabilityLines, totalLiabilities),
                        new FinancialReportResponse.ReportSection("Equity",              equityLines,    totalEquity)
                ),
                liabPlusEquity   // Assets should equal Liabilities + Equity
        );
    }
}