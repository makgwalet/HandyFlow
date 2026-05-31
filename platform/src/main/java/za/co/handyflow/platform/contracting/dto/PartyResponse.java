package za.co.handyflow.platform.contracting.dto;

import java.time.Instant;
import java.util.UUID;

public record PartyResponse(
        UUID id,
        String partyType,
        String partyRole,
        String fullName,
        String email,
        String phone,
        String companyName,
        int signingOrder,
        String signingStatus,
        Instant signedAt,
        Instant otpSentAt
) {}