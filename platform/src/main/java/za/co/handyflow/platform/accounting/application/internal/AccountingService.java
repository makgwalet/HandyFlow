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
import za.co.handyflow.platform.invoicing.domain.model.Invoice;
import za.co.handyflow.platform.invoicing.domain.repository.InvoiceRepository;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
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
    private final InvoiceRepository            invoiceRepo;
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

        for (var lr : req.lines()) {
            BigDecimal d = lr.debitAmount()  != null ? lr.debitAmount()  : BigDecimal.ZERO;
            BigDecimal c = lr.creditAmount() != null ? lr.creditAmount() : BigDecimal.ZERO;
            if (d.compareTo(BigDecimal.ZERO) < 0 || c.compareTo(BigDecimal.ZERO) < 0)
                throw new IllegalArgumentException("Journal line amounts must be positive");
        }

        String entryNumber = numberGen.next(tenantId);
        AccJournalEntry entry = AccJournalEntry.create(
                tenantId, entryNumber, req.entryDate(),
                req.description(), req.reference(), req.entryType());
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
        log.info("Created journal entry={} tenant={}", entryNumber, tenantId);

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

    @Transactional(readOnly = true)
    public Vat201Response getVat201(TenantId tenantId, LocalDate from, LocalDate to) {
        List<Invoice> invoices = invoiceRepo.findAllForVat(tenantId.getValue().toString(), from, to);
        BigDecimal outputVat  = invoices.stream().map(Invoice::getVatTotal)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSales = invoices.stream().map(Invoice::getSubtotal)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

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

        return new Vat201Response(from, to, invoices.size(), totalSales, outputVat,
                inputVat, outputVat.subtract(inputVat));
    }

    // ── AR Aging ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AgingReportResponse getArAging(TenantId tenantId) {
        List<Invoice> outstanding = invoiceRepo.findOutstandingForTenant(tenantId.getValue().toString());
        LocalDate today = LocalDate.now();

        BigDecimal current = BigDecimal.ZERO, days30 = BigDecimal.ZERO,
                days60 = BigDecimal.ZERO, days90 = BigDecimal.ZERO, over90 = BigDecimal.ZERO;
        List<AgingReportResponse.AgingLine> lines = new ArrayList<>();

        for (Invoice inv : outstanding) {
            BigDecimal balance = inv.getTotal().subtract(
                    inv.getAmountPaid() != null ? inv.getAmountPaid() : BigDecimal.ZERO);
            if (balance.compareTo(BigDecimal.ZERO) <= 0) continue;

            LocalDate due = inv.getDueDate() != null ? inv.getDueDate() : today;
            long daysOverdue = ChronoUnit.DAYS.between(due, today);

            String bucket;
            if      (daysOverdue <= 0)  { bucket = "CURRENT"; current = current.add(balance); }
            else if (daysOverdue <= 30) { bucket = "1-30";    days30  = days30.add(balance);  }
            else if (daysOverdue <= 60) { bucket = "31-60";   days60  = days60.add(balance);  }
            else if (daysOverdue <= 90) { bucket = "61-90";   days90  = days90.add(balance);  }
            else                        { bucket = "90+";     over90  = over90.add(balance);  }

            String customerName = inv.getCustomerId() != null
                    ? crmFacade.findCustomerById(tenantId, inv.getCustomerId())
                    .map(c -> c.name())
                    .orElse("Customer " + inv.getCustomerId().toString().substring(0, 8))
                    : inv.getWalkinClientName();
            lines.add(new AgingReportResponse.AgingLine(
                    inv.getId(), inv.getInvoiceNumber(),
                    customerName,
                    inv.getDueDate(), daysOverdue > 0 ? (int) daysOverdue : 0,
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
                e.getTotalDebit(), e.getTotalCredit(), e.isBalanced(), lines, e.getCreatedAt());
    }

    private BankAccountResponse toBankAccountResponse(AccBankAccount b) {
        return new BankAccountResponse(b.getId(), b.getBankName(), b.getAccountName(),
                b.getAccountNumber(), b.getBranchCode(), b.getAccountType(),
                b.getCurrency(), b.getCurrentBalance(), b.isActive());
    }

    private BankTransactionResponse toBankTransactionResponse(AccBankTransaction t) {
        return new BankTransactionResponse(t.getId(), t.getBankAccountId(),
                t.getTransactionDate(), t.getDescription(), t.getReference(),
                t.getAmount(), t.getTransactionType(), t.getBalanceAfter(),
                t.isReconciled(), t.getCreatedAt());
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
     */
    public void sendVatReminder(TenantId tenantId, String recipientEmail,
                                String companyName, LocalDate periodEnd, BigDecimal estimatedVat) {
        String subject = "VAT Period Closing Soon — " + periodEnd.format(
                DateTimeFormatter.ofPattern("d MMM yyyy"));
        String body = vatReminderEmail(companyName, periodEnd, estimatedVat);
        emailService.send(recipientEmail, subject, body);
        log.info("Sent VAT reminder to={} periodEnd={}", recipientEmail, periodEnd);
    }

    /**
     * Send overdue AR alert.
     * Called by AccountingNotificationScheduler daily for outstanding invoices > 30 days.
     */
    public void sendOverdueArAlert(TenantId tenantId, String recipientEmail,
                                   String companyName, AgingReportResponse aging) {
        long overdueCount = aging.lines().stream()
                .filter(l -> !"CURRENT".equals(l.bucket())).count();
        if (overdueCount == 0) return;  // nothing overdue, skip

        BigDecimal overdueTotal = aging.days1to30()
                .add(aging.days31to60()).add(aging.days61to90()).add(aging.over90());

        String subject = String.format("Overdue Invoices Alert — %d invoice%s, %s outstanding",
                overdueCount, overdueCount == 1 ? "" : "s", fmtAmount(overdueTotal));
        String body = overdueArEmail(companyName, aging, overdueCount, overdueTotal);
        emailService.send(recipientEmail, subject, body);
        log.info("Sent overdue AR alert to={} overdueCount={} total={}", recipientEmail, overdueCount, overdueTotal);
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

}
