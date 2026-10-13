package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import za.co.handyflow.platform.debtcollection.domain.model.ContactMethod;
import za.co.handyflow.platform.debtcollection.domain.model.PtpFrequency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * frequency defaults to ONE_OFF; additionalOccurrences (recurring only) = how many further promises follow
 * the first. accepted defaults to true (debtor confirmed on the call); send false to record a proposal that
 * isn't tracked until /accept. graceDays defaults to 2. workItemId (optional) completes that work item with
 * disposition PTP_CAPTURED in the same transaction.
 */
public record ProposePtpRequest(
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate dueDate,
        PtpFrequency frequency,
        @Min(0) @Max(60) Integer additionalOccurrences,
        @NotNull ContactMethod source,
        String terms,
        @Min(0) @Max(30) Integer graceDays,
        Boolean accepted,
        UUID workItemId
) {}
