package za.co.handyflow.platform.pos.dto;
import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
public record ProcessSaleRequest(
        UUID             customerId,
        String           customerName,       // walk-in customer name
        @NotEmpty List<SaleLineItem> items,
        String           paymentMethod,      // CASH|CARD|EFT|ACCOUNT|SPLIT
        BigDecimal       amountTendered,     // for cash sales
        String           paymentRef,         // EFT ref / card auth
        String           notes
) {
    public record SaleLineItem(
            UUID       catalogueItemId,
            String     itemName,             // fallback if no catalogueItemId
            BigDecimal qty,
            BigDecimal unitPrice,            // override catalogue price if set
            BigDecimal discountPct
    ) {}
}
