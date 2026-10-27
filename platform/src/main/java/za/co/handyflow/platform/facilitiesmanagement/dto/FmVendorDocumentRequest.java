package za.co.handyflow.platform.facilitiesmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FmVendorDocumentRequest(
        @NotBlank String docType, @Size(max = 120) String reference, @Size(max = 200) String issuedBy,
        LocalDate issueDate, LocalDate expiryDate, BigDecimal coverAmount, @Size(max = 500) String documentRef
) {}
