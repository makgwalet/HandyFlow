package za.co.handyflow.platform.earthmoving.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record WorkOrderLineResponse(UUID id, String kind, String description, BigDecimal quantity, BigDecimal unitCost,
                                    BigDecimal total, String supplier) {}
