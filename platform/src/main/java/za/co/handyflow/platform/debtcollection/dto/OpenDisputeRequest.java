package za.co.handyflow.platform.debtcollection.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OpenDisputeRequest(UUID invoiceId, String reasonType, String description, BigDecimal amountDisputed, UUID ownerUserId, String ownerUserName) {}
