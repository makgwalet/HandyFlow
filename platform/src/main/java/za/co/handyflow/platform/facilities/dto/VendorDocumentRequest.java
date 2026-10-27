package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VendorDocumentRequest(
        @NotBlank String docType, @Size(max = 120) String reference, @Size(max = 200) String issuedBy,
        LocalDate issueDate, LocalDate expiryDate, BigDecimal coverAmount, @Size(max = 500) String documentRef
) {}
