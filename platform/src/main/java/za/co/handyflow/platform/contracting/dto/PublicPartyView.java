package za.co.handyflow.platform.contracting.dto;

import java.util.UUID;

// This party's own details — full info
public record PublicPartyView(
        UUID partyId,
        String fullName,
        String partyRole,
        String partyType,
        String companyName,
        String email,
        // Phone masked: +27 82 ***4567 — confirm their number without exposing it fully
        String phoneMasked,
        int signingOrder,
        String signingStatus
) {}
