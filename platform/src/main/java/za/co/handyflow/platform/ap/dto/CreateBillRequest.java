package za.co.handyflow.platform.ap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateBillRequest(
        UUID       supplierId,
        @NotBlank  String     supplierName,
        @NotBlank  String     billNumber,
        @NotNull   LocalDate  billDate,
        @NotNull   LocalDate  dueDate,
                   String     category,
        @NotBlank  String     description,
        @NotNull @Positive BigDecimal amount,
                   BigDecimal vatAmount,
                   String     attachmentBase64,
                   String     attachmentName,
                   String     notes
) {}
