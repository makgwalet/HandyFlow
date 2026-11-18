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
        Instant otpSentAt,
        Instant linkIssuedAt,       // when the current or latest signing link was issued
        String linkDeliveryStatus,  // QUEUED, FAILED or NO_CHANNEL (email is asynchronous, so QUEUED is not "delivered")
        UUID linkIssuedBy           // staff user who issued or reissued it; null if the system did
) {}