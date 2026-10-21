package za.co.handyflow.platform.crm.dto;

import za.co.handyflow.platform.crm.domain.model.LeadSource;

/** defaultSource is the lead source used when the sender does not give a valid one; empty means Website. */
public record CaptureKeyRequest(String name, LeadSource defaultSource) {}
