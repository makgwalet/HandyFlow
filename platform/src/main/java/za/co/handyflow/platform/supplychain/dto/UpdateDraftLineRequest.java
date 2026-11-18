package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;

/** Edit a line on a DRAFT PO. Null fields are left unchanged. */
public record UpdateDraftLineRequest(BigDecimal qtyOrdered, BigDecimal unitCost, BigDecimal vatRate) {}
