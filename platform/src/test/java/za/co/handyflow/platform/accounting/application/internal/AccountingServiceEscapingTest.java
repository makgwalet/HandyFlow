package za.co.handyflow.platform.accounting.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.accounting.domain.model.AccBankAccount;
import za.co.handyflow.platform.accounting.domain.repository.AccAccountRepository;
import za.co.handyflow.platform.accounting.domain.repository.AccBankAccountRepository;
import za.co.handyflow.platform.accounting.domain.repository.AccBankTransactionRepository;
import za.co.handyflow.platform.accounting.domain.repository.AccJournalEntryRepository;
import za.co.handyflow.platform.accounting.domain.repository.AccVatPeriodRepository;
import za.co.handyflow.platform.accounting.dto.AgingReportResponse;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for AccountingService's 4 email templates, found while
 * checking this file as a candidate for the EmailTemplates shared-template
 * migration: NOT migrated (each of the 4 uses a distinct severity colour
 * — purple/red/red/dark-red — that doesn't exist in wrap()'s single-colour
 * design; forcing them into one shared header would lose real
 * at-a-glance-in-an-inbox severity signalling, not just deduplicate CSS),
 * but `company`, `customerName`, `bankName`, and `accountName` were all
 * interpolated with no escaping at all — the same bug category as the 34
 * already found and fixed in EmailTemplates this session.
 */
@ExtendWith(MockitoExtension.class)
class AccountingServiceEscapingTest {

    @Mock private AccAccountRepository accountRepo;
    @Mock private AccJournalEntryRepository journalRepo;
    @Mock private AccBankAccountRepository bankAccountRepo;
    @Mock private AccBankTransactionRepository bankTxRepo;
    @Mock private AccVatPeriodRepository vatPeriodRepo;
    @Mock private ChartOfAccountsSeeder coaSeeder;
    @Mock private JournalNumberGenerator numberGen;
    @Mock private InvoicingFacade invoicingFacade;
    @Mock private CrmFacade crmFacade;
    @Mock private EmailService emailService;

    private static final String PAYLOAD = "<script>alert(1)</script>";
    private static final String ESCAPED = "&lt;script&gt;alert(1)&lt;/script&gt;";

    private AccountingService service() {
        return new AccountingService(accountRepo, journalRepo, bankAccountRepo, bankTxRepo,
                vatPeriodRepo, coaSeeder, numberGen, invoicingFacade, crmFacade, emailService);
    }

    @Test
    @DisplayName("vatReminderEmail escapes company")
    void vatReminderEmail_escapesCompany() {
        String html = service().vatReminderEmail(PAYLOAD, LocalDate.of(2026, 2, 28), new BigDecimal("1500.00"));
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("overdueArEmail escapes company and customerName")
    void overdueArEmail_escapesCompanyAndCustomerName() {
        var line = new AgingReportResponse.AgingLine(
                UUID.randomUUID(), "INV-00001", PAYLOAD, LocalDate.of(2026, 1, 1), 45,
                new BigDecimal("1000.00"), "31-60");
        var aging = new AgingReportResponse(LocalDate.now(), List.of(line),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000.00"));

        String html = service().overdueArEmail(PAYLOAD, aging, 1, new BigDecimal("1000.00"));

        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("lowBalanceEmail escapes company, bankName, and accountName")
    void lowBalanceEmail_escapesNames() {
        TenantId tenantId = TenantId.generate();
        AccBankAccount account = AccBankAccount.create(tenantId, PAYLOAD, PAYLOAD, "123456", "250655", "CURRENT");
        account.setLowBalanceThreshold(new BigDecimal("5000.00"));

        String html = service().lowBalanceEmail(PAYLOAD, List.of(account));

        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("vatOverdueEmail escapes company")
    void vatOverdueEmail_escapesCompany() {
        String html = service().vatOverdueEmail(PAYLOAD, LocalDate.of(2026, 1, 31), 10);
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }
}
