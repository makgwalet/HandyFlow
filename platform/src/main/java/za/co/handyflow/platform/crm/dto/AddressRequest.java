package za.co.handyflow.platform.crm.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * AddressRequest — typed, validated SA address structure.
 *
 * WHY a typed record instead of Map<String, String>?
 *
 * The original code used Map<String, String> for address.
 * This means:
 * - No field-level validation (any key with any value is accepted)
 * - No IDE autocomplete for callers
 * - No documentation of what fields are expected
 * - Province could be "Gautinggg" with no error
 *
 * A typed record fixes all of this.  The @Valid on the parent DTO
 * triggers nested validation automatically.
 *
 * WHY allow nulls on all fields?
 * Address is optional as a whole and partially optional per-field.
 * A customer might provide city+province but no street yet.
 * We validate FORMAT when provided, but don't require all fields.
 */
public record AddressRequest(

        @Size(max = 255, message = "Street cannot exceed 255 characters")
        String street,

        @Size(max = 100, message = "Suburb cannot exceed 100 characters")
        String suburb,

        @Size(max = 100, message = "City cannot exceed 100 characters")
        String city,

        /**
         * Must be a valid SA province name.
         * WHY regex instead of enum?
         * Keeping it as a String (not CustomerProvince enum) means
         * we can add international addresses later without a schema change.
         * The regex validates the 9 SA provinces.
         */
        @Pattern(
                regexp = "^(Eastern Cape|Free State|Gauteng|KwaZulu-Natal|Limpopo|Mpumalanga|North West|Northern Cape|Western Cape)$",
                message = "Province must be a valid South African province"
        )
        String province,

        /**
         * SA postal codes: 4 digits (e.g. 2000, 8001).
         * WHY String not Integer? Leading zeros must be preserved for
         * postal codes in some areas.  Also keeps JSON serialization simple.
         */
        @Pattern(
                regexp = "^\\d{4}$",
                message = "SA postal code must be exactly 4 digits"
        )
        String postalCode

) {}
