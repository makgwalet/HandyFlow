package za.co.handyflow.platform.catalogue.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BundleLineRequest(UUID itemId, BigDecimal quantity) {}
