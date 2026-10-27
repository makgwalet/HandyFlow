package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** The supplier's invoice for a purchase order. {@code subtotal} excludes VAT: that is what is matched. */
public record VendorInvoiceRequest(
        @NotNull UUID purchaseOrderId, @NotBlank @Size(max = 80) String invoiceNumber, @NotNull LocalDate invoiceDate,
        @NotNull @DecimalMin("0.01") BigDecimal subtotal, @DecimalMin("0") BigDecimal vatAmount
) {}
