package za.co.handyflow.platform.catalogue.dto;

import java.util.List;

public record CreateVariantsRequest(List<VariantOptionDto> options, List<VariantRowRequest> variants) {}
