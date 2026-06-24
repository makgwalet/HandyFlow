package za.co.handyflow.platform.supplychain.dto;

import java.time.LocalDate;
import java.util.UUID;

public record CreateGoodsReceiptRequest(
        UUID purchaseOrderId,
        UUID receivedToLocation,
        String deliveryNoteRef,
        LocalDate receivedDate,
        String notes
) {}