package za.co.handyflow.platform.catalogue.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record VariantsResponse(UUID parentId, String parentName, String parentSku, BigDecimal parentPrice,
                               List<VariantOptionDto> options, List<VariantRowResponse> variants) {}
