package za.co.handyflow.platform.crm.dto;

import za.co.handyflow.platform.crm.domain.model.LeadSource;

import java.math.BigDecimal;

/**
 * One row of the lead-source report. source is null for leads with no source recorded. winRate is won / (won + lost), null until
 * at least one lead from that source has been decided.
 */
public record SourceEntry(LeadSource source, long leads, long won, long lost, long open, BigDecimal winRate, BigDecimal wonValue) {}
