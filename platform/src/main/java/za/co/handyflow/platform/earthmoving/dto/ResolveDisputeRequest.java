package za.co.handyflow.platform.earthmoving.dto;

/** Close a dispute by confirming the reading stands. The note says why. */
public record ResolveDisputeRequest(String note) {}
