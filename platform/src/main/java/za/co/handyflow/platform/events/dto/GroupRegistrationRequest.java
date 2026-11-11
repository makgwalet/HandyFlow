package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** All-or-nothing: either every member is registered, or none are. */
public record GroupRegistrationRequest(
        UUID tierId,
        UUID customerId,
        @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal amountPaidEach,
        @NotEmpty @Size(max = 50) @Valid List<GroupMemberRequest> members
) {}