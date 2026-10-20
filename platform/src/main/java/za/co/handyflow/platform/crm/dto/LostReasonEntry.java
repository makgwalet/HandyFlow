package za.co.handyflow.platform.crm.dto;

import za.co.handyflow.platform.crm.domain.model.LostReason;

import java.math.BigDecimal;

/** One row of the lost-deals report. reason is null for leads lost before reasons were recorded. */
public record LostReasonEntry(LostReason reason, long count, BigDecimal dealValue) {}
