package za.co.handyflow.platform.contracting.dto;

// Request to formally decline
public record DeclineSignRequest(
        String reason               // optional but encouraged
) {}
