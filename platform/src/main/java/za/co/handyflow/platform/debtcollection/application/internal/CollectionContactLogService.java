package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.debtcollection.domain.model.CollectionContactLog;
import za.co.handyflow.platform.debtcollection.domain.model.ContactMethod;
import za.co.handyflow.platform.debtcollection.domain.model.ContactOutcome;
import za.co.handyflow.platform.debtcollection.domain.model.DebtCollectionCase;
import za.co.handyflow.platform.debtcollection.domain.repository.CollectionContactLogRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Records contact attempts against a case. Two side effects on every
 * record() call, both deliberate:
 * <ol>
 *   <li>DebtCollectionCase.lastContactDate is kept in sync (via the
 *   case service's package-visible recordContact()), so the case list view
 *   never needs to join/aggregate the log table just to show "last
 *   contacted."</li>
 *   <li>If the case has a linked CRM customer, the contact is also logged
 *   on CrmFacade.logCommunication() — this module's own log stays the
 *   structured, debt-specific record (promised amounts/dates, compliance
 *   detail); CRM's activity timeline is the general-purpose one every
 *   other module already feeds, and a debt-collection call is exactly the
 *   kind of customer-facing event a user reviewing that customer's 360
 *   view would expect to see. direction is hard-defaulted to OUTBOUND —
 *   this module doesn't currently distinguish "debtor called us" from "we
 *   called the debtor," which is a simplification worth flagging rather
 *   than guessing at a direction field that wasn't asked for.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class CollectionContactLogService {

    private final CollectionContactLogRepository repository;
    private final DebtCollectionCaseService caseService;
    private final CrmFacade crmFacade;

    @Transactional
    public CollectionContactLog record(TenantId tenantId, UUID caseId, LocalDate contactDate,
                                        ContactMethod contactMethod, ContactOutcome outcome, String notes,
                                        LocalDate promisedPaymentDate, BigDecimal promisedPaymentAmount,
                                        UUID recordedByUserId, String recordedByUserName) {
        DebtCollectionCase c = caseService.get(tenantId, caseId); // 404s if the case doesn't exist or isn't this tenant's
        CollectionContactLog log = CollectionContactLog.record(tenantId, caseId, contactDate, contactMethod,
                outcome, notes, promisedPaymentDate, promisedPaymentAmount, recordedByUserId, recordedByUserName);
        log = repository.save(log);

        caseService.recordContact(tenantId, caseId, log.getContactDate());

        if (c.getCustomerId() != null) {
            crmFacade.logCommunication(tenantId, c.getCustomerId(), toCrmType(contactMethod), "OUTBOUND",
                    "Debt collection contact (" + c.getCaseNumber() + "): " + outcome
                            + (notes != null && !notes.isBlank() ? " — " + notes : ""),
                    Instant.now(), recordedByUserId);
        }
        return log;
    }

    @Transactional(readOnly = true)
    public List<CollectionContactLog> listForCase(TenantId tenantId, UUID caseId) {
        return repository.findByCaseId(tenantId, caseId);
    }

    /** Maps this module's ContactMethod onto CrmFacade.logCommunication()'s fixed vocabulary (CALL/EMAIL/MEETING/WHATSAPP/SMS/OTHER). */
    private String toCrmType(ContactMethod method) {
        return switch (method) {
            case PHONE_CALL -> "CALL";
            case EMAIL -> "EMAIL";
            case SMS -> "SMS";
            case WHATSAPP -> "WHATSAPP";
            case IN_PERSON -> "MEETING";
            case LETTER, OTHER -> "OTHER";
        };
    }
}
