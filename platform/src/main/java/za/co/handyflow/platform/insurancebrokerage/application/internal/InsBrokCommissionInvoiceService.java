package za.co.handyflow.platform.insurancebrokerage.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accounting.application.AccountingFacade;
import za.co.handyflow.platform.accounting.dto.AccountResponse;
import za.co.handyflow.platform.accounting.dto.CreateJournalEntryRequest;
import za.co.handyflow.platform.accounting.dto.JournalEntryResponse;
import za.co.handyflow.platform.insurancebrokerage.domain.model.InsBrokClient;
import za.co.handyflow.platform.insurancebrokerage.domain.model.InsBrokCommissionInvoice;
import za.co.handyflow.platform.insurancebrokerage.domain.model.InsBrokPolicy;
import za.co.handyflow.platform.insurancebrokerage.domain.repository.InsBrokCommissionInvoiceRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Commission issuance and the ONLY GL-posting path in this module —
 * mirrors {@code CollAgencyTrustTransactionService.processRemittance()}'s
 * own commission leg exactly (same AR/Revenue/VAT account codes, same
 * three-method {@code AccountingFacade} call sequence, same "log and
 * skip, never block or guess" behaviour when the Chart of Accounts is
 * missing an account). See {@code InsBrokCommissionInvoice}'s own
 * Javadoc for why settlement (recordPayment) does not itself post a
 * second GL journal.
 * <p>
 * {@code issueForPolicy()} is called from EXACTLY ONE place —
 * {@code InsBrokPolicyService.activate()} and
 * {@code InsBrokPolicyService.renew()} (which activates the new term
 * directly) — never independently, same "creation only ever happens as
 * part of the triggering business event" discipline
 * {@code CollAgencyCommissionInvoiceService} already documents for its
 * own {@code processRemittance()}-only creation path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsBrokCommissionInvoiceService {

    private static final String AR_ACCOUNT_CODE = "1100";      // Accounts Receivable
    private static final String REVENUE_ACCOUNT_CODE = "4000"; // Revenue/Sales
    private static final String VAT_ACCOUNT_CODE = "2100";     // VAT Output

    private final InsBrokCommissionInvoiceRepository repository;
    private final InsBrokClientService clientService;
    private final InsBrokNumberGenerator numberGenerator;
    private final AccountingFacade accountingFacade;

    @Transactional(readOnly = true)
    public Page<InsBrokCommissionInvoice> listForClient(TenantId tenantId, UUID clientId, Pageable pageable) {
        return repository.findByClient(tenantId.getValue(), clientId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<InsBrokCommissionInvoice> listAll(TenantId tenantId, Pageable pageable) {
        return repository.findAllForTenant(tenantId.getValue(), pageable);
    }

    @Transactional(readOnly = true)
    public InsBrokCommissionInvoice get(TenantId tenantId, UUID id) {
        return findActive(tenantId, id);
    }

    /**
     * Creates and immediately issues one commission invoice for the given
     * ACTIVE policy term, then posts its revenue journal. Called from
     * {@code InsBrokPolicyService} only, at the moment a policy reaches
     * ACTIVE (new business or renewal) — never called for a QUOTE or
     * BOUND policy, and never called twice for the same policy row
     * (each renewal is a NEW row with its own id, so
     * {@code findByPolicy()} staying empty for that new row's id is what
     * guarantees idempotency here rather than a separate flag).
     */
    @Transactional
    public InsBrokCommissionInvoice issueForPolicy(TenantId tenantId, InsBrokPolicy policy) {
        if (repository.findByPolicy(tenantId.getValue(), policy.getId()).isPresent()) {
            throw new IllegalStateException(
                    "A commission invoice already exists for policy " + policy.getId() + " — this should never happen "
                            + "(each policy term/row is only ever activated once)");
        }

        InsBrokClient client = clientService.findActive(tenantId, policy.getClientId());
        BigDecimal ratePct = resolveCommissionRate(policy, client);
        BigDecimal premium = policy.getPremiumAmount() == null ? BigDecimal.ZERO : policy.getPremiumAmount();
        BigDecimal commission = premium.multiply(ratePct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        String invoiceNumber = numberGenerator.nextCommissionInvoiceNumber(tenantId, client.getId());
        String description = "Commission on policy " + (policy.getPolicyNumber() != null ? policy.getPolicyNumber()
                : policy.getId()) + " (" + ratePct + "% of premium R" + premium + ")";
        LocalDate invoiceDate = LocalDate.now();
        BigDecimal vatAmount = BigDecimal.ZERO; // VAT-on-commission treatment not confirmed — same flagged gap CollAgency's own commission invoice carries

        InsBrokCommissionInvoice invoice = InsBrokCommissionInvoice.create(tenantId.getValue(), client.getId(),
                policy.getId(), invoiceNumber, description, invoiceDate, invoiceDate.plusDays(30), commission,
                vatAmount);
        invoice = repository.save(invoice);
        invoice.markSent();
        invoice = repository.save(invoice);

        postCommissionRevenueJournal(tenantId, invoice, commission, vatAmount);

        log.info("[InsuranceBrokerage] Commission invoice {} issued tenant={} client={} policy={} amount={}",
                invoiceNumber, tenantId.getValue(), client.getId(), policy.getId(), invoice.getTotal());
        return invoice;
    }

    @Transactional
    public InsBrokCommissionInvoice recordPayment(TenantId tenantId, UUID id, BigDecimal amount) {
        InsBrokCommissionInvoice invoice = findActive(tenantId, id);
        invoice.recordPayment(amount);
        return repository.save(invoice);
    }

    private BigDecimal resolveCommissionRate(InsBrokPolicy policy, InsBrokClient client) {
        if (policy.getCommissionRatePct() != null) {
            return policy.getCommissionRatePct();
        }
        if (client.getDefaultCommissionRatePct() != null) {
            return client.getDefaultCommissionRatePct();
        }
        throw new IllegalStateException(
                "No commission rate is set on this policy or on client " + client.getClientName()
                        + " — set one before activating the policy (this is revenue-critical and was deliberately "
                        + "not defaulted to a guessed value, same guard CollAgencyTrustTransactionService already enforces)");
    }

    private void postCommissionRevenueJournal(TenantId tenantId, InsBrokCommissionInvoice invoice,
                                               BigDecimal subtotal, BigDecimal vatAmount) {
        try {
            List<AccountResponse> accounts = accountingFacade.getAccounts(tenantId);
            UUID arAccountId = findAccountId(accounts, AR_ACCOUNT_CODE);
            UUID revenueAccountId = findAccountId(accounts, REVENUE_ACCOUNT_CODE);
            if (arAccountId == null || revenueAccountId == null) {
                log.warn("[InsuranceBrokerage] Chart of Accounts missing account {} or {} for tenant={} — commission invoice={} revenue not posted",
                        AR_ACCOUNT_CODE, REVENUE_ACCOUNT_CODE, tenantId.getValue(), invoice.getId());
                return;
            }
            boolean hasVat = vatAmount != null && vatAmount.compareTo(BigDecimal.ZERO) > 0;
            UUID vatAccountId = null;
            if (hasVat) {
                vatAccountId = findAccountId(accounts, VAT_ACCOUNT_CODE);
                if (vatAccountId == null) {
                    log.warn("[InsuranceBrokerage] Chart of Accounts missing VAT Output ({}) for tenant={} — commission invoice={} revenue not posted",
                            VAT_ACCOUNT_CODE, tenantId.getValue(), invoice.getId());
                    return;
                }
            }

            List<CreateJournalEntryRequest.JournalLineRequest> lines = new ArrayList<>();
            lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                    arAccountId, "Commission — " + invoice.getInvoiceNumber(), invoice.getTotal(), null));
            lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                    revenueAccountId, "Commission revenue — " + invoice.getInvoiceNumber(), null, subtotal));
            if (hasVat) {
                lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                        vatAccountId, "VAT output — " + invoice.getInvoiceNumber(), null, vatAmount));
            }

            CreateJournalEntryRequest req = new CreateJournalEntryRequest(
                    LocalDate.now(), "Commission — " + invoice.getInvoiceNumber(), invoice.getInvoiceNumber(),
                    "MANUAL", lines);

            JournalEntryResponse entry = accountingFacade.createJournalEntry(tenantId, req);
            accountingFacade.postJournalEntry(tenantId, entry.id());
        } catch (Exception e) {
            log.error("[InsuranceBrokerage] Failed to post GL journal for commission invoice={} tenant={}: {}",
                    invoice.getInvoiceNumber(), tenantId.getValue(), e.getMessage(), e);
        }
    }

    private UUID findAccountId(List<AccountResponse> accounts, String code) {
        return accounts.stream()
                .filter(a -> code.equals(a.accountCode()))
                .map(AccountResponse::id)
                .findFirst()
                .orElse(null);
    }

    private InsBrokCommissionInvoice findActive(TenantId tenantId, UUID id) {
        return repository.findByTenantAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new ResourceNotFoundException("InsBrokCommissionInvoice", id.toString()));
    }
}
