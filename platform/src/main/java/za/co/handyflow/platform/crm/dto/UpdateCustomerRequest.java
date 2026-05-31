package za.co.handyflow.platform.crm.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record UpdateCustomerRequest(
        @NotBlank(message = "Customer name is required")
        @Size(max = 255)
        String name,

        @Email(message = "Invalid email format")
        String email,

        String phone,
        Map<String, String> address,
        String taxNumber,
        String notes
) {}