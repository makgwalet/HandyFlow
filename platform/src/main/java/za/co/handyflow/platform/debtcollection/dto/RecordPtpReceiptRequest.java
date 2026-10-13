package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecordPtpReceiptRequest(@NotNull @Positive BigDecimal amount, LocalDate receivedDate,
                                      String reference, String notes) {}
