package za.co.handyflow.platform.crm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import za.co.handyflow.platform.crm.domain.model.CustomerStatus;
import za.co.handyflow.platform.crm.domain.model.CustomerType;

/**
 * UpdateCustomerRequest — validated DTO for customer updates.
 *
 * KEY DIFFERENCE from the original:
 * The original was identical to CreateCustomerRequest.
 * We've added status and customerType fields so staff can:
 * - Convert a LEAD to a CUSTOMER via update
 * - Mark a customer as INACTIVE or BLOCKED
 *
 * WHY not PATCH (partial update)?
 * PUT with all fields is simpler for the frontend — they just send
 * the whole form back.  PATCH requires the frontend to track which
 * fields changed, and the backend needs merge logic.
 * For a form-driven UI (which this is), PUT is the right choice.
 *
 * If we later need PATCH (e.g. for a mobile app that updates one
 * field at a time), add a separate PatchCustomerRequest with all
 * Optional<> fields.
 */
public record UpdateCustomerRequest(

        @NotBlank(message = "Customer name is required")
        @Size(max = 255, message = "Name cannot exceed 255 characters")
        String name,

        @Email(message = "Invalid email format")
        @Size(max = 255, message = "Email cannot exceed 255 characters")
        String email,

        @Pattern(
                regexp = "^(\\+|0)[\\d\\s\\-]{7,19}$",
                message = "Phone must start with + or 0, contain 7–20 digits/spaces/hyphens"
        )
        String phone,

        @Valid
        AddressRequest address,

        @Pattern(
                regexp = "^\\d{10}$",
                message = "SA VAT number must be exactly 10 digits"
        )
        String taxNumber,

        @Size(max = 5000, message = "Notes cannot exceed 5000 characters")
        String notes,

        CustomerType customerType,
        CustomerStatus status

) {}
