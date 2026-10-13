package za.co.handyflow.platform.debtcollection.dto;

import za.co.handyflow.platform.debtcollection.domain.model.PtpReceipt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PtpReceiptResponse(UUID id, BigDecimal amount, LocalDate receivedDate, String reference, String notes) {
    public static PtpReceiptResponse of(PtpReceipt r) {
        return new PtpReceiptResponse(r.getId(), r.getAmount(), r.getReceivedDate(), r.getReference(), r.getNotes());
    }
}
