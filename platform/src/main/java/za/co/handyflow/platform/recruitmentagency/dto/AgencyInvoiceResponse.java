package za.co.handyflow.platform.recruitmentagency.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * FIX: confirmed via live compile error that this record's real,
 * current definition already has a 14th field (a trailing UUID) that
 * the earlier 13-field version this search kept surfacing didn't have —
 * that search result was stale, not the live file. placementId is the
 * most likely candidate: RecAgencyInvoice itself already stores it
 * (confirmed in its own create() method) but this DTO never exposed it,
 * and RecruitmentAgencyPortalDataService (a portal-facing service, where
 * "which placement is this invoice for" is exactly the kind of detail
 * a client would want to see) hits the identical compile error.
 */
public record AgencyInvoiceResponse(
        UUID id, String invoiceNumber, String description, LocalDate invoiceDate, LocalDate dueDate,
        BigDecimal subtotal, BigDecimal vatAmount, BigDecimal total, BigDecimal amountPaid,
        BigDecimal balance, String status, Instant sentAt, Instant paidAt, UUID placementId
) {}