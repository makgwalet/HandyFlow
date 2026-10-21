package za.co.handyflow.platform.catalogue.dto;

import java.math.BigDecimal;

/** A null minimum price clears it. */
public record MinPriceRequest(BigDecimal minPrice) {}
