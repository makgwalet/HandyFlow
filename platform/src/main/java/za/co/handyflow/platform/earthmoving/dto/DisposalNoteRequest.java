package za.co.handyflow.platform.earthmoving.dto;

/** A note on approving, rejecting or cancelling a disposal. Rejecting and cancelling need one. */
public record DisposalNoteRequest(String note) {}
