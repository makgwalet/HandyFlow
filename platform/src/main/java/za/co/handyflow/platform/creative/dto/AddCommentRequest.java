package za.co.handyflow.platform.creative.dto;

import jakarta.validation.constraints.NotBlank;

public record AddCommentRequest(
        @NotBlank String comment,
        String authorName,   // only used by PUBLIC (client) endpoint
        Double timecodeSeconds,  // for video proofs — the playback position this comment refers to, if any
        Double anchorX,  // for image proofs — pin position as a 0-1 fraction of width, if any
        Double anchorY   // pin position as a 0-1 fraction of height — always set together with anchorX
) {}
