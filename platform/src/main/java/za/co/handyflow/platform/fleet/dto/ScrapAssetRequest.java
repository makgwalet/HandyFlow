package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Scrap a tyre or battery that is not on a vehicle. (A fitted one is scrapped by removing it with outcome SCRAPPED.) */
public record ScrapAssetRequest(
        @NotBlank @Size(max = 500) String reason,
        Instant scrappedAt
) {}