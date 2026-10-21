package za.co.handyflow.platform.catalogue.dto;

import java.util.List;

/** An option of a parent item (Size) and every value offered for it. */
public record VariantOptionDto(String name, List<String> values) {}
