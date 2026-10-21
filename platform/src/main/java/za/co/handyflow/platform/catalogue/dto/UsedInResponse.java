package za.co.handyflow.platform.catalogue.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** A package that contains the item, and how many of it. */
public record UsedInResponse(UUID bundleId, String name, String sku, String status, BigDecimal quantity) {}
