package za.co.handyflow.platform.collectionsagency.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyClient;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyDebtorAccount;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyPlacementBatch;
import za.co.handyflow.platform.collectionsagency.domain.repository.CollAgencyDebtorAccountRepository;
import za.co.handyflow.platform.collectionsagency.domain.repository.CollAgencyPlacementBatchRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The placement/handover workflow — the core operational loop called out
 * explicitly in the domain analysis that scoped this module: a creditor
 * client places a batch of debtor accounts, the agency acknowledges
 * receipt, and each account then enters the ordinary
 * CollAgencyDebtorAccountService workflow (assign/contact/payment-plan/
 * etc.).
 * <p>
 * createBatch() is transactional across the whole batch: either every
 * line becomes a debtor account or none do — a partially-imported batch
 * would leave totalAccounts/totalPlacedValue on the batch record
 * inconsistent with what's actually in the portfolio.
 */
@Service
@RequiredArgsConstructor
public class CollAgencyPlacementService {

    private final CollAgencyPlacementBatchRepository batchRepository;
    private final CollAgencyDebtorAccountRepository debtorAccountRepository;
    private final CollAgencyClientService clientService;

    /** One line in a placement batch — one debtor account to be created. originalCreditorName may be left null to default to the client's own tradingName (the common case: the client IS the original creditor). */
    public record DebtorPlacementLine(
            String accountReference,
            String debtorName,
            String debtorIdNumber,
            String debtorEmail,
            String debtorPhone,
            String debtorAddress,
            String originalCreditorName,
            LocalDate originalDebtDate,
            BigDecimal originalDebtAmount
    ) {}

    @Transactional
    public CollAgencyPlacementBatch createBatch(TenantId tenantId, UUID clientId, String batchReference,
                                                 LocalDate placedDate, List<DebtorPlacementLine> lines,
                                                 String notes) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("A placement batch must contain at least one debtor account");
        }
        CollAgencyClient client = clientService.findActive(tenantId, clientId);

        BigDecimal totalValue = lines.stream()
                .map(DebtorPlacementLine::originalDebtAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        CollAgencyPlacementBatch batch = CollAgencyPlacementBatch.create(tenantId.getValue(), clientId,
                batchReference, placedDate, lines.size(), totalValue, notes);
        batch = batchRepository.save(batch);

        for (DebtorPlacementLine line : lines) {
            String originalCreditorName = line.originalCreditorName() != null && !line.originalCreditorName().isBlank()
                    ? line.originalCreditorName()
                    : client.getTradingName();
            CollAgencyDebtorAccount account = CollAgencyDebtorAccount.create(tenantId.getValue(), clientId,
                    batch.getId(), line.accountReference(), line.debtorName(), line.debtorIdNumber(),
                    line.debtorEmail(), line.debtorPhone(), line.debtorAddress(), originalCreditorName,
                    line.originalDebtDate(), line.originalDebtAmount(), placedDate, null);
            debtorAccountRepository.save(account);
        }
        return batch;
    }

    @Transactional
    public CollAgencyPlacementBatch acknowledge(TenantId tenantId, UUID batchId, UUID acknowledgedBy) {
        CollAgencyPlacementBatch batch = findActive(tenantId, batchId);
        batch.acknowledge(acknowledgedBy);
        return batchRepository.save(batch);
    }

    @Transactional(readOnly = true)
    public List<CollAgencyPlacementBatch> listForClient(TenantId tenantId, UUID clientId) {
        return batchRepository.findByClient(tenantId.getValue(), clientId);
    }

    @Transactional(readOnly = true)
    public CollAgencyPlacementBatch get(TenantId tenantId, UUID batchId) {
        return findActive(tenantId, batchId);
    }

    @Transactional(readOnly = true)
    public List<CollAgencyDebtorAccount> accountsInBatch(TenantId tenantId, UUID batchId) {
        return debtorAccountRepository.findByPlacementBatch(tenantId.getValue(), batchId);
    }

    private CollAgencyPlacementBatch findActive(TenantId tenantId, UUID batchId) {
        return batchRepository.findByTenantAndId(tenantId.getValue(), batchId)
                .orElseThrow(() -> new ResourceNotFoundException("CollAgencyPlacementBatch", batchId.toString()));
    }
}
