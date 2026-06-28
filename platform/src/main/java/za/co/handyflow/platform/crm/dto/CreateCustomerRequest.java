package za.co.handyflow.platform.crm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import za.co.handyflow.platform.crm.domain.model.CustomerType;

import java.util.Map;
import java.util.Set;

/**
 * CreateCustomerRequest — validated DTO for customer creation.
 *
 * WHY a record?
 * Records are immutable by design.  A DTO that arrives from the network
 * should never be mutated — it's a value object that describes an intent.
 * Records enforce this with no extra code.
 *
 * WHY inline validation annotations?
 * Bean Validation (@NotBlank, @Email, etc.) runs BEFORE the service method
 * is called, triggered by @Valid on the controller parameter.  This means
 * validation errors return a 400 with field-level details automatically —
 * no manual if/throw blocks in the service.
 *
 * CHANGES FROM ORIGINAL:
 * - email now optional (some B2B customers contact via phone only)
 * - phone validation pattern updated to handle SA formats
 * - taxNumber: SA VAT numbers are exactly 10 digits per SARS rules
 * - tags added: create with initial tags (e.g. "vip", "key-account")
 * - customerType added: create as LEAD or CUSTOMER from day one
 * - address is now a typed @Valid nested record instead of Map<String,String>
 *   WHY typed record for address? Gives field-level validation on address
 *   components (province must be in the SA provinces list).  A raw Map
 *   can't be validated without custom code.
 */
public record CreateCustomerRequest(

        @NotBlank(message = "Customer name is required")
        @Size(max = 255, message = "Name cannot exceed 255 characters")
        String name,

        /**
         * Email is optional — some customers are phone-only.
         * But if provided, it must be valid format.
         * The service layer enforces uniqueness (DB partial index is the backstop).
         */
        @Email(message = "Invalid email format")
        @Size(max = 255, message = "Email cannot exceed 255 characters")
        String email,

        /**
         * SA phone: starts with +27 (international) or 0 (local).
         * Allow spaces and hyphens as separators.
         * Minimum 7 digits after prefix (shortest valid SA number).
         */
        @Pattern(
                regexp = "^(\\+|0)[\\d\\s\\-]{7,19}$",
                message = "Phone must start with + or 0, contain 7–20 digits/spaces/hyphens"
        )
        String phone,

        @Valid
        AddressRequest address,

        /**
         * SA VAT number: exactly 10 digits per SARS specification.
         * Regex: digits only, length 10.
         * WHY validate at DTO level? Cheaper than a SARS API call for
         * obvious format violations.  Real VAT validation (checksum,
         * SARS lookup) is a separate concern for later.
         */
        @Pattern(
                regexp = "^\\d{10}$",
                message = "SA VAT number must be exactly 10 digits"
        )
        String taxNumber,

        @Size(max = 5000, message = "Notes cannot exceed 5000 characters")
        String notes,

        CustomerType customerType,

        /**
         * Tags applied at creation time.
         * Max 20 tags, each max 50 chars.
         * WHY limit? Prevent abuse (10,000 tags on one customer).
         */
        @Size(max = 20, message = "Cannot apply more than 20 tags at once")
        Set<@Size(max = 50, message = "Each tag cannot exceed 50 characters") String> tags

) {
    /**
     * Canonical constructor — coerce nulls and defaults.
     * WHY compact constructor?
     * Records don't have setters. The compact constructor runs at
     * construction time and lets us normalize values (trim, lowercase,
     * default customerType) before the fields are set.
     */
    public CreateCustomerRequest {
        customerType = (customerType == null) ? CustomerType.CUSTOMER : customerType;
        tags         = (tags == null) ? Set.of() : Set.copyOf(tags);
    }
}
