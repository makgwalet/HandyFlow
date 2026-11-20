package za.co.handyflow.platform.legalpractice.dto;

import java.time.LocalDate;
import java.util.UUID;

/** status: NOT_STARTED, DRAFT, SUBMITTED, VERIFIED, DUE_SOON or EXPIRED. profileId is the profile to open (the one in progress, else the latest). */
public record KycOverviewRow(UUID clientId, String clientName, String clientType, String status, UUID profileId, String riskRating, LocalDate validUntil, boolean reviewOpen) {
}
