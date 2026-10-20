package za.co.handyflow.platform.fleet.dto;

/** Returned once, when a key is created. {@code apiKey} is not stored and cannot be shown again. */
public record CreatedIngestKeyResponse(
        IngestKeyResponse key,
        String apiKey
) {}