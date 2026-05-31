package za.co.handyflow.platform.security.dto;
import jakarta.validation.constraints.NotBlank;
public record CreateGuardRequest(
        @NotBlank String firstName, @NotBlank String lastName,
        String psiraNumber, String idNumber,
        String phone, String grade, String notes
) {}