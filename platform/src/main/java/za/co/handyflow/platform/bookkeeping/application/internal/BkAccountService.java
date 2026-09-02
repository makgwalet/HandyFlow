package za.co.handyflow.platform.bookkeeping.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.bookkeeping.domain.model.BkAccount;
import za.co.handyflow.platform.bookkeeping.domain.repository.BkAccountRepository;
import za.co.handyflow.platform.bookkeeping.domain.repository.BkClientRepository;
import za.co.handyflow.platform.bookkeeping.dto.BkAccountResponse;
import za.co.handyflow.platform.bookkeeping.dto.CreateBkAccountRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Per-client chart of accounts. {@code getAccounts} seeds a standard SA
 * chart on first call for that client (mirrors {@code AccountingService}'s
 * own seed-on-first-use pattern for a tenant, per {@code BkAccount}'s own
 * Javadoc).
 * <p>
 * KNOWN SIMPLIFICATION, FLAGGED NOT SILENTLY GUESSED: no real {@code
 * coaSeeder}-equivalent source was available to copy for this module —
 * the list below is a reasonable, minimal standard South African small-
 * business chart of accounts assembled for this build, not a verbatim
 * port of {@code accounting}'s own seeded chart. Every seeded row is
 * marked {@code system=true}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BkAccountService {

    private final BkAccountRepository accountRepository;
    private final BkClientRepository clientRepository;

    /** {code, name, type, subtype} — matches the minimum list named in the build brief. */
    private static final String[][] STANDARD_CHART = {
            {"1000", "Bank",                    "ASSET",     "CURRENT_ASSET"},
            {"1100", "Accounts Receivable",     "ASSET",     "CURRENT_ASSET"},
            {"1200", "Inventory",               "ASSET",     "CURRENT_ASSET"},
            {"1500", "Fixed Assets",            "ASSET",     "NON_CURRENT_ASSET"},
            {"2000", "Accounts Payable",        "LIABILITY", "CURRENT_LIABILITY"},
            {"2100", "VAT Control",             "LIABILITY", "CURRENT_LIABILITY"},
            {"2200", "Loans Payable",           "LIABILITY", "NON_CURRENT_LIABILITY"},
            {"3000", "Owner's Equity",          "EQUITY",    "EQUITY"},
            {"3100", "Retained Earnings",       "EQUITY",    "EQUITY"},
            {"4000", "Sales Revenue",           "REVENUE",   "OPERATING_REVENUE"},
            {"4100", "Other Income",            "REVENUE",   "OTHER_INCOME"},
            {"5000", "Cost of Sales",           "EXPENSE",   "COST_OF_SALES"},
            {"6000", "Operating Expenses",      "EXPENSE",   "OPERATING_EXPENSE"},
            {"6100", "Salaries and Wages",      "EXPENSE",   "OPERATING_EXPENSE"},
            {"6200", "Rent Expense",            "EXPENSE",   "OPERATING_EXPENSE"},
            {"6300", "Bank Charges",            "EXPENSE",   "OPERATING_EXPENSE"},
    };

    @Transactional
    public List<BkAccountResponse> getAccounts(TenantId tenantId, UUID clientId) {
        clientRepository.findActiveById(tenantId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("BkClient", clientId.toString()));

        List<BkAccount> existing = accountRepository.findAllForClient(tenantId, clientId);
        if (existing.isEmpty()) {
            existing = seedStandardChart(tenantId, clientId);
        }
        return existing.stream().map(this::toResponse).toList();
    }

    private List<BkAccount> seedStandardChart(TenantId tenantId, UUID clientId) {
        log.info("Seeding standard SA chart of accounts for client={} tenant={}", clientId, tenantId.getValue());
        List<BkAccount> seeded = new java.util.ArrayList<>();
        for (String[] row : STANDARD_CHART) {
            BkAccount account = BkAccount.create(tenantId, clientId, row[0], row[1], row[2], row[3], true, null);
            seeded.add(accountRepository.save(account));
        }
        return seeded;
    }

    @Transactional
    public BkAccountResponse createCustomAccount(TenantId tenantId, UUID clientId, CreateBkAccountRequest req) {
        clientRepository.findActiveById(tenantId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("BkClient", clientId.toString()));
        accountRepository.findByClientAndCode(tenantId, clientId, req.accountCode()).ifPresent(a -> {
            throw new IllegalArgumentException("An account with code " + req.accountCode() + " already exists for this client");
        });

        BkAccount account = BkAccount.create(tenantId, clientId, req.accountCode(), req.accountName(),
                req.accountType(), req.accountSubtype(), false, req.description());
        accountRepository.save(account);
        log.info("Custom bookkeeping account created code={} client={} tenant={}", req.accountCode(), clientId, tenantId);
        return toResponse(account);
    }

    private BkAccountResponse toResponse(BkAccount a) {
        return new BkAccountResponse(a.getId(), a.getClientId(), a.getAccountCode(), a.getAccountName(),
                a.getAccountType(), a.getAccountSubtype(), a.isSystem(), a.getOpeningBalance(), a.getDescription(), a.getCreatedAt());
    }
}
