package za.co.handyflow.platform.accounting.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accounting.domain.model.AccAccount;
import za.co.handyflow.platform.accounting.domain.repository.AccAccountRepository;
import za.co.handyflow.platform.shared.TenantId;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChartOfAccountsSeeder {

    private final AccAccountRepository accountRepo;

    // WHY? South African standard chart of accounts numbering:
    // 1xxx = Assets, 2xxx = Liabilities, 3xxx = Equity, 4xxx = Income, 5xxx = Expenses
    @Transactional
    public void seedForTenant(TenantId tenantId) {
        if (accountRepo.countByTenant(tenantId) > 0) {
            log.debug("Chart of accounts already seeded for tenant={}", tenantId);
            return;
        }

        log.info("Seeding standard SA chart of accounts for tenant={}", tenantId);

        // ── ASSETS (1xxx) ──────────────────────────────────────────────────────
        seed(tenantId, "1000", "Current Assets",           "ASSET", "CURRENT_ASSETS");
        seed(tenantId, "1010", "Cash and Cash Equivalents","ASSET", "CASH");
        seed(tenantId, "1020", "Bank — Cheque Account",    "ASSET", "BANK");
        seed(tenantId, "1030", "Bank — Savings Account",   "ASSET", "BANK");
        seed(tenantId, "1100", "Accounts Receivable",      "ASSET", "RECEIVABLE");
        seed(tenantId, "1110", "Trade Debtors",            "ASSET", "RECEIVABLE");
        seed(tenantId, "1200", "Inventory",                "ASSET", "INVENTORY");
        seed(tenantId, "1300", "VAT Input (Claimable)",    "ASSET", "VAT");
        seed(tenantId, "1400", "Prepaid Expenses",         "ASSET", "PREPAID");
        seed(tenantId, "1500", "Non-Current Assets",       "ASSET", "NON_CURRENT");
        seed(tenantId, "1510", "Property, Plant & Equipment","ASSET","FIXED_ASSET");
        seed(tenantId, "1520", "Vehicles",                 "ASSET", "FIXED_ASSET");
        seed(tenantId, "1530", "Equipment",                "ASSET", "FIXED_ASSET");
        seed(tenantId, "1590", "Accumulated Depreciation", "ASSET", "DEPRECIATION");

        // ── LIABILITIES (2xxx) ────────────────────────────────────────────────
        seed(tenantId, "2000", "Current Liabilities",      "LIABILITY", "CURRENT");
        seed(tenantId, "2010", "Accounts Payable",         "LIABILITY", "PAYABLE");
        seed(tenantId, "2020", "Trade Creditors",          "LIABILITY", "PAYABLE");
        seed(tenantId, "2100", "VAT Output (Payable)",     "LIABILITY", "VAT");
        seed(tenantId, "2110", "VAT Control Account",      "LIABILITY", "VAT");
        seed(tenantId, "2200", "PAYE Payable",             "LIABILITY", "TAX");
        seed(tenantId, "2210", "UIF Payable",              "LIABILITY", "TAX");
        seed(tenantId, "2300", "Short-term Loans",         "LIABILITY", "LOAN");
        seed(tenantId, "2400", "Accrued Expenses",         "LIABILITY", "ACCRUAL");
        seed(tenantId, "2500", "Non-Current Liabilities",  "LIABILITY", "NON_CURRENT");
        seed(tenantId, "2510", "Long-term Loans",          "LIABILITY", "LOAN");

        // ── EQUITY (3xxx) ─────────────────────────────────────────────────────
        seed(tenantId, "3000", "Owner's Equity",           "EQUITY", "EQUITY");
        seed(tenantId, "3010", "Share Capital",            "EQUITY", "CAPITAL");
        seed(tenantId, "3020", "Retained Earnings",        "EQUITY", "RETAINED");
        seed(tenantId, "3030", "Drawings",                 "EQUITY", "DRAWINGS");
        seed(tenantId, "3040", "Current Year Earnings",    "EQUITY", "CURRENT_EARNINGS");

        // ── INCOME (4xxx) ─────────────────────────────────────────────────────
        seed(tenantId, "4000", "Revenue",                  "INCOME", "REVENUE");
        seed(tenantId, "4010", "Sales — Products",         "INCOME", "SALES");
        seed(tenantId, "4020", "Sales — Services",         "INCOME", "SALES");
        seed(tenantId, "4030", "Hire Income",              "INCOME", "HIRE");
        seed(tenantId, "4100", "Other Income",             "INCOME", "OTHER");
        seed(tenantId, "4110", "Interest Received",        "INCOME", "INTEREST");
        seed(tenantId, "4120", "Discount Received",        "INCOME", "DISCOUNT");

        // ── EXPENSES (5xxx) ───────────────────────────────────────────────────
        seed(tenantId, "5000", "Cost of Sales",            "EXPENSE", "COS");
        seed(tenantId, "5010", "Cost of Goods Sold",       "EXPENSE", "COS");
        seed(tenantId, "5100", "Operating Expenses",       "EXPENSE", "OPERATING");
        seed(tenantId, "5110", "Salaries and Wages",       "EXPENSE", "PAYROLL");
        seed(tenantId, "5120", "Rent Expense",             "EXPENSE", "RENT");
        seed(tenantId, "5130", "Utilities",                "EXPENSE", "UTILITIES");
        seed(tenantId, "5140", "Fuel and Travel",          "EXPENSE", "TRAVEL");
        seed(tenantId, "5150", "Vehicle Maintenance",      "EXPENSE", "VEHICLE");
        seed(tenantId, "5160", "Telephone and Internet",   "EXPENSE", "COMMS");
        seed(tenantId, "5170", "Office Supplies",          "EXPENSE", "OFFICE");
        seed(tenantId, "5180", "Marketing and Advertising","EXPENSE", "MARKETING");
        seed(tenantId, "5190", "Professional Fees",        "EXPENSE", "PROFESSIONAL");
        seed(tenantId, "5200", "Insurance",                "EXPENSE", "INSURANCE");
        seed(tenantId, "5210", "Bank Charges",             "EXPENSE", "BANK_CHARGES");
        seed(tenantId, "5220", "Depreciation",             "EXPENSE", "DEPRECIATION");
        seed(tenantId, "5230", "Bad Debts",                "EXPENSE", "BAD_DEBTS");
        seed(tenantId, "5300", "Finance Costs",            "EXPENSE", "FINANCE");
        seed(tenantId, "5310", "Interest Paid",            "EXPENSE", "INTEREST");
        seed(tenantId, "5240", "Staff Expense Reimbursements", "EXPENSE", "STAFF_EXPENSES");
        seed(tenantId, "5241", "Travel and Subsistence",       "EXPENSE", "STAFF_EXPENSES");
        seed(tenantId, "5242", "Meals and Entertainment",      "EXPENSE", "STAFF_EXPENSES");
        seed(tenantId, "5243", "Accommodation",                "EXPENSE", "STAFF_EXPENSES");

        log.info("Seeded {} accounts for tenant={}", 47, tenantId);
    }

    private void seed(TenantId tenantId, String code, String name, String type, String subtype) {
        AccAccount account = AccAccount.create(tenantId, code, name, type, subtype, true);
        accountRepo.save(account);
    }
}