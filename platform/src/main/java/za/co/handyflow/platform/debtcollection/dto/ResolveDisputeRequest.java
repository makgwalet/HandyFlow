package za.co.handyflow.platform.debtcollection.dto;

import java.math.BigDecimal;

public record ResolveDisputeRequest(String resolution, String note, BigDecimal amountConceded, String creditNoteRef) {}
