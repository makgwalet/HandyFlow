package za.co.handyflow.platform.marketing.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ImportContactsRequest(
        @NotEmpty List<ContactEntry> contacts,
                  String optInSource   // IMPORT, FORM, MANUAL
) {
    public record ContactEntry(
            @Email @NotBlank String email,
                               String name,
                               boolean emailOptedIn
    ) {}
}
