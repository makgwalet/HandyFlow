package za.co.handyflow.platform.earthmoving.dto;

/** Say a reading looks wrong. The reason is required. */
public record DisputeReadingRequest(String reason) {}
