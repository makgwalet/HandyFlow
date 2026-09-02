package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotNull;
import za.co.handyflow.platform.debtcollection.domain.model.ContactMethod;
import za.co.handyflow.platform.debtcollection.domain.model.ContactOutcome;

import java.math.BigDecimal;
import java.time.LocalDate;

/** promisedPaymentDate/promisedPaymentAmount are required when outcome is PROMISE_TO_PAY — enforced by CollectionContactLog.record() itself, not this DTO. */
public record RecordContactRequest(
        LocalDate contactDate,
        @NotNull ContactMethod contactMethod,
        @NotNull ContactOutcome outcome,
        String notes,
        LocalDate promisedPaymentDate,
        BigDecimal promisedPaymentAmount
) {}
