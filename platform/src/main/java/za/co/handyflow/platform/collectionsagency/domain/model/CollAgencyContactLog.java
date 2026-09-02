package za.co.handyflow.platform.collectionsagency.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A recorded contact attempt against a debtor account — the mandatory
 * compliance trail for a THIRD-PARTY collector, which is a materially
 * stricter obligation than debtcollection.CollectionContactLog's
 * original-creditor equivalent. Per the National Credit Act, a
 * third-party collector must, on every single contact with a debtor:
 * identify itself as a third-party collector (not the original
 * creditor), name the original creditor, and state the debtor's
 * statutory rights.
 * <p>
 * THIS IS ENFORCED, NOT JUST TRACKED: record() rejects the entry outright
 * unless all three disclosure flags are true. This is a deliberate,
 * compliance-critical design choice — a logging field that can be left
 * false would let staff silently create a non-compliant contact record,
 * which defeats the point of having a compliance trail at all. If the
 * real requirement is softer (e.g. disclosure only required on the FIRST
 * contact, or exemptions apply in specific circumstances), that's a
 * legal question this session should not guess at — flagged explicitly
 * in the status doc rather than assumed.
 * <p>
 * Otherwise append-only, same immutability rationale as
 * debtcollection.CollectionContactLog: a compliance record of what
 * happened, not an editable field set.
 */
@Entity
@Table(name = "collagency_contact_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollAgencyContactLog {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "debtor_account_id", nullable = false)
    private UUID debtorAccountId;

    @Column(name = "contact_date", nullable = false)
    private LocalDate contactDate;

    @Column(name = "contact_method", nullable = false)
    private String contactMethod; // PHONE_CALL | EMAIL | SMS | WHATSAPP | LETTER | IN_PERSON | OTHER

    @Column(name = "outcome", nullable = false)
    private String outcome; // NO_ANSWER | LEFT_MESSAGE | PROMISE_TO_PAY | DISPUTED | REFUSED_TO_PAY | ALREADY_PAID | WRONG_CONTACT_DETAILS | OTHER

    @Column(name = "disclosed_third_party_collector", nullable = false)
    private boolean disclosedThirdPartyCollector;

    @Column(name = "disclosed_original_creditor", nullable = false)
    private boolean disclosedOriginalCreditor;

    @Column(name = "disclosed_debtor_rights", nullable = false)
    private boolean disclosedDebtorRights;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "promised_payment_date")
    private LocalDate promisedPaymentDate;

    @Column(name = "promised_payment_amount", precision = 15, scale = 2)
    private BigDecimal promisedPaymentAmount;

    @Column(name = "recorded_by_user_id", nullable = false)
    private UUID recordedByUserId;

    @Column(name = "recorded_by_user_name")
    private String recordedByUserName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static CollAgencyContactLog record(UUID tenantId, UUID debtorAccountId, LocalDate contactDate,
                                               String contactMethod, String outcome,
                                               boolean disclosedThirdPartyCollector,
                                               boolean disclosedOriginalCreditor, boolean disclosedDebtorRights,
                                               String notes, LocalDate promisedPaymentDate,
                                               BigDecimal promisedPaymentAmount, UUID recordedByUserId,
                                               String recordedByUserName) {
        if (debtorAccountId == null) {
            throw new IllegalArgumentException("debtorAccountId is required");
        }
        if (contactMethod == null || contactMethod.isBlank() || outcome == null || outcome.isBlank()) {
            throw new IllegalArgumentException("contactMethod and outcome are required");
        }
        if (!disclosedThirdPartyCollector || !disclosedOriginalCreditor || !disclosedDebtorRights) {
            throw new IllegalArgumentException(
                    "All three NCA disclosures (third-party-collector status, original creditor, debtor rights) "
                            + "must be confirmed for every contact — this contact was not recorded");
        }
        if ("PROMISE_TO_PAY".equals(outcome) && (promisedPaymentDate == null || promisedPaymentAmount == null)) {
            throw new IllegalArgumentException("promisedPaymentDate and promisedPaymentAmount are required when outcome is PROMISE_TO_PAY");
        }
        CollAgencyContactLog log = new CollAgencyContactLog();
        log.tenantId = tenantId;
        log.debtorAccountId = debtorAccountId;
        log.contactDate = contactDate != null ? contactDate : LocalDate.now();
        log.contactMethod = contactMethod;
        log.outcome = outcome;
        log.disclosedThirdPartyCollector = disclosedThirdPartyCollector;
        log.disclosedOriginalCreditor = disclosedOriginalCreditor;
        log.disclosedDebtorRights = disclosedDebtorRights;
        log.notes = notes;
        log.promisedPaymentDate = promisedPaymentDate;
        log.promisedPaymentAmount = promisedPaymentAmount;
        log.recordedByUserId = recordedByUserId;
        log.recordedByUserName = recordedByUserName;
        log.createdAt = Instant.now();
        return log;
    }
}
