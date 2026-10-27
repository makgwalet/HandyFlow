package za.co.handyflow.platform.facilities.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record StockLevelResponse(UUID storeId, String storeCode, String storeName, BigDecimal onHand) {}
