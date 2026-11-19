package za.co.handyflow.platform.contracting.dto;

import java.time.Instant;
import java.util.UUID;

/** What was recorded when a signing attempt was withdrawn, and the new draft made from it (if any). */
public record WithdrawalResponse(
        UUID withdrawnBy, Instant withdrawnAt, String reason,
        int linksRevoked, int otpChallengesCleared, int partiesSigned, int partiesTotal,
        UUID revisionContractId) {}
