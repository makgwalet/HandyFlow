package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.fleet.domain.model.DisposalMethod;
import za.co.handyflow.platform.fleet.domain.model.VehicleLifecycleStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Sell, transfer or dispose of a vehicle. {@code target} is SOLD, TRANSFERRED or DISPOSED.
 * SOLD needs a buyer and a sale price; SOLD / TRANSFERRED need a counterparty; a financed
 * vehicle needs {@code financeCleared = true}.
 */
public record RecordDisposalRequest(
        @NotNull VehicleLifecycleStatus target,
        DisposalMethod method,
        @Size(max = 255) String counterpartyName,       // buyer / transferee / scrapper
        @Size(max = 255) String counterpartyContact,
        @NotNull LocalDate disposalDate,
        @PositiveOrZero BigDecimal salePrice,
        @PositiveOrZero Integer odometerAtDisposal,     // cannot be lower than the current reading; raises it if higher
        boolean financeCleared,
        @PositiveOrZero BigDecimal financeSettlementAmount,
        boolean registrationTransferred,
        LocalDate registrationTransferDate,             // defaults to today when registrationTransferred
        @Size(max = 100) String accountingReference,    // journal / asset-register reference once posted
        String notes
) {}