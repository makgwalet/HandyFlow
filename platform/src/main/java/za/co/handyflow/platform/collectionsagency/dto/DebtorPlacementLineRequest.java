package za.co.handyflow.platform.collectionsagency.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Wire shape for one line of a bulk debtor-account import. Mirrors
 * CollAgencyPlacementService.DebtorPlacementLine exactly — kept as a
 * separate DTO (rather than reusing that nested record directly as a
 * request body) so the domain-facing service signature doesn't leak
 * jakarta.validation annotations into application/internal.
 * <p>
 * originalCreditorName may be left blank — the service defaults it to
 * the client's own tradingName (the common case: the client IS the
 * original creditor). See CollAgencyPlacementService's own Javadoc.
 */
public record DebtorPlacementLineRequest(
        String accountReference, @NotBlank String debtorName, String debtorIdNumber, String debtorEmail,
        String debtorPhone, String debtorAddress, String originalCreditorName, LocalDate originalDebtDate,
        @NotNull @Positive BigDecimal originalDebtAmount
) {}
