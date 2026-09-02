package za.co.handyflow.platform.collectionsagency.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyContactLog;
import za.co.handyflow.platform.collectionsagency.domain.repository.CollAgencyContactLogRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Records NCA-compliant contact attempts. Unlike debtcollection's
 * CollectionContactLogService, this does not also mirror the contact
 * into a CRM timeline — this module deliberately has no CrmFacade
 * dependency (the debtor is not necessarily, and usually isn't, a CRM
 * customer of this tenant; they're a third party's debtor). The
 * compliance trail here is the whole record, not a supplementary one.
 */
@Service
@RequiredArgsConstructor
public class CollAgencyContactLogService {

    private final CollAgencyContactLogRepository repository;
    private final CollAgencyDebtorAccountService debtorAccountService;

    @Transactional
    public CollAgencyContactLog record(TenantId tenantId, UUID debtorAccountId, LocalDate contactDate,
                                        String contactMethod, String outcome, boolean disclosedThirdPartyCollector,
                                        boolean disclosedOriginalCreditor, boolean disclosedDebtorRights,
                                        String notes, LocalDate promisedPaymentDate,
                                        BigDecimal promisedPaymentAmount, UUID recordedByUserId,
                                        String recordedByUserName) {
        debtorAccountService.findActive(tenantId, debtorAccountId); // 404s if the account doesn't exist or isn't this tenant's
        CollAgencyContactLog log = CollAgencyContactLog.record(tenantId.getValue(), debtorAccountId, contactDate,
                contactMethod, outcome, disclosedThirdPartyCollector, disclosedOriginalCreditor,
                disclosedDebtorRights, notes, promisedPaymentDate, promisedPaymentAmount, recordedByUserId,
                recordedByUserName);
        return repository.save(log);
    }

    @Transactional(readOnly = true)
    public List<CollAgencyContactLog> listForDebtorAccount(TenantId tenantId, UUID debtorAccountId) {
        return repository.findByDebtorAccount(tenantId.getValue(), debtorAccountId);
    }
}
