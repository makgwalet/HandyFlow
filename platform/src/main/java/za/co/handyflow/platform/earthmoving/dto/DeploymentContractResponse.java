package za.co.handyflow.platform.earthmoving.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The contract side of one hire deployment. {@code contract} is null when none is linked. {@code expectedValue} is the daily rate
 * times the planned days (null when a rate or date is missing); {@code expected} says whether that reaches the company's threshold;
 * {@code warning} is what to tell the person (null when nothing). Addenda are the extension agreements made under the contract.
 */
public record DeploymentContractResponse(UUID deploymentId, Contract contract, List<Contract> addenda, BigDecimal expectedValue,
                                         BigDecimal threshold, boolean expected, String warning) {
    public record Contract(UUID id, String number, String title, String status, LocalDate startDate, LocalDate endDate) {}
}
