package za.co.handyflow.platform.contracting.dto;

import jakarta.validation.constraints.NotBlank;

public record AddPartyRequest(
        @NotBlank String partyType,
        @NotBlank String partyRole,
        @NotBlank String fullName,
        String email,
        String phone,
        String idNumber,
        String companyName,
        Integer signingOrder
) {}