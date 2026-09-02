package za.co.handyflow.platform.legalcompliance.dto;

/** Shared shape for complete/reject/withdraw — all three just carry resolution notes; the state-machine rule differs, not the payload. */
public record ResolveDsarRequestRequest(String resolutionNotes) {}
