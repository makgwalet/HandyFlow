package za.co.handyflow.platform.contracting.dto;

import java.time.Instant;

// Other parties — minimal info, no contact details (POPIA compliance)
public record OtherPartyView(
        String fullName,
        String partyRole,
        String companyName,
        int signingOrder,
        String signingStatus,
        Instant signedAt         // visible — confirms to each party that others have signed
) {}
