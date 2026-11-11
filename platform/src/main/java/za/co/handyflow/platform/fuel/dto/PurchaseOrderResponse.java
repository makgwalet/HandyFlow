package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PurchaseOrderResponse(UUID id, String poNumber, UUID supplierId, String supplierName, String fuelType,
                                    BigDecimal litresOrdered, BigDecimal litresReceived, BigDecimal litresOutstanding,
                                    BigDecimal pricePerLitre, BigDecimal orderValue, String status, LocalDate expectedDate,
                                    boolean overdue, String cancelReason, String notes) {}
