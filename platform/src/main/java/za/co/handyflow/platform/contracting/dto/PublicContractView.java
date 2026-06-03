package za.co.handyflow.platform.contracting.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// ── What the external party sees when they open their signing link ────────────
public record PublicContractView(
        UUID contractId,
        String contractNumber,
        String title,
        String contractType,
        String status,
        String bodyHtml,          // full resolved body — rendered on SigningPage
        LocalDate startDate,
        LocalDate endDate,
        java.math.BigDecimal valueAmount,
        String currency,
        String notes,
        // Only this party's own details — not other parties' contact info
        PublicPartyView myDetails,
        // Other parties shown by name/role only — no contact details (POPIA)
        List<OtherPartyView> otherParties,
        // Indicates whether this party has already signed
        boolean alreadySigned,
        Instant signedAt,
        // Token expiry so the frontend can show a countdown
        Instant tokenExpiresAt,
        // Whether the body was locked (prevents tamper after sending)
        String bodyHash,
        // Comments thread on the contract
        List<CommentView> comments
) {}

