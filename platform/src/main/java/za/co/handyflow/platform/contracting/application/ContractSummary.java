package za.co.handyflow.platform.contracting.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The DTO ContractingFacade returns — deliberately primitives/value types
 * only, never the internal Contract entity (see CrmFacade's own Javadoc
 * on why domain objects never cross a module boundary in this codebase).
 * <p>
 * status/contractType are passed through as the same raw Strings
 * Contract.java itself uses (that entity stores both as plain String,
 * not an enum — confirmed directly against its real source) rather than
 * this facade inventing a stricter type Contract doesn't actually have.
 */
public record ContractSummary(
        UUID id,
        String contractNumber,
        String title,
        String contractType,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal valueAmount,
        boolean autoRenew
) {}
