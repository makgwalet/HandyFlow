package za.co.handyflow.platform.crm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * FIX: backlog 4.2 — "no deal value / expected close date" gap.
 * <p>
 * Both fields nullable and unvalidated on purpose — see
 * Customer.updateDeal()'s Javadoc for why clearing either value is a valid
 * operation, not an error. No @Positive/@FutureOrPresent constraints:
 * expectedCloseDate legitimately needs to be settable in the past for a
 * lead that slipped its forecast and is being re-dated after the fact, and
 * dealValue has no natural floor a sales rep's estimate couldn't
 * legitimately fall below zero of during a discount negotiation edge case
 * — better to let the number through than block a real update on an
 * over-eager constraint nobody asked for.
 */
public record UpdateDealRequest(BigDecimal dealValue, LocalDate expectedCloseDate) {}