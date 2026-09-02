package za.co.handyflow.platform.debtcollection.dto;

import za.co.handyflow.platform.debtcollection.domain.model.CollectionContactLog;
import za.co.handyflow.platform.debtcollection.domain.model.ContactMethod;
import za.co.handyflow.platform.debtcollection.domain.model.ContactOutcome;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CollectionContactLogResponse(
        UUID id,
        UUID caseId,
        LocalDate contactDate,
        ContactMethod contactMethod,
        ContactOutcome outcome,
        String notes,
        LocalDate promisedPaymentDate,
        BigDecimal promisedPaymentAmount,
        UUID recordedByUserId,
        String recordedByUserName,
        Instant createdAt
) {
    public static CollectionContactLogResponse of(CollectionContactLog l) {
        return new CollectionContactLogResponse(
                l.getId(), l.getCaseId(), l.getContactDate(), l.getContactMethod(), l.getOutcome(), l.getNotes(),
                l.getPromisedPaymentDate(), l.getPromisedPaymentAmount(), l.getRecordedByUserId(),
                l.getRecordedByUserName(), l.getCreatedAt());
    }
}
