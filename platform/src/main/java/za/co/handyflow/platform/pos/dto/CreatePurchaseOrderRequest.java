package za.co.handyflow.platform.pos.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
public record CreatePurchaseOrderRequest(
        UUID              supplierId,
        @NotBlank String  supplierName,
        LocalDate         expectedDate,
        String            notes,
        @NotEmpty List<PurchaseOrderLine> items
) {
    public record PurchaseOrderLine(
            UUID       catalogueItemId,
            String     itemName,
            BigDecimal qtyOrdered,
            BigDecimal unitCost,
            BigDecimal vatRate
    ) {}
}